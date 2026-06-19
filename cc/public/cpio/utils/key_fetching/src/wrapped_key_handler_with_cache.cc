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

#include "wrapped_key_handler_with_cache.h"

#include <memory>
#include <optional>
#include <shared_mutex>
#include <string>
#include <utility>

#include "absl/base/no_destructor.h"
#include "absl/container/flat_hash_set.h"
#include "absl/strings/str_cat.h"
#include "absl/strings/str_join.h"
#include "absl/strings/strip.h"
#include "cpio/common/src/common_error_codes.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/core/interface/execution_result_or_macros.h"
#include "public/cpio/interface/error_codes.h"
#include "public/cpio/utils/key_fetching/src/error_codes.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"
#include "public/cpio/utils/key_fetching/src/wrapped_key_handler_with_cache_interface.h"
#include "public/cpio/utils/metric_instance/interface/metric_instance_factory_interface.h"
#include "public/cpio/utils/metric_instance/src/aggregate_metric.h"
#include "public/cpio/utils/metric_instance/src/metric_utils.h"
#include "public/cpio/utils/proto_utils.h"

using google::cmrt::sdk::kms_service::v1::DecryptRequest;
using google::cmrt::sdk::kms_service::v1::DecryptResponse;
using google::cmrt::sdk::v1::GcpWrappedKey;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResult;
using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::StatusCode;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::core::common::TimeProvider;
using google::scp::core::errors::SC_CPIO_ENTITY_NOT_FOUND;
using google::scp::core::errors::SC_CPIO_INTERNAL_ERROR;
using google::scp::core::errors::SC_CPIO_INVALID_ARGUMENT;
using google::scp::core::errors::SC_CPIO_INVALID_CREDENTIALS;
using google::scp::core::errors::SC_CPIO_KEY_FETCHER_FETCHING_TIMEOUT;
using google::scp::core::errors::SC_CPIO_KEY_NOT_FOUND;
using google::scp::core::errors::SC_CPIO_REQUEST_LIMIT_REACHED;
using google::scp::core::errors::SC_PROTO_PARSING_FAILURE;
using google::scp::cpio::DualWritingMetricClientInterface;
using google::scp::cpio::KeyCacheStatus;
using google::scp::cpio::KeyFetchingType;
using google::scp::cpio::KeyType;
using google::scp::cpio::KmsClientInterface;
using google::scp::cpio::ProtoUtils;
using google::scp::cpio::PushKeyCacheStatusMetric;
using google::scp::cpio::PushKeyFetchingLatencyMetric;
using google::scp::cpio::PushKeyFetchingRequestMetric;
using google::scp::cpio::PushWrappedKeyFetchingErrorMetric;
using google::scp::cpio::WrappedKeyHandlerOptions;
using std::nullopt;
using std::optional;
using std::pair;
using std::shared_ptr;
using std::string;
using std::chrono::duration_cast;
using std::chrono::milliseconds;
using std::chrono::nanoseconds;
using std::chrono::system_clock;
using std::this_thread::sleep_for;

