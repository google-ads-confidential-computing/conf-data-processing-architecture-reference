/*
 * Copyright 2022-2025 Google LLC
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
#include "core/authorization_proxy/src/authorization_proxy.h"

#include <chrono>
#include <memory>
#include <string>
#include <utility>

#include "core/authorization_proxy/src/error_codes.h"
#include "core/common/uuid/src/uuid.h"
#include "core/http2_client/src/http2_client.h"

using boost::system::error_code;
using google::scp::core::common::kZeroUuid;
using nghttp2::asio_http2::host_service_from_uri;
using std::function;
using std::make_shared;
using std::make_unique;
using std::shared_ptr;
using std::string;
using std::placeholders::_1;

static constexpr const char kAuthorizationProxy[] = "AuthorizationProxy";

namespace google::scp::core {

ExecutionResult AuthorizationProxy::Init() noexcept {
  error_code http2_error_code;
  string scheme;
  string service;
  if (host_service_from_uri(http2_error_code, scheme, host_, service,
                            *server_endpoint_uri_)) {
    auto execution_result =
        FailureExecutionResult(errors::SC_AUTHORIZATION_PROXY_INVALID_CONFIG);
    SCP_ERROR(kAuthorizationProxy, kZeroUuid, execution_result,
              "Failed to parse URI with boost error_code: %s",
              http2_error_code.message().c_str());
    return execution_result;
  }

  return AuthorizationProxyBase::Init();
}

AuthorizationProxy::AuthorizationProxy(
    const string& server_endpoint_url,
    const shared_ptr<AsyncExecutorInterface>& async_executor,
    const shared_ptr<HttpClientInterface>& http_client,
    std::unique_ptr<HttpRequestResponseAuthInterceptorInterface> http_helper,
    std::chrono::seconds auth_cache_entry_lifetime)
    : AuthorizationProxyBase(async_executor, auth_cache_entry_lifetime),
      server_endpoint_uri_(make_shared<string>(server_endpoint_url)),
      http_client_(http_client),
      http_helper_(std::move(http_helper)) {}

ExecutionResult AuthorizationProxy::AuthorizeInternal(
    AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&
        authorization_context) noexcept {
  const auto& request = *authorization_context.request;
  auto http_request = make_shared<HttpRequest>();
  http_request->method = HttpMethod::POST;
  http_request->path = server_endpoint_uri_;
  http_request->headers = make_shared<HttpHeaders>();

  auto execution_result = http_helper_->PrepareRequest(
      request.authorization_metadata, *http_request);
  if (!execution_result.Successful()) {
    SCP_ERROR(kAuthorizationProxy, kZeroUuid, execution_result,
              "Failed adding headers to request");
    return FailureExecutionResult(errors::SC_AUTHORIZATION_PROXY_BAD_REQUEST);
  }

  AsyncContext<HttpRequest, HttpResponse> http_context(
      std::move(http_request),
      bind(&AuthorizationProxy::HandleAuthorizeResponse, this,
           authorization_context, _1),
      authorization_context);
  auto result = http_client_->PerformRequest(http_context);
  if (!result.Successful()) {
    return RetryExecutionResult(
        errors::SC_AUTHORIZATION_PROXY_REMOTE_UNAVAILABLE);
  }

  return SuccessExecutionResult();
}

void AuthorizationProxy::HandleAuthorizeResponse(
    AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&
        authorization_context,
    AsyncContext<HttpRequest, HttpResponse>& http_context) {
  if (!http_context.result.Successful()) {
    // Bubbling client error up the stack
    authorization_context.result = http_context.result;
    authorization_context.Finish();
    return;
  }

  auto metadata_or = http_helper_->ObtainAuthorizedMetadataFromResponse(
      authorization_context.request->authorization_metadata,
      *(http_context.response));
  if (!metadata_or.Successful()) {
    authorization_context.result = metadata_or.result();
    authorization_context.Finish();
    return;
  }

  authorization_context.response = make_shared<AuthorizationProxyResponse>();
  authorization_context.response->authorized_metadata = std::move(*metadata_or);
  authorization_context.result = SuccessExecutionResult();
  authorization_context.Finish();
}
}  // namespace google::scp::core
