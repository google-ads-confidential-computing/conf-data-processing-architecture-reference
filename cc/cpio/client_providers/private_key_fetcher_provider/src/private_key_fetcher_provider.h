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

#include <memory>
#include <string>
#include <utility>

#include "core/interface/async_context.h"
#include "core/interface/http_client_interface.h"
#include "cpio/client_providers/interface/private_key_fetcher_provider_interface.h"
#include "cpio/common/src/common_error_codes.h"
#include "public/core/interface/execution_result.h"

#include "error_codes.h"
#include "private_key_fetcher_provider_utils.h"

namespace google::scp::cpio::client_providers {
/*! @copydoc PrivateKeyFetcherProviderInterface
 */
class PrivateKeyFetcherProvider : public PrivateKeyFetcherProviderInterface {
 public:
  virtual ~PrivateKeyFetcherProvider() = default;

  explicit PrivateKeyFetcherProvider(
      const std::shared_ptr<core::HttpClientInterface>& http_client)
      : http_client_(http_client) {}

  core::ExecutionResult Init() noexcept override;

  core::ExecutionResult Run() noexcept override;

  core::ExecutionResult Stop() noexcept override;

  void FetchPrivateKey(
      core::AsyncContext<PrivateKeyFetchingRequest, PrivateKeyFetchingResponse>&
          private_key_fetching_context) noexcept override;

  void FetchKeysetMetadata(core::AsyncContext<KeysetMetadataFetchingRequest,
                                              KeysetMetadataFetchingResponse>&
                               context) noexcept override;

 protected:
  /**
   * @brief Sign Http request with credentials for PrivateKeyFetchingRequest
   *
   * @param sign_http_request_context execution context.
   */
  virtual void SignHttpRequest(
      core::AsyncContext<PrivateKeyFetchingRequest, core::HttpRequest>&
          sign_http_request_context) noexcept = 0;

  /**
   * @brief Sign Http request with credentials for KeysetMetadataFetchingRequest
   *
   * @param sign_http_request_context
   */
  virtual void SignHttpRequest(
      core::AsyncContext<KeysetMetadataFetchingRequest, core::HttpRequest>&
          sign_http_request_context) noexcept {
    sign_http_request_context.result = core::FailureExecutionResult(
        core::errors::SC_COMMON_ERRORS_UNIMPLEMENTED);
    sign_http_request_context.Finish();
  }

  /**
   * @brief Create a Http Request object to query private key vending endpoint.
   *
   * @param private_key_fetching_request request to query private key.
   */
  virtual std::shared_ptr<core::HttpRequest> CreateHttpRequest(
      const PrivateKeyFetchingRequest&
          private_key_fetching_request) noexcept = 0;

  /**
   * @brief Create a Http Request object to get keyset metadata object.
   *
   * @param keyset_metadata_request
   * @return std::shared_ptr<core::HttpRequest>
   */
  virtual std::shared_ptr<core::HttpRequest> CreateHttpRequest(
      const KeysetMetadataFetchingRequest& keyset_metadata_request) noexcept {
    return std::make_shared<core::HttpRequest>();
  }