namespace google::scp::cpio {

namespace {

constexpr char kWrappedKeyHandlerWithCacheComponentName[] =
    "WrappedKeyHandlerWithCache";
constexpr milliseconds kThreadSleepIntervalForKeyReady = milliseconds(5);
constexpr char tinkKekGcpPrefix[] = "gcp-kms://";
constexpr milliseconds kLogPeriod = milliseconds(1000);

// Non-retryable key decryption errors
const absl::flat_hash_set<StatusCode>& GetNonRetryableDecryptionErrors() {
  static const absl::NoDestructor<absl::flat_hash_set<StatusCode>>
      kNonRetryableDecryptionErrors(
          {SC_CPIO_KEY_NOT_FOUND, SC_CPIO_ENTITY_NOT_FOUND,
           SC_CPIO_INVALID_ARGUMENT, SC_PROTO_PARSING_FAILURE,
           SC_CPIO_INTERNAL_ERROR, SC_CPIO_INVALID_CREDENTIALS});
  return *kNonRetryableDecryptionErrors;
}

}  // namespace

WrappedKeyHandlerWithCache::WrappedKeyHandlerWithCache(
    shared_ptr<AsyncExecutorInterface>& async_executor,
    KmsClientInterface& kms_client,
    WrappedKeyHandlerOptions wrapped_key_handler_options,
    DualWritingMetricClientInterface& metric_client)
    : key_cache_(
          wrapped_key_handler_options.key_cache_lifetime.count(),
          true /* extend_entry_lifetime_on_access */,
          true /* block_entry_while_eviction */,
          [](auto&, auto&, auto should_delete_entry) {
            should_delete_entry(true);
          } /*function on before garbage collection*/,
          async_executor),
      key_failure_cache_(
          wrapped_key_handler_options.key_failure_cache_lifetime.count(),
          false /* extend_entry_lifetime_on_access */,
          true /* block_entry_while_eviction */,
          [](auto&, auto&, auto should_delete_entry) {
            should_delete_entry(true);
          } /*function on before garbage collection*/,
          async_executor),
      kms_client_(kms_client),
      wrapped_key_handler_options_(wrapped_key_handler_options),
      metric_client_(metric_client) {}

ExecutionResult WrappedKeyHandlerWithCache::Init() noexcept {
  RETURN_AND_LOG_IF_FAILURE(key_cache_.Init(),
                            kWrappedKeyHandlerWithCacheComponentName, kZeroUuid,
                            "Failed to init key_cache_.");
  RETURN_AND_LOG_IF_FAILURE(key_failure_cache_.Init(),
                            kWrappedKeyHandlerWithCacheComponentName, kZeroUuid,
                            "Failed to init key_failure_cache_.");
  return SuccessExecutionResult();
}

ExecutionResult WrappedKeyHandlerWithCache::Run() noexcept {
  RETURN_AND_LOG_IF_FAILURE(key_cache_.Run(),
                            kWrappedKeyHandlerWithCacheComponentName, kZeroUuid,
                            "Failed to run key_cache_.");
  RETURN_AND_LOG_IF_FAILURE(key_failure_cache_.Run(),
                            kWrappedKeyHandlerWithCacheComponentName, kZeroUuid,
                            "Failed to run key_failure_cache_.");
  return SuccessExecutionResult();
}

ExecutionResult WrappedKeyHandlerWithCache::Stop() noexcept {
  RETURN_AND_LOG_IF_FAILURE(key_cache_.Stop(),
                            kWrappedKeyHandlerWithCacheComponentName, kZeroUuid,
                            "Failed to stop key_cache_.");
  RETURN_AND_LOG_IF_FAILURE(key_failure_cache_.Stop(),
                            kWrappedKeyHandlerWithCacheComponentName, kZeroUuid,
                            "Failed to stop key_failure_cache_.");
  return SuccessExecutionResult();
}

void WrappedKeyHandlerWithCache::CacheValidKey(
    const std::string& serialized_wrapped_key,
    const std::string& decrypted_dek) noexcept {
  std::string key;
  // Skip execution result checking and ignore the insert failure. Key cache
  // will re-decrypt the key if the insertion fails.
  key_cache_.Insert(std::make_pair(serialized_wrapped_key, decrypted_dek), key);
}

void WrappedKeyHandlerWithCache::CacheFailureResultForWrappedKey(
    std::string serialized_wrapped_key,
    ExecutionResult failure_result) noexcept {
  // Remove the old failure result if one exists. Ignore the erase result.
  key_failure_cache_.Erase(serialized_wrapped_key);
  // Ignore the insert result if another thread already inserted it.
  key_failure_cache_.Insert(
      std::make_pair(serialized_wrapped_key, failure_result), failure_result);
}

ExecutionResultOr<string>
WrappedKeyHandlerWithCache::DecryptValidateAndCacheDecryptedDek(
    const GcpWrappedKey& wrapped_key) noexcept {
  SCP_INFO(kWrappedKeyHandlerWithCacheComponentName, kZeroUuid,
           "Decrypt encrypted_dek from wrapped key (%s).",
           ProtoUtils::TextProtoString(wrapped_key).c_str());

  DecryptRequest decrypt_request;
  decrypt_request.set_ciphertext(wrapped_key.encrypted_dek());
  decrypt_request.set_key_resource_name(wrapped_key.kek_uri());
  decrypt_request.set_gcp_wip_provider(wrapped_key.wip_provider());

  // Log new wrapped key decryption metric using OpenTelemetry
  PushKeyFetchingRequestMetric(metric_client_, KeyType::kGcpWrappedKey,
                               KeyFetchingType::kOnDemand,
                               /*keyset_name=*/kDummyLabelValue);
  auto decryption_start_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();

  // Call KMS to decrypt DEK
  auto response_or = kms_client_.DecryptSync(decrypt_request);

  auto decryption_end_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();
  auto latency_in_millis =
      duration_cast<milliseconds>(
          nanoseconds(decryption_end_time_in_ns - decryption_start_time_in_ns))
          .count();
  // Log new wrapped key decryption latency metric using OpenTelemetry
  PushKeyFetchingLatencyMetric(
      metric_client_, KeyType::kGcpWrappedKey, KeyFetchingType::kOnDemand,
      /*keyset_name=*/kDummyLabelValue, latency_in_millis);

  return ValidateAndCacheDecryptedDek(wrapped_key, response_or);
}

ExecutionResultOr<string>
WrappedKeyHandlerWithCache::ValidateAndCacheDecryptedDek(
    const GcpWrappedKey& wrapped_key, const ExecutionResultOr<DecryptResponse>&
                                          decrypt_dek_response_or) noexcept {
  auto serialized_wrapped_key = wrapped_key.SerializeAsString();
  if (!decrypt_dek_response_or.Successful()) {
    SCP_ERROR_EVERY_PERIOD(kLogPeriod, kWrappedKeyHandlerWithCacheComponentName,
                           kZeroUuid, decrypt_dek_response_or.result(),
                           "Failed to get decrypted DEK for wrapped key (%s).",
                           ProtoUtils::TextProtoString(wrapped_key).c_str());
    CacheFailureResultForWrappedKey(serialized_wrapped_key,
                                    decrypt_dek_response_or.result());
    // Log new wrapped key error metric using OpenTelemetry
    PushWrappedKeyFetchingErrorMetric(
        metric_client_,
        MapToWrappedKeyFetchingErrorString(decrypt_dek_response_or.result()));

    return decrypt_dek_response_or.result();
  }

  if (decrypt_dek_response_or->plaintext().empty()) {
    auto failure_result = FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND);
    SCP_ERROR_EVERY_PERIOD(kLogPeriod, kWrappedKeyHandlerWithCacheComponentName,
                           kZeroUuid,
                           FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND),
                           "No decrypted DEK found for wrapped key (%s).",
                           ProtoUtils::TextProtoString(wrapped_key).c_str());
    CacheFailureResultForWrappedKey(serialized_wrapped_key, failure_result);
    // Log new wrapped key error metric using OpenTelemetry
    PushWrappedKeyFetchingErrorMetric(
        metric_client_, MapToWrappedKeyFetchingErrorString(failure_result));
    return failure_result;
  }

  auto& decrypted_dek = decrypt_dek_response_or->plaintext();

  CacheValidKey(serialized_wrapped_key, decrypted_dek);
  return decrypted_dek;
}

