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

#include "core/common/auto_expiry_concurrent_map/src/auto_expiry_concurrent_map.h"
#include "core/common/operation_dispatcher/src/operation_dispatcher.h"
#include "core/interface/async_context.h"
#include "cpio/client_providers/interface/kms_client_provider_interface.h"
#include "cpio/client_providers/kms_client_provider/interface/gcp/gcp_key_management_service_client_interface.h"
#include "google/cloud/kms/key_management_client.h"
#include "public/core/interface/execution_result.h"

#include "error_codes.h"

namespace google::scp::cpio::client_providers {
constexpr int kKmsServiceClientCacheLifetimeSeconds = 3600 * 24;  // 1 day

class GcpKmsFactory;

/*! @copydoc KmsClientProviderInterface
 */
class GcpKmsClientProvider : public KmsClientProviderInterface {
 public:
  explicit GcpKmsClientProvider(
      const std::shared_ptr<core::AsyncExecutorInterface>& io_async_executor,
      const std::shared_ptr<core::AsyncExecutorInterface>& cpu_async_executor,
      const std::shared_ptr<KmsClientOptions>& kms_client_options,
      const std::shared_ptr<GcpKmsFactory>& gcp_kms_factory =
          std::make_shared<GcpKmsFactory>())
      : io_async_executor_(io_async_executor),
        cpu_async_executor_(cpu_async_executor),
        kms_client_options_(kms_client_options),
        gcp_kms_factory_(gcp_kms_factory),
        gcp_kms_service_client_cache_(
            std::make_unique<core::common::AutoExpiryConcurrentMap<
                std::string,
                std::shared_ptr<GcpKeyManagementServiceClientInterface>>>(
                kms_client_options->gcp_kms_client_cache_lifetime.count(),
                true /* extend_entry_lifetime_on_access */,
                true /* block_entry_while_eviction */,
                [](auto&, auto&, auto should_delete_entry) {
                  should_delete_entry(true);
                },
                cpu_async_executor)),
        io_operation_dispatcher_(
            io_async_executor,
            core::common::RetryStrategy(
                core::common::RetryStrategyType::Exponential,
                kms_client_options->gcp_kms_client_retry_initial_interval
                    .count(),
                kms_client_options->gcp_kms_client_retry_total_retries)) {}

  core::ExecutionResult Init() noexcept override;

  core::ExecutionResult Run() noexcept override;

  core::ExecutionResult Stop() noexcept override;

  void Decrypt(core::AsyncContext<cmrt::sdk::kms_service::v1::DecryptRequest,
                                  cmrt::sdk::kms_service::v1::DecryptResponse>&
                   decrypt_context) noexcept override;

 private:
  std::shared_ptr<GcpKeyManagementServiceClientInterface>
  GetOrCreateGcpKeyManagementServiceClient(
      const cmrt::sdk::kms_service::v1::DecryptRequest& request) noexcept;

  bool ShouldRetryOnStatus(google::cloud::StatusCode status_code) noexcept;

  bool IsStatusCodeRetriable(google::cloud::StatusCode status_code) noexcept;

  void AeadDecrypt(
      core::AsyncContext<cmrt::sdk::kms_service::v1::DecryptRequest,
                         cmrt::sdk::kms_service::v1::DecryptResponse>&
          decrypt_context) noexcept;

  const std::shared_ptr<core::AsyncExecutorInterface> io_async_executor_,
      cpu_async_executor_;
  std::shared_ptr<KmsClientOptions> kms_client_options_;
  std::shared_ptr<GcpKmsFactory> gcp_kms_factory_;

  // KeyManagementServiceClient map keyed by WIP when WIP is not empty.
  std::unique_ptr<core::common::AutoExpiryConcurrentMap<
      std::string, std::shared_ptr<GcpKeyManagementServiceClientInterface>>>
      gcp_kms_service_client_cache_;

  core::common::OperationDispatcher io_operation_dispatcher_;
};

/// Provides GcpKms.
class GcpKmsFactory {
 public:
  /**
   * @brief Creates GcpKeyManagementServiceClientInterface.
   *
   * @param wip_provider WIP provider.
   * @param service_account_to_impersonate servic account to impersonate.
   * @return
   * std::shared_ptr<GcpKeyManagementServiceClientInterface> the
   * creation result.
   */
  virtual std::shared_ptr<GcpKeyManagementServiceClientInterface>
  CreateGcpKeyManagementServiceClient(
      const std::string& wip_provider,
      const std::string& service_account_to_impersonate) noexcept;

 private:
  /**
   * @brief Creates KeyManagementServiceClient.
   *
   * @param wip_provider WIP provider.
   * @param service_account_to_impersonate service account to impersonate.
   * @return
   * std::shared_ptr<cloud::kms::KeyManagementServiceClient> the
   * creation result.
   */
  std::shared_ptr<cloud::kms::KeyManagementServiceClient>
  CreateKeyManagementServiceClient(
      const std::string& wip_provider,
      const std::string& service_account_to_impersonate) noexcept;
};
}  // namespace google::scp::cpio::client_providers
