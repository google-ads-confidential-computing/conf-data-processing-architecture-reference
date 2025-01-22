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
#include <shared_mutex>
#include <string>
#include <unordered_map>

#include <tink/hybrid/internal/hpke_context.h>
#include <tink/hybrid_decrypt.h>
#include <tink/hybrid_encrypt.h>
#include <tink/input_stream.h>
#include <tink/mac.h>

#include "core/common/auto_expiry_concurrent_map/src/auto_expiry_concurrent_map.h"
#include "core/interface/async_context.h"
#include "core/interface/async_executor_interface.h"
#include "core/interface/service_interface.h"
#include "google/protobuf/any.pb.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/crypto_client/crypto_client_interface.h"
#include "public/cpio/interface/crypto_client/type_def.h"
#include "public/cpio/proto/crypto_service/v1/crypto_service.pb.h"

#include "error_codes.h"

namespace google::scp::cpio::client_providers {
/**
 * @copydoc CryptoClientProviderInterface
 */
class CryptoClientProvider : public CryptoClientInterface {
 public:
  /**
   * @brief Construct a new Crypto Client Provider object
   *
   * @param options
   * @param async_executor Optional parameter, the primitives will be cached if
   * there is async_executor provided.
   */
  explicit CryptoClientProvider(
      const std::shared_ptr<CryptoClientOptions>& options,
      const std::shared_ptr<scp::core::AsyncExecutorInterface>& async_executor =
          nullptr);

  core::ExecutionResult Init() noexcept override;

  core::ExecutionResult Run() noexcept override;

  core::ExecutionResult Stop() noexcept override;
  core::ExecutionResultOr<cmrt::sdk::crypto_service::v1::HpkeEncryptResponse>
  HpkeEncryptSync(const cmrt::sdk::crypto_service::v1::HpkeEncryptRequest&
                      request) noexcept override;

  core::ExecutionResultOr<cmrt::sdk::crypto_service::v1::HpkeDecryptResponse>
  HpkeDecryptSync(const cmrt::sdk::crypto_service::v1::HpkeDecryptRequest&
                      request) noexcept override;

  core::ExecutionResultOr<cmrt::sdk::crypto_service::v1::AeadEncryptResponse>
  AeadEncryptSync(const cmrt::sdk::crypto_service::v1::AeadEncryptRequest&
                      request) noexcept override;

  core::ExecutionResultOr<cmrt::sdk::crypto_service::v1::AeadDecryptResponse>
  AeadDecryptSync(const cmrt::sdk::crypto_service::v1::AeadDecryptRequest&
                      request) noexcept override;

  core::ExecutionResultOr<std::unique_ptr<::crypto::tink::InputStream>>
  AeadDecryptStreamSync(const google::scp::cpio::AeadDecryptStreamRequest&
                            request) noexcept override;

  core::ExecutionResultOr<std::unique_ptr<::crypto::tink::OutputStream>>
  AeadEncryptStreamSync(const google::scp::cpio::AeadEncryptStreamRequest&
                            request) noexcept override;

  core::ExecutionResultOr<cmrt::sdk::crypto_service::v1::ComputeMacResponse>
  ComputeMacSync(const cmrt::sdk::crypto_service::v1::ComputeMacRequest&
                     request) noexcept override;

 private:
  core::ExecutionResultOr<cmrt::sdk::crypto_service::v1::HpkeEncryptResponse>
  HpkeEncryptUsingExternalInterface(
      const cmrt::sdk::crypto_service::v1::HpkeEncryptRequest&) noexcept;

  core::ExecutionResultOr<cmrt::sdk::crypto_service::v1::HpkeDecryptResponse>
  HpkeDecryptUsingExternalInterface(
      const cmrt::sdk::crypto_service::v1::HpkeDecryptRequest&) noexcept;

 protected:
  /// HpkeParams passed in from configuration which will override the default
  /// params.
  std::shared_ptr<CryptoClientOptions> options_;

 private:
  core::ExecutionResultOr<std::shared_ptr<::crypto::tink::HybridEncrypt>>
  GetHybridEncryptPrimitive(const std::string& key) noexcept;
  core::ExecutionResultOr<std::shared_ptr<::crypto::tink::HybridDecrypt>>
  GetHybridDecryptPrimitive(const std::string& key) noexcept;
  core::ExecutionResultOr<std::shared_ptr<::crypto::tink::Mac>> GetMacPrimitive(
      const std::string& key) noexcept;

  std::unique_ptr<scp::core::common::AutoExpiryConcurrentMap<
      std::string, std::shared_ptr<::crypto::tink::HybridEncrypt>>>
      hybrid_encrypt_primitive_cache_;
  std::unique_ptr<scp::core::common::AutoExpiryConcurrentMap<
      std::string, std::shared_ptr<::crypto::tink::HybridDecrypt>>>
      hybrid_decrypt_primitive_cache_;
  std::unique_ptr<scp::core::common::AutoExpiryConcurrentMap<
      std::string, std::shared_ptr<::crypto::tink::Mac>>>
      mac_primitive_cache_;
};
}  // namespace google::scp::cpio::client_providers
