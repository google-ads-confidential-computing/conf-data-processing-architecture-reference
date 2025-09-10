/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "core/authorization_proxy/src/authorization_proxy_base.h"

#include <chrono>
#include <memory>
#include <string>
#include <utility>

#include "core/authorization_proxy/src/error_codes.h"
#include "core/common/uuid/src/uuid.h"

using google::scp::core::common::kZeroUuid;
using std::function;
using std::make_shared;
using std::shared_ptr;
using std::string;
using std::placeholders::_1;
using std::placeholders::_2;
using std::placeholders::_3;

static constexpr const char kAuthorizationProxy[] = "AuthorizationProxyBase";

namespace google::scp::core {

void OnBeforeGarbageCollection(std::string&,
                               shared_ptr<AuthorizationProxyBase::CacheEntry>&,
                               function<void(bool)> should_delete_entry) {
  should_delete_entry(true);
}

ExecutionResult AuthorizationProxyBase::Init() noexcept {
  return cache_.Init();
}

ExecutionResult AuthorizationProxyBase::Run() noexcept {
  return cache_.Run();
}

ExecutionResult AuthorizationProxyBase::Stop() noexcept {
  return cache_.Stop();
}

AuthorizationProxyBase::AuthorizationProxyBase(
    const shared_ptr<AsyncExecutorInterface>& async_executor,
    std::chrono::seconds auth_cache_entry_lifetime)
    : cache_(auth_cache_entry_lifetime.count(),
             false /* extend_entry_lifetime_on_access */,
             false /* block_entry_while_eviction */,
             bind(&OnBeforeGarbageCollection, _1, _2, _3), async_executor) {}

ExecutionResult AuthorizationProxyBase::Authorize(
    AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&
        authorization_context) noexcept {
  if (!authorization_context.request) {
    return FailureExecutionResult(errors::SC_AUTHORIZATION_PROXY_BAD_REQUEST);
  }

  const auto& request = *authorization_context.request;
  if (!request.authorization_metadata.IsValid()) {
    return FailureExecutionResult(errors::SC_AUTHORIZATION_PROXY_BAD_REQUEST);
  }

  shared_ptr<CacheEntry> cache_entry_result;
  // TODO Current map doesn't allow custom types i.e. AuthorizationMetadata
  // to be part of key because there is a need to specialise std::hash template
  // for AuthorizationMetadata, keeping string for now as the key.
  auto key_value_pair = make_pair(request.authorization_metadata.GetKey(),
                                  make_shared<CacheEntry>());
  auto execution_result = cache_.Insert(key_value_pair, cache_entry_result);
  if (!execution_result.Successful()) {
    if (execution_result.status_code ==
        errors::SC_AUTO_EXPIRY_CONCURRENT_MAP_ENTRY_BEING_DELETED) {
      return RetryExecutionResult(execution_result.status_code);
    }

    if (execution_result.status_code !=
        errors::SC_CONCURRENT_MAP_ENTRY_ALREADY_EXISTS) {
      return execution_result;
    }

    if (cache_entry_result->is_loaded) {
      authorization_context.response =
          make_shared<AuthorizationProxyResponse>();
      authorization_context.response->authorized_metadata =
          cache_entry_result->authorized_metadata;
      authorization_context.result = SuccessExecutionResult();
      authorization_context.Finish();
      return SuccessExecutionResult();
    }

    SCP_DEBUG(kAuthorizationProxy, kZeroUuid,
              string("Failed authorizing the token with error ") +
                  errors::GetErrorMessage(execution_result.status_code));
    return RetryExecutionResult(
        errors::SC_AUTHORIZATION_PROXY_AUTH_REQUEST_INPROGRESS);
  }

  // Cache entry was not present, inserted.
  execution_result = cache_.DisableEviction(key_value_pair.first);
  if (!execution_result.Successful()) {
    cache_.Erase(key_value_pair.first);
    return RetryExecutionResult(
        errors::SC_AUTHORIZATION_PROXY_AUTH_REQUEST_INPROGRESS);
  }

  AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
      authorization_context_internal(
          authorization_context.request,
          bind(&AuthorizationProxyBase::HandleAuthorizeInternalResponse, this,
               authorization_context, key_value_pair.first, _1),
          authorization_context);

  execution_result = AuthorizeInternal(authorization_context_internal);
  if (!execution_result.Successful()) {
    SCP_ERROR(kAuthorizationProxy, kZeroUuid, execution_result,
              "Failed in internal authorization request");
    cache_.Erase(key_value_pair.first);
    return execution_result;
  }

  return SuccessExecutionResult();
}

void AuthorizationProxyBase::HandleAuthorizeInternalResponse(
    AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&
        authorization_context,
    std::string& cache_entry_key,
    AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&
        authorization_context_internal) {
  if (!authorization_context_internal.result.Successful()) {
    cache_.Erase(cache_entry_key);
    // Bubbling internal error up the stack
    authorization_context.result = authorization_context_internal.result;
    authorization_context.Finish();
    return;
  }

  authorization_context.response = make_shared<AuthorizationProxyResponse>();
  authorization_context.response->authorized_metadata =
      authorization_context_internal.response->authorized_metadata;

  // Update cache entry
  shared_ptr<CacheEntry> cache_entry;
  auto execution_result = cache_.Find(cache_entry_key, cache_entry);
  if (!execution_result.Successful()) {
    SCP_DEBUG_CONTEXT(kAuthorizationProxy, authorization_context,
                      "Cannot find the cached entry.");
    authorization_context.result = SuccessExecutionResult();
    authorization_context.Finish();
  }

  cache_entry->authorized_metadata =
      authorization_context.response->authorized_metadata;
  cache_entry->is_loaded = true;

  execution_result = cache_.EnableEviction(cache_entry_key);
  if (!execution_result.Successful()) {
    cache_.Erase(cache_entry_key);
  }

  authorization_context.result = SuccessExecutionResult();
  authorization_context.Finish();
}
}  // namespace google::scp::core
