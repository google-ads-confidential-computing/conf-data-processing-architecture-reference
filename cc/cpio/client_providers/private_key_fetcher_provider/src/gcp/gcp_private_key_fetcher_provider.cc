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

#include "gcp_private_key_fetcher_provider.h"

#include <utility>
#include <vector>

#include "absl/strings/str_cat.h"
#include "core/interface/http_client_interface.h"
#include "cpio/client_providers/interface/auth_token_provider_interface.h"
#include "cpio/client_providers/interface/role_credentials_provider_interface.h"
#include "cpio/client_providers/private_key_fetcher_provider/src/private_key_fetcher_provider_utils.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/core/interface/execution_result_or_macros.h"

#include "error_codes.h"

using google::scp::core::AsyncContext;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::HttpClientInterface;
using google::scp::core::HttpHeaders;
using google::scp::core::HttpMethod;
using google::scp::core::HttpRequest;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::Uri;
using google::scp::core::common::kZeroUuid;
using google::scp::core::errors::
    SC_GCP_PRIVATE_KEY_FETCHER_PROVIDER_CREDENTIALS_PROVIDER_NOT_FOUND;
using std::bind;
using std::make_shared;
using std::move;
using std::shared_ptr;
using std::string;
using std::vector;
using std::placeholders::_1;

namespace {
constexpr char kGcpPrivateKeyFetcherProvider[] = "GcpPrivateKeyFetcherProvider";
constexpr char kAuthorizationHeaderKey[] = "Authorization";
constexpr char kBearerTokenPrefix[] = "Bearer ";
constexpr char kVersionNumberBetaSuffix[] = "/v1beta";
constexpr char kVersionNumberSuffix[] = "/v1";
constexpr char kEncryptionKeyUrlSuffix[] = "/encryptionKeys";
constexpr char kActiveEncryptionKeyUrlSuffix[] = "/activeKeys";
constexpr char kKeySetName[] = "sets";
constexpr char kListKeysByTimeUri[] = ":recent";
constexpr char kMaxAgeSecondsQueryParameter[] = "maxAgeSeconds=";
}  // namespace

namespace google::scp::cpio::client_providers {

ExecutionResult GcpPrivateKeyFetcherProvider::Init() noexcept {
  RETURN_IF_FAILURE(PrivateKeyFetcherProvider::Init());

  if (!auth_token_provider_) {
    auto execution_result = FailureExecutionResult(
        SC_GCP_PRIVATE_KEY_FETCHER_PROVIDER_CREDENTIALS_PROVIDER_NOT_FOUND);
    SCP_ERROR(kGcpPrivateKeyFetcherProvider, kZeroUuid, execution_result,
              "Failed to get credentials provider.");
    return execution_result;
  }

  return SuccessExecutionResult();
}

void GcpPrivateKeyFetcherProvider::SignHttpRequest(
    AsyncContext<PrivateKeyFetchingRequest, core::HttpRequest>&
        sign_request_context) noexcept {
  auto request = make_shared<GetSessionTokenForTargetAudienceRequest>();
  const auto& uri =
      sign_request_context.request->key_endpoint->gcp_cloud_function_url();
  request->token_target_audience_uri = make_shared<string>(uri);
  AsyncContext<GetSessionTokenForTargetAudienceRequest, GetSessionTokenResponse>
      get_token_context(
          move(request),
          bind(&GcpPrivateKeyFetcherProvider::OnGetSessionTokenCallback, this,
               sign_request_context, _1),
          sign_request_context);

  auth_token_provider_->GetSessionTokenForTargetAudience(get_token_context);
}

void GcpPrivateKeyFetcherProvider::OnGetSessionTokenCallback(
    AsyncContext<PrivateKeyFetchingRequest, core::HttpRequest>&
        sign_request_context,
    AsyncContext<GetSessionTokenForTargetAudienceRequest,
                 GetSessionTokenResponse>& get_token_context) noexcept {
  if (!get_token_context.result.Successful()) {
    SCP_ERROR_CONTEXT(
        kGcpPrivateKeyFetcherProvider, sign_request_context,
        get_token_context.result,
        "Failed to get the access token for audience target %s.",
        get_token_context.request->token_target_audience_uri->c_str());
    sign_request_context.result = get_token_context.result;
    sign_request_context.Finish();
    return;
  }

  const auto& access_token = *get_token_context.response->session_token;
  auto http_request = CreateHttpRequest(*sign_request_context.request);
  http_request->headers = make_shared<core::HttpHeaders>();
  http_request->headers->insert(
      {string(kAuthorizationHeaderKey),
       absl::StrCat(kBearerTokenPrefix, access_token)});
  sign_request_context.response = move(http_request);
  sign_request_context.result = SuccessExecutionResult();
  sign_request_context.Finish();
}

shared_ptr<HttpRequest> GcpPrivateKeyFetcherProvider::CreateHttpRequest(
    const PrivateKeyFetchingRequest& request) noexcept {
  auto http_request = make_shared<HttpRequest>();
  http_request->method = HttpMethod::GET;

  auto endpoint = request.key_endpoint->endpoint();
  size_t version_position = endpoint.find(kVersionNumberSuffix);
  if (version_position != string::npos) {
    endpoint = endpoint.substr(0, version_position);
  }

  string base_uri;
  if (request.key_id && !request.key_id->empty()) {
    base_uri = absl::StrCat(endpoint, kVersionNumberBetaSuffix,
                            kEncryptionKeyUrlSuffix, "/", *request.key_id);
    http_request->path = make_shared<Uri>(base_uri);
    return http_request;
  }
  if ((!request.key_id || request.key_id->empty()) &&
      request.max_age_seconds == 0) {
    base_uri = absl::StrCat(
        endpoint, kVersionNumberBetaSuffix, kEncryptionKeyUrlSuffix, "/",
        kKeySetName, "/", *request.key_set_name, kActiveEncryptionKeyUrlSuffix);
    http_request->path = make_shared<Uri>(base_uri);
    return http_request;
  }
  base_uri =
      absl::StrCat(endpoint, kVersionNumberBetaSuffix, "/", kKeySetName, "/");
  if (request.key_set_name) {
    absl::StrAppend(&base_uri, *request.key_set_name);
  }
  absl::StrAppend(&base_uri, kEncryptionKeyUrlSuffix, kListKeysByTimeUri);
  http_request->path = make_shared<Uri>(base_uri);
  http_request->query = make_shared<string>(
      absl::StrCat(kMaxAgeSecondsQueryParameter, request.max_age_seconds));
  return http_request;
}

#ifndef TEST_CPIO
std::shared_ptr<PrivateKeyFetcherProviderInterface>
PrivateKeyFetcherProviderFactory::Create(
    const shared_ptr<HttpClientInterface>& http_client,
    const shared_ptr<RoleCredentialsProviderInterface>&
        role_credentials_provider,
    const shared_ptr<AuthTokenProviderInterface>& auth_token_provider) {
  return make_shared<GcpPrivateKeyFetcherProvider>(http_client,
                                                   auth_token_provider);
}
#endif
}  // namespace google::scp::cpio::client_providers
