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

#pragma once

#include <chrono>
#include <memory>
#include <string>

#include "core/authorization_proxy/src/authorization_proxy_base.h"
#include "core/interface/authorization_proxy_interface.h"
#include "core/interface/http_client_interface.h"
#include "core/interface/http_request_response_auth_interceptor_interface.h"

namespace google::scp::core {

class AuthorizationProxy : public AuthorizationProxyBase {
 public:
  AuthorizationProxy(
      const std::string& server_endpoint,
      const std::shared_ptr<AsyncExecutorInterface>& async_executor,
      const std::shared_ptr<HttpClientInterface>& http_client,
      std::unique_ptr<HttpRequestResponseAuthInterceptorInterface> http_helper,
      std::chrono::seconds auth_cache_entry_lifetime =
          std::chrono::seconds(150));

  ExecutionResult Init() noexcept override;

 protected:
  ExecutionResult AuthorizeInternal(
      AsyncContext<AuthorizationProxyRequest,
                   AuthorizationProxyResponse>&) noexcept override;

  /**
   * @brief The handler when performing HttpClient operations.
   *
   * @param authorization_context The authorization context to perform
   * operation on.
   * @param http_context
   */
  void HandleAuthorizeResponse(
      AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&
          authorization_context,
      AsyncContext<HttpRequest, HttpResponse>& http_context);

  /// The remote authorization end point URI
  /// Ex: http://localhost:65534/endpoint
  std::shared_ptr<std::string> server_endpoint_uri_;

  /// The host portion of server_endpoint_uri_.
  std::string host_;

  /// The http client to send request to the remote authorizer.
  const std::shared_ptr<HttpClientInterface> http_client_;

  /// Request Response helper for HTTP
  std::unique_ptr<HttpRequestResponseAuthInterceptorInterface> http_helper_;
};
}  // namespace google::scp::core
