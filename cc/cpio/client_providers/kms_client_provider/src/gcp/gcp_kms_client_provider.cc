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

#include "gcp_kms_client_provider.h"

#include <memory>
#include <set>
#include <string>
#include <utility>

#include "core/common/auto_expiry_concurrent_map/src/auto_expiry_concurrent_map.h"
#include "core/utils/src/base64.h"
#include "cpio/client_providers/interface/role_credentials_provider_interface.h"
#include "google/cloud/kms/key_management_client.h"
#include "google/cloud/status.h"
#include "public/cpio/interface/kms_client/type_def.h"

#include "error_codes.h"
#include "gcp_key_management_service_client.h"

using google::cloud::kms::KeyManagementServiceClient;
using google::cloud::kms::MakeKeyManagementServiceConnection;
using google::cmrt::sdk::kms_service::v1::DecryptRequest;
using google::cmrt::sdk::kms_service::v1::DecryptResponse;
using google::scp::core::AsyncContext;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::AsyncOperation;
using google::scp::core::AsyncPriority;
using google::scp::core::ExecutionResult;
using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::FinishContext;
using google::scp::core::RetryExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::AutoExpiryConcurrentMap;
using google::scp::core::common::kZeroUuid;
using google::scp::core::common::OperationDispatcher;
using google::scp::core::errors::
    SC_GCP_KMS_CLIENT_PROVIDER_BASE64_DECODING_FAILED;
using google::scp::core::errors::
    SC_GCP_KMS_CLIENT_PROVIDER_CIPHERTEXT_NOT_FOUND;
using google::scp::core::errors::SC_GCP_KMS_CLIENT_PROVIDER_CLOUD_UNAVAILABLE;
using google::scp::core::errors::SC_GCP_KMS_CLIENT_PROVIDER_DECRYPTION_FAILED;
using google::scp::core::errors::SC_GCP_KMS_CLIENT_PROVIDER_KEY_ARN_NOT_FOUND;
using google::scp::core::utils::Base64Decode;
using std::bind;
using std::make_shared;
using std::move;
using std::set;
using std::shared_ptr;
using std::string;
using std::to_string;

/// Filename for logging errors
static constexpr char kGcpKmsClientProvider[] = "GcpKmsClientProvider";
static constexpr char kGcpKmsClientAeadDecryptRetryLogTemplate[] =
    "gcp-kms-client-aead-decrypt-%s";

const set<google::cloud::StatusCode> kRetryStatusCodes = {
    google::cloud::StatusCode::kUnavailable,
    /* The reason to retry on following status codes is because gRpc
       server sometimes returns UNKNOWN or INTERNAL when using OAuth2
       credentials. The root cause appears to be a 503 error during
       token fetching and should be retriable. The gRpc server also
       returns UNKNOWN status code for real authentication error, so
       this client will also retries on it.
    */
    google::cloud::StatusCode::kUnknown,
    google::cloud::StatusCode::kInternal,
    google::cloud::StatusCode::kCancelled,
};