ExecutionResultOr<string> WrappedKeyHandlerWithCache::GetKey(
    const GcpWrappedKey& input_wrapped_key) noexcept {
  GcpWrappedKey wrapped_key = input_wrapped_key;
  // Sanitize kek_uri by removing tink prefix if present
  wrapped_key.set_kek_uri(
      absl::StripPrefix(input_wrapped_key.kek_uri(), tinkKekGcpPrefix));

  auto serialized_wrapped_key = wrapped_key.SerializeAsString();
  auto decrypted_dek = GetDecryptedDekFromValidKeyCache(serialized_wrapped_key);

  if (decrypted_dek.has_value()) {
    // Log new key cache stats metric using OpenTelemetry.
    PushKeyCacheStatusMetric(metric_client_, KeyType::kGcpWrappedKey,
                             /*keyset_name=*/kDummyLabelValue,
                             KeyCacheStatus::kValidKeyCacheHit);
    return decrypted_dek.value();
  }
  auto failure_result = GetDecryptionFailureFromCache(wrapped_key);
  // Only return directly when the failure is not retryable.
  if (failure_result.has_value() &&
      GetNonRetryableDecryptionErrors().contains(failure_result->status_code)) {
    // Log new key cache stats metric using OpenTelemetry
    PushKeyCacheStatusMetric(metric_client_, KeyType::kGcpWrappedKey,
                             /*keyset_name=*/kDummyLabelValue,
                             KeyCacheStatus::kInvalidKeyCacheHit);
    return failure_result.value();
  }
  // Log new key cache stats metric using OpenTelemetry
  PushKeyCacheStatusMetric(metric_client_, KeyType::kGcpWrappedKey,
                           /*keyset_name=*/kDummyLabelValue,
                           KeyCacheStatus::kValidKeyCacheMiss);

  if (!wrapped_key_handler_options_.enable_decryption_lock) {
    return DecryptValidateAndCacheDecryptedDek(wrapped_key);
  }

  if (!DecryptionInProgress(serialized_wrapped_key)) {
    // This is to double confirm there is no thread already done decrypting
    // the dek and caching, but the in progress status has not updated yet.
    decrypted_dek = GetDecryptedDekFromValidKeyCache(serialized_wrapped_key);
    if (decrypted_dek.has_value()) {
      return decrypted_dek.value();
    }
    failure_result = GetDecryptionFailureFromCache(wrapped_key);
    // Only return directly when the failure is not retryable.
    if (failure_result.has_value() &&
        GetNonRetryableDecryptionErrors().contains(
            failure_result->status_code)) {
      return failure_result.value();
    }

    // Failing to mark IN_PROGRESS status means some other thread is already
    // decrypting the DEK. So it will fall to the WaitForKeyReady() process.
    if (MarkDecryptionInProgress(serialized_wrapped_key)) {
      auto decrypted_dek_or = DecryptValidateAndCacheDecryptedDek(wrapped_key);
      MarkDecryptionFinished(serialized_wrapped_key);
      return decrypted_dek_or;
    }
  }

  WaitForKeyReady(serialized_wrapped_key);

  decrypted_dek = GetDecryptedDekFromValidKeyCache(serialized_wrapped_key);
  if (decrypted_dek.has_value()) {
    return decrypted_dek.value();
  }
  failure_result = GetDecryptionFailureFromCache(wrapped_key);
  // Another thread just finished decrypting the DEK and it is useless to retry
  // immediately, so we return directly.
  if (failure_result.has_value()) {
    return failure_result.value();
  }

  // It means the waiting timeout when this happens.
  auto timeout_failure =
      FailureExecutionResult(SC_CPIO_KEY_FETCHER_FETCHING_TIMEOUT);
  SCP_ERROR_EVERY_PERIOD(kLogPeriod, kWrappedKeyHandlerWithCacheComponentName,
                         kZeroUuid, timeout_failure,
                         "Failed to get decrypted DEK for wrapped key (%s).",
                         ProtoUtils::TextProtoString(wrapped_key).c_str());
  return timeout_failure;
}

