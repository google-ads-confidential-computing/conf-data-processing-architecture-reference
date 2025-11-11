/*
 * Copyright 2022 Google LLC
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

#include "private_key_fetcher_provider.h"

#include <functional>
#include <memory>
#include <utility>

#include <nlohmann/json.hpp>

#include "core/interface/async_context.h"
#include "cpio/client_providers/interface/private_key_fetcher_provider_interface.h"
#include "public/core/interface/execution_result.h"

#include "error_codes.h"
#include "private_key_fetcher_provider_utils.h"

using google::scp::core::AsyncContext;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::HttpRequest;
using google::scp::core::HttpResponse;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_HTTP_CLIENT_NOT_FOUND;
using google::scp::cpio::client_providers::PrivateKeyFetchingRequest;
using google::scp::cpio::client_providers::PrivateKeyFetchingResponse;
using std::make_shared;
using std::move;
using std::shared_ptr;
using std::string;
using std::placeholders::_1;

namespace google::scp::cpio::client_providers {
ExecutionResult PrivateKeyFetcherProvider::Init() noexcept {
  if (!http_client_) {
    auto execution_result = FailureExecutionResult(
        SC_PRIVATE_KEY_FETCHER_PROVIDER_HTTP_CLIENT_NOT_FOUND);
    SCP_ERROR(kPrivateKeyFetcherProvider, kZeroUuid, execution_result,
              "Failed to get http client.");
    return execution_result;
  }
  return SuccessExecutionResult();
}

ExecutionResult PrivateKeyFetcherProvider::Run() noexcept {
  return SuccessExecutionResult();
}

ExecutionResult PrivateKeyFetcherProvider::Stop() noexcept {
  return SuccessExecutionResult();
}

void PrivateKeyFetcherProvider::FetchPrivateKey(
    AsyncContext<PrivateKeyFetchingRequest, PrivateKeyFetchingResponse>&
        private_key_fetching_context) noexcept {
  AsyncContext<PrivateKeyFetchingRequest, HttpRequest>
      sign_http_request_context(
          private_key_fetching_context.request,
          bind(&PrivateKeyFetcherProvider::SignHttpRequestCallback<
                   PrivateKeyFetchingRequest, PrivateKeyFetchingResponse>,
               this, private_key_fetching_context, _1),
          private_key_fetching_context);

  SignHttpRequest(sign_http_request_context);
}

void PrivateKeyFetcherProvider::FetchKeysetMetadata(
    AsyncContext<KeysetMetadataFetchingRequest, KeysetMetadataFetchingResponse>&
        keyset_metadata_fetching_context) noexcept {
  AsyncContext<KeysetMetadataFetchingRequest, HttpRequest>
      sign_http_request_context(
          keyset_metadata_fetching_context.request,
          bind(&PrivateKeyFetcherProvider::SignHttpRequestCallback<
                   KeysetMetadataFetchingRequest,
                   KeysetMetadataFetchingResponse>,
               this, keyset_metadata_fetching_context, _1),
          keyset_metadata_fetching_context);

  SignHttpRequest(sign_http_request_context);
}
}  // namespace google::scp::cpio::client_providers