namespace google::scp::cpio::client_providers {
ExecutionResult GcpKmsClientProvider::Init() noexcept {
  return gcp_kms_service_client_cache_->Init();
}

ExecutionResult GcpKmsClientProvider::Run() noexcept {
  return gcp_kms_service_client_cache_->Run();
}

ExecutionResult GcpKmsClientProvider::Stop() noexcept {
  return gcp_kms_service_client_cache_->Stop();
}

void GcpKmsClientProvider::Decrypt(
    AsyncContext<DecryptRequest, DecryptResponse>& decrypt_context) noexcept {
  if (decrypt_context.request->ciphertext().empty()) {
    auto execution_result =
        FailureExecutionResult(SC_GCP_KMS_CLIENT_PROVIDER_CIPHERTEXT_NOT_FOUND);
    SCP_ERROR_CONTEXT(kGcpKmsClientProvider, decrypt_context, execution_result,
                      "Failed to get cipher text from decryption request.");
    decrypt_context.result = execution_result;
    decrypt_context.Finish();
    return;
  }

  if (decrypt_context.request->key_resource_name().empty()) {
    auto execution_result =
        FailureExecutionResult(SC_GCP_KMS_CLIENT_PROVIDER_KEY_ARN_NOT_FOUND);
    SCP_ERROR_CONTEXT(
        kGcpKmsClientProvider, decrypt_context, execution_result,
        "Failed to get Key resource name from decryption request.");
    decrypt_context.result = execution_result;
    decrypt_context.Finish();
    return;
  }

  AsyncOperation decrypt_call =
      bind(&GcpKmsClientProvider::AeadDecrypt, this, decrypt_context);

  AsyncOperation decrypt_call_with_retries = [this, decrypt_context]() mutable {
    SCP_INFO(kGcpKmsClientProvider, kZeroUuid,
             kGcpKmsClientAeadDecryptRetryLogTemplate, "CALL");
    io_operation_dispatcher_
        .Dispatch<AsyncContext<DecryptRequest, DecryptResponse>>(
            decrypt_context,
            [this](AsyncContext<DecryptRequest, DecryptResponse>& context) {
              AeadDecrypt(context);
              return SuccessExecutionResult();
            });
  };

  if (auto schedule_result = io_async_executor_->Schedule(
          kms_client_options_->enable_gcp_kms_client_retries
              ? decrypt_call_with_retries
              : decrypt_call,
          AsyncPriority::Normal);
      !schedule_result.Successful()) {
    decrypt_context.result = schedule_result;
    SCP_ERROR_CONTEXT(kGcpKmsClientProvider, decrypt_context,
                      decrypt_context.result,
                      "AEAD decrypt failed to be scheduled");
    decrypt_context.Finish();
  }
}

shared_ptr<GcpKeyManagementServiceClientInterface>
GcpKmsClientProvider::GetOrCreateGcpKeyManagementServiceClient(
    const DecryptRequest& request) noexcept {
  shared_ptr<GcpKeyManagementServiceClientInterface> client_found;
  if (kms_client_options_->enable_gcp_kms_client_cache) {
    if (!request.gcp_wip_provider().empty() &&
        gcp_kms_service_client_cache_
            ->Find(request.gcp_wip_provider(), client_found)
            .Successful()) {
      return client_found;
    }
  }

  // TODO: delete account_identity after migrate release test to use new
  // attestation approach.
  auto client = gcp_kms_factory_->CreateGcpKeyManagementServiceClient(
      request.gcp_wip_provider(), request.account_identity());
  if (!kms_client_options_->enable_gcp_kms_client_cache ||
      request.gcp_wip_provider().empty()) {
    return client;
  }

  std::pair<string, shared_ptr<GcpKeyManagementServiceClientInterface>>
      client_pair;
  client_pair.first = request.gcp_wip_provider();
  client_pair.second = client;
  // Ignore insert error
  gcp_kms_service_client_cache_->Insert(client_pair, client_found);
  return client_found;
}

bool GcpKmsClientProvider::ShouldRetryOnStatus(
    cloud::StatusCode status_code) noexcept {
  return kms_client_options_->enable_gcp_kms_client_retries &&
         IsStatusCodeRetriable(status_code);
}

bool GcpKmsClientProvider::IsStatusCodeRetriable(
    cloud::StatusCode status_code) noexcept {
  return kRetryStatusCodes.find(status_code) != kRetryStatusCodes.end();
}

void GcpKmsClientProvider::AeadDecrypt(
    AsyncContext<DecryptRequest, DecryptResponse>& decrypt_context) noexcept {
  auto decoded_ciphertext_or =
      Base64Decode(decrypt_context.request->ciphertext());
  if (!decoded_ciphertext_or.Successful()) {
    auto execution_result = FailureExecutionResult(
        SC_GCP_KMS_CLIENT_PROVIDER_BASE64_DECODING_FAILED);
    SCP_ERROR_CONTEXT(kGcpKmsClientProvider, decrypt_context, execution_result,
                      "Failed to decode the ciphertext using base64.");
    decrypt_context.result = execution_result;
    decrypt_context.Finish();
    return;
  }

  auto gcp_kms =
      GetOrCreateGcpKeyManagementServiceClient(*decrypt_context.request);

  string decoded_ciphertext = decoded_ciphertext_or.release();
  cloud::kms::v1::DecryptRequest req;
  req.set_name(decrypt_context.request->key_resource_name());
  req.set_ciphertext(decoded_ciphertext);

  auto response_or = gcp_kms->Decrypt(req);
  if (!response_or) {
    if (ShouldRetryOnStatus(response_or.status().code())) {
      SCP_INFO_CONTEXT(kGcpKmsClientProvider, decrypt_context,
                       "Decryption failed with RETRYABLE code %s and error "
                       "message %s. Retry attempt: %d",
                       StatusCodeToString(response_or.status().code()).c_str(),
                       response_or.status().message().c_str(),
                       decrypt_context.retry_count);
      auto execution_result =
          RetryExecutionResult(SC_GCP_KMS_CLIENT_PROVIDER_CLOUD_UNAVAILABLE);
      FinishContext(execution_result, decrypt_context, cpu_async_executor_);
      return;
    }

    auto execution_result =
        FailureExecutionResult(SC_GCP_KMS_CLIENT_PROVIDER_DECRYPTION_FAILED);
    SCP_ERROR_CONTEXT(kGcpKmsClientProvider, decrypt_context, execution_result,
                      "Decryption failed with code %s and error message %s.",
                      StatusCodeToString(response_or.status().code()).c_str(),
                      response_or.status().message().c_str());
    FinishContext(execution_result, decrypt_context, cpu_async_executor_);
    return;
  }
  decrypt_context.response = make_shared<DecryptResponse>();
  decrypt_context.response->set_plaintext(std::move(response_or->plaintext()));

  FinishContext(SuccessExecutionResult(), decrypt_context, cpu_async_executor_);
}

shared_ptr<GcpKeyManagementServiceClientInterface>
GcpKmsFactory::CreateGcpKeyManagementServiceClient(
    const string& wip_provider,
    const string& service_account_to_impersonate) noexcept {
  auto key_management_service_client = CreateKeyManagementServiceClient(
      wip_provider, service_account_to_impersonate);
  return make_shared<GcpKeyManagementServiceClient>(
      key_management_service_client);
}

void GcpKmsClientProvider::RetryInformationEventHandler(
    const OperationDispatcher::RetryInformationEvent event) noexcept {
  string count_message = "";
  switch (event.retry_event) {
    // This event informs that a call has been retried and a retry was
    // successful. This event is not published when a call was successful
    // without a retry attempt.
    case OperationDispatcher::RetryEvent::kSuccessAfterRetry:
      SCP_INFO(kGcpKmsClientProvider, kZeroUuid,
               kGcpKmsClientAeadDecryptRetryLogTemplate, "RETRY_SUCCESS");

      count_message = "RETRY_SUCCESS_COUNT:" + to_string(event.retry_count);
      SCP_INFO(kGcpKmsClientProvider, kZeroUuid,
               kGcpKmsClientAeadDecryptRetryLogTemplate, count_message.c_str());
      break;

    // This event informs that a call has been retried, but still failed. That
    // is, it at least retried once or the maximum number of attempts has been
    // reached.
    case OperationDispatcher::RetryEvent::kFailureAfterRetry:
      SCP_INFO(kGcpKmsClientProvider, kZeroUuid,
               kGcpKmsClientAeadDecryptRetryLogTemplate, "RETRY_FAILURE");
      count_message = "RETRY_FAILURE_COUNT:" + to_string(event.retry_count);
      SCP_INFO(kGcpKmsClientProvider, kZeroUuid,
               kGcpKmsClientAeadDecryptRetryLogTemplate, count_message.c_str());
      break;

    // This event informs that an error occurred and has been ignored - not
    // retried. An error is ignored when the client code directly retruns
    // FailureExecutionResult.
    case OperationDispatcher::RetryEvent::kNonRetriableFailure:
      SCP_INFO(kGcpKmsClientProvider, kZeroUuid,
               kGcpKmsClientAeadDecryptRetryLogTemplate, "FAILURE");
      break;

    default:
      break;
  }
}

#ifndef TEST_CPIO
shared_ptr<KmsClientProviderInterface> KmsClientProviderFactory::Create(
    const shared_ptr<KmsClientOptions>& options,
    const shared_ptr<RoleCredentialsProviderInterface>&
        role_credentials_provider,
    const shared_ptr<AsyncExecutorInterface>& io_async_executor,
    const shared_ptr<AsyncExecutorInterface>& cpu_async_executor) noexcept {
  return make_shared<GcpKmsClientProvider>(io_async_executor,
                                           cpu_async_executor, options);
}
#endif
}  // namespace google::scp::cpio::client_providers