optional<string> WrappedKeyHandlerWithCache::GetDecryptedDekFromValidKeyCache(
    const string& serialized_wrapped_key) noexcept {
  if (!wrapped_key_handler_options_.enable_cache) {
    return nullopt;
  }
  string decrypted_dek;
  auto execution_result =
      key_cache_.Find(serialized_wrapped_key, decrypted_dek);
  if (execution_result.Successful()) {
    return decrypted_dek;
  }
  return nullopt;
}

optional<ExecutionResult>
WrappedKeyHandlerWithCache::GetDecryptionFailureFromCache(
    const GcpWrappedKey& wrapped_key) noexcept {
  if (!wrapped_key_handler_options_.enable_cache) {
    return nullopt;
  }
  ExecutionResult failure_result;
  auto is_key_found =
      key_failure_cache_.Find(wrapped_key.SerializeAsString(), failure_result)
          .Successful();
  if (is_key_found) {
    SCP_ERROR_EVERY_PERIOD(
        kLogPeriod, kWrappedKeyHandlerWithCacheComponentName, kZeroUuid,
        failure_result,
        "Successfully retrieved failure for key in invalid cache (%s).",
        ProtoUtils::TextProtoString(wrapped_key).c_str());
    return failure_result;
  }
  return nullopt;
}

