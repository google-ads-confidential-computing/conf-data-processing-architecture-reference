/*
 * Copyright 2026 Google LLC
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

#include "coordinator_wrapped_key_handler_with_cache.h"

#include <chrono>
#include <memory>
#include <string>

#include "absl/base/no_destructor.h"
#include "absl/container/flat_hash_set.h"
#include "core/common/global_logger/src/global_logger.h"
#include "core/common/time_provider/src/time_provider.h"
#include "core/interface/async_executor_interface.h"
#include "public/core/interface/execution_result.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/cpio/interface/crypto_client/crypto_client_interface.h"
#include "public/cpio/interface/error_codes.h"
#include "public/cpio/proto/crypto_service/v1/crypto_service.pb.h"
#include "public/cpio/utils/dual_writing_metric_client/interface/dual_writing_metric_client_interface.h"
#include "public/cpio/utils/key_fetching/interface/key_fetcher_with_cache_interface.h"
#include "public/cpio/utils/key_fetching/src/error_codes.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"

using google::cmrt::sdk::crypto_service::v1::HpkeDecryptRequest;
using google::cmrt::sdk::v1::CloudWrappedKey;
using google::cmrt::sdk::v1::CoordinatorWrappedKey;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResult;
using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::StatusCode;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::core::common::TimeProvider;
using google::scp::core::errors::SC_CPIO_ENTITY_NOT_FOUND;
using google::scp::core::errors::SC_CPIO_INVALID_ARGUMENT;
using google::scp::core::errors::SC_CPIO_KEY_COUNT_MISMATCH;
using google::scp::core::errors::SC_CPIO_KEY_NOT_FOUND;
using google::scp::cpio::CryptoClientInterface;
using google::scp::cpio::DualWritingMetricClientInterface;
using google::scp::cpio::KeyFetcherWithCacheInterface;
using google::scp::cpio::KeyFetchingType;
using google::scp::cpio::KeyType;
using google::scp::cpio::PushKeyFetchingLatencyMetric;
using std::shared_ptr;
using std::string;
using std::chrono::duration_cast;
using std::chrono::milliseconds;
using std::chrono::nanoseconds;

namespace google::scp::cpio {

namespace {
constexpr char kCoordinatorWrappedKeyHandlerWithCacheComponentName[] =
    "CoordinatorWrappedKeyHandlerWithCache";
constexpr milliseconds kLogPeriod = milliseconds(1000);

ExecutionResult ValidateCoordinatorWrappedKey(
    const CloudWrappedKey& wrapped_key) noexcept {
  if (!wrapped_key.has_coordinator_wrapped_key()) {
    auto result = FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT);
    SCP_ERROR_EVERY_PERIOD(
        kLogPeriod, kCoordinatorWrappedKeyHandlerWithCacheComponentName,
        kZeroUuid, result,
        "Wrapped key does not have coordinator_wrapped_key.");
    return result;
  }
  const auto& coordinator_wrapped_key = wrapped_key.coordinator_wrapped_key();
  if (coordinator_wrapped_key.key_id().empty() ||
      coordinator_wrapped_key.encrypted_dek().empty()) {
    auto result = FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT);
    SCP_ERROR_EVERY_PERIOD(
        kLogPeriod, kCoordinatorWrappedKeyHandlerWithCacheComponentName,
        kZeroUuid, result,
        "Coordinator wrapped key is missing key_id or encrypted_dek.");
    return result;
  }
  return SuccessExecutionResult();
}
}  // namespace

CoordinatorWrappedKeyHandlerWithCache::CoordinatorWrappedKeyHandlerWithCache(
    shared_ptr<AsyncExecutorInterface>& async_executor,
    CryptoClientInterface& crypto_client,
    KeyFetcherWithCacheInterface& coordinator_key_cache,
    WrappedKeyHandlerOptions wrapped_key_handler_options,
    DualWritingMetricClientInterface& metric_client)
    : WrappedKeyHandlerWithCacheBase(
           async_executor, wrapped_key_handler_options, metric_client),
      crypto_client_(crypto_client),
      coordinator_key_cache_(coordinator_key_cache) {}

string CoordinatorWrappedKeyHandlerWithCache::GetKeyType() noexcept {
  return KeyType::kCoordinatorWrappedKey;
}

ExecutionResultOr<string> CoordinatorWrappedKeyHandlerWithCache::GetKey(
    const CloudWrappedKey& wrapped_key) noexcept {
  RETURN_IF_FAILURE(ValidateCoordinatorWrappedKey(wrapped_key));
  return GetKeyInternal(wrapped_key.coordinator_wrapped_key());
}

ExecutionResultOr<bool> CoordinatorWrappedKeyHandlerWithCache::ValidateKey(
    const CloudWrappedKey& wrapped_key) noexcept {
  RETURN_IF_FAILURE(ValidateCoordinatorWrappedKey(wrapped_key));
  return coordinator_key_cache_.ValidateKey(
      wrapped_key.coordinator_wrapped_key().key_id());
}

ExecutionResultOr<string> CoordinatorWrappedKeyHandlerWithCache::DecryptDek(
    const CoordinatorWrappedKey& wrapped_key) noexcept {
  auto decryption_start_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();

  auto key_or = coordinator_key_cache_.GetKey(wrapped_key.key_id());
  if (!key_or.Successful()) {
    SCP_ERROR_EVERY_PERIOD(kLogPeriod,
                           kCoordinatorWrappedKeyHandlerWithCacheComponentName,
                           kZeroUuid, key_or.result(),
                           "Failed to fetch coordinator key for key_id (%s).",
                           wrapped_key.key_id().c_str());
    return key_or.result();
  }
  if (key_or->private_key.empty()) {
    auto result = FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND);
    SCP_ERROR_EVERY_PERIOD(
        kLogPeriod, kCoordinatorWrappedKeyHandlerWithCacheComponentName,
        kZeroUuid, result,
        "Coordinator key is missing private key for key_id (%s).",
        wrapped_key.key_id().c_str());
    return result;
  }

  HpkeDecryptRequest decrypt_request;
  *decrypt_request.mutable_tink_key_binary() = key_or->private_key;
  decrypt_request.mutable_encrypted_data()->set_ciphertext(
      wrapped_key.encrypted_dek());

  auto response_or = crypto_client_.HpkeDecryptSync(decrypt_request);

  auto decryption_end_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();
  auto latency_in_millis =
      duration_cast<milliseconds>(
          nanoseconds(decryption_end_time_in_ns - decryption_start_time_in_ns))
          .count();
  PushKeyFetchingLatencyMetric(
      metric_client_, GetKeyType(), KeyFetchingType::kOnDemand,
      /*keyset_name=*/kDummyLabelValue, latency_in_millis);

  if (!response_or.Successful()) {
    return response_or.result();
  }
  return response_or->payload();
}

string
CoordinatorWrappedKeyHandlerWithCache::MapToWrappedKeyFetchingErrorString(
    google::scp::core::ExecutionResult error_result) noexcept {
  if (error_result.status_code == SC_CPIO_KEY_NOT_FOUND ||
      error_result.status_code == SC_CPIO_ENTITY_NOT_FOUND ||
      error_result.status_code == SC_CPIO_INVALID_ARGUMENT) {
    return KeyFetchingErrorType::kInvalidKeyId;
  }
  return KeyFetchingErrorType::kGenericError;
}

bool CoordinatorWrappedKeyHandlerWithCache::IsRetryableDecryptionError(
    StatusCode error_code) noexcept {
  static const absl::NoDestructor<absl::flat_hash_set<StatusCode>>
      kNonRetryableDecryptionErrors(
          {SC_CPIO_KEY_NOT_FOUND, SC_CPIO_KEY_COUNT_MISMATCH,
           SC_CPIO_ENTITY_NOT_FOUND, SC_CPIO_INVALID_ARGUMENT});
  return !kNonRetryableDecryptionErrors->contains(error_code);
}

}  // namespace google::scp::cpio