  /**
   * @brief Triggered to perform http request when signed http request is
   * fetched.
   *
   * @tparam FetchingRequestType Private key service fetching request type,
   * either PrivateKeyFetchingRequest or KeysetMetadataFetchingRequest.
   * @tparam FetchingResponseType Private key service fetching response type,
   * either PrivateKeyFetchingResponse or KeysetMetadataFetchingResponse.
   * @param private_key_service_fetching_context context to fetch private key or
   * keyset metadata.
   * @param sign_http_request_context context to perform sign http request.
   */
  template <typename FetchingRequestType, typename FetchingResponseType>
  void SignHttpRequestCallback(
      core::AsyncContext<FetchingRequestType, FetchingResponseType>&
          private_key_service_fetching_context,
      core::AsyncContext<FetchingRequestType, core::HttpRequest>&
          sign_http_request_context) noexcept {
    if (!sign_http_request_context.result.Successful()) {
      SCP_ERROR_CONTEXT(
          kPrivateKeyFetcherProvider, private_key_service_fetching_context,
          sign_http_request_context.result, "Failed to sign http request.");
      private_key_service_fetching_context.result =
          sign_http_request_context.result;
      private_key_service_fetching_context.Finish();
      return;
    }

    core::AsyncContext<core::HttpRequest, core::HttpResponse>
        http_client_context(
            std::move(sign_http_request_context.response),
            bind(&PrivateKeyFetcherProvider::PrivateKeyFetchingCallback<
                     FetchingRequestType, FetchingResponseType>,
                 this, private_key_service_fetching_context,
                 std::placeholders::_1),
            private_key_service_fetching_context);
    SCP_DEBUG_CONTEXT(kPrivateKeyFetcherProvider,
                      private_key_service_fetching_context,
                      "Starting to perform http request to endpoint %s",
                      http_client_context.request->path->c_str());
    auto execution_result = http_client_->PerformRequest(http_client_context);
    if (!execution_result.Successful()) {
      SCP_ERROR_CONTEXT(
          kPrivateKeyFetcherProvider, private_key_service_fetching_context,
          execution_result,
          "Failed to perform sign http request to reach endpoint %s.",
          http_client_context.request->path->c_str());
      private_key_service_fetching_context.result = execution_result;
      private_key_service_fetching_context.Finish();
    }
  }

  /**
   * @brief Triggered to parse the fetched payload when http response is return.
   *
   * @tparam FetchingRequestType Private key service fetching request type,
   * either PrivateKeyFetchingRequest or KeysetMetadataFetchingRequest.
   * @tparam FetchingResponseType Private key service fetching response type,
   * either PrivateKeyFetchingResponse or KeysetMetadataFetchingResponse.
   * @param private_key_fetching_context context to fetch private key or
   * keyset metadata.
   * @param http_client_context context to make the http reqeust.
   */
  template <typename FetchingRequestType, typename FetchingResponseType>
  void PrivateKeyFetchingCallback(
      core::AsyncContext<FetchingRequestType, FetchingResponseType>&
          private_key_fetching_context,
      core::AsyncContext<core::HttpRequest, core::HttpResponse>&
          http_client_context) noexcept {
    private_key_fetching_context.result = http_client_context.result;
    if (!http_client_context.result.Successful()) {
      SCP_ERROR_CONTEXT(kPrivateKeyFetcherProvider,
                        private_key_fetching_context,
                        private_key_fetching_context.result,
                        "Failed to fetch data from endpoint %s.",
                        http_client_context.request->path->c_str());
      private_key_fetching_context.Finish();
      return;
    }

    FetchingResponseType response;
    auto result = PrivateKeyFetchingClientUtils::ParseFetchingResponse(
        http_client_context.response->body, response);
    if (!result.Successful()) {
      // Since PrivateKeyFetchingRequest.endpoint is modified, http_request.path
      // is the final path of the http request call.
      SCP_ERROR_CONTEXT(
          kPrivateKeyFetcherProvider, private_key_fetching_context,
          private_key_fetching_context.result,
          "Failed to parse the fetched response from endpoint %s.",
          http_client_context.request->path->c_str());
      private_key_fetching_context.result = result;
      private_key_fetching_context.Finish();
      return;
    }
    SCP_DEBUG_CONTEXT(kPrivateKeyFetcherProvider, private_key_fetching_context,
                      "Successfully obtained data from endpoint %s.",
                      http_client_context.request->path->c_str());
    private_key_fetching_context.response =
        std::make_shared<FetchingResponseType>(std::move(response));
    private_key_fetching_context.Finish();
  }

  /// HttpClient for issuing HTTP actions.
  std::shared_ptr<core::HttpClientInterface> http_client_;
  static constexpr char kPrivateKeyFetcherProvider[] =
      "PrivateKeyFetcherProvider";
};
}  // namespace google::scp::cpio::client_providers
