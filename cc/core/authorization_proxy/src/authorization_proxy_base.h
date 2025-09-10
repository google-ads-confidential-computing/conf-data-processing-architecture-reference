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

#pragma once

#include <chrono>
#include <memory>
#include <string>

#include "core/common/auto_expiry_concurrent_map/src/auto_expiry_concurrent_map.h"
#include "core/interface/authorization_proxy_interface.h"

namespace google::scp::core {

/**
 * @brief This is a base class that just implements authorization response
 * caching logic. The cache key is extracted from the AuthorizationProxyRequest.
 * The actual authorization logic is delegated to the AuthorizeInternal method.
 */
class AuthorizationProxyBase : public AuthorizationProxyInterface {
 public:
  struct CacheEntry : public LoadableObject {
    AuthorizedMetadata authorized_metadata;
  };

  AuthorizationProxyBase(
      const std::shared_ptr<AsyncExecutorInterface>& async_executor,
      std::chrono::seconds auth_cache_entry_lifetime =
          std::chrono::seconds(150));

  ExecutionResult Init() noexcept override;

  ExecutionResult Run() noexcept override;

  ExecutionResult Stop() noexcept override;

  ExecutionResult Authorize(
      AsyncContext<AuthorizationProxyRequest,
                   AuthorizationProxyResponse>&) noexcept override;

 protected:
  virtual ExecutionResult AuthorizeInternal(
      AsyncContext<AuthorizationProxyRequest,
                   AuthorizationProxyResponse>&) noexcept = 0;

 private:
  /**
   * @brief The handler for the AuthorizeInternal call.
   *
   * @param authorization_context The authorization context to perform
   * operation on.
   * @param cache_entry_key key of the entry
   * @param authorization_context_internal The internal authorization context.
   */
  void HandleAuthorizeInternalResponse(
      AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&
          authorization_context,
      std::string& cache_entry_key,
      AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&
          authorization_context_internal);

  /// The authorization context cache.
  common::AutoExpiryConcurrentMap<std::string, std::shared_ptr<CacheEntry>>
      cache_;
};
}  // namespace google::scp::core