void WrappedKeyHandlerWithCache::MarkDecryptionFinished(
    const std::string& serialized_wrapped_key) noexcept {
  std::unique_lock lock(in_progress_key_cache_mutex_);
  in_progress_key_cache_.erase(serialized_wrapped_key);
  lock.unlock();
}

bool WrappedKeyHandlerWithCache::MarkDecryptionInProgress(
    const std::string& serialized_wrapped_key) noexcept {
  std::unique_lock lock(in_progress_key_cache_mutex_);
  if (auto it = in_progress_key_cache_.find(serialized_wrapped_key);
      it != in_progress_key_cache_.end()) {
    lock.unlock();
    return false;
  } else {
    in_progress_key_cache_.insert(serialized_wrapped_key);
    lock.unlock();
    return true;
  }
}

void WrappedKeyHandlerWithCache::WaitForKeyReady(
    const std::string& serialized_wrapped_key) noexcept {
  auto start_time = system_clock::now();
  auto end_time = start_time;
  while (DecryptionInProgress(serialized_wrapped_key) &&
         (end_time - start_time) <
             wrapped_key_handler_options_.key_decryption_waiting_timeout) {
    sleep_for(kThreadSleepIntervalForKeyReady);
    end_time = system_clock::now();
  }
}

bool WrappedKeyHandlerWithCache::DecryptionInProgress(
    const std::string& serialized_wrapped_key) noexcept {
  bool in_progress = false;
  std::shared_lock lock(in_progress_key_cache_mutex_);

  auto it = in_progress_key_cache_.find(serialized_wrapped_key);
  in_progress = it != in_progress_key_cache_.end();
  lock.unlock();
  return in_progress;
}

std::string WrappedKeyHandlerWithCache::MapToWrappedKeyFetchingErrorString(
    google::scp::core::ExecutionResult error_result) noexcept {
  if (error_result.status_code == SC_CPIO_REQUEST_LIMIT_REACHED) {
    return "ERROR_CODE_CUSTOMER_QUOTA_EXCEEDED";
  }
  if (error_result.status_code == SC_CPIO_INVALID_CREDENTIALS) {
    return "ERROR_CODE_CUSTOMER_KEY_PERMISSION_DENIED";
  }
  if (error_result.status_code == SC_CPIO_KEY_NOT_FOUND ||
      error_result.status_code == SC_CPIO_INTERNAL_ERROR ||
      error_result.status_code == SC_CPIO_INVALID_ARGUMENT ||
      error_result.status_code == SC_CPIO_ENTITY_NOT_FOUND) {
    return "ERROR_CODE_INVALID_KEY_ID";
  }
  return "ERROR_CODE_KEY_FETCHING_ERROR";
}

}  // namespace google::scp::cpio
