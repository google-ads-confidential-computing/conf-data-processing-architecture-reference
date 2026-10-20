/*
 * Copyright 2024 Google LLC
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

#include "ondemand_key_fetcher_with_cache.h"

#include <memory>
#include <optional>
#include <string>
#include <utility>

#include "absl/container/flat_hash_set.h"
#include "core/common/global_logger/src/global_logger.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/core/interface/execution_result_or_macros.h"
#include "public/cpio/interface/error_codes.h"
#include "public/cpio/utils/key_fetching/proto/key_coordinator_configuration.pb.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"

#include "error_codes.h"
#include "key_fetching_utils.h"

using google::cmrt::sdk::v1::KeyCoordinatorConfiguration;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::StatusCode;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::core::errors::SC_CPIO_ENTITY_NOT_FOUND;
using google::scp::core::errors::SC_CPIO_INVALID_ARGUMENT;
using google::scp::core::errors::SC_CPIO_KEY_COUNT_MISMATCH;
using google::scp::core::errors::SC_CPIO_KEY_FETCHER_FETCHING_TIMEOUT;
using google::scp::core::errors::SC_CPIO_KEY_NOT_FOUND;
using google::scp::cpio::KeyType;
using std::nullopt;
using std::optional;
using std::pair;
using std::shared_ptr;
using std::string;
using std::vector;
using std::chrono::milliseconds;
using std::placeholders::_1;
using std::placeholders::_2;
using std::placeholders::_3;

namespace google::scp::cpio {
namespace {
constexpr char kOndemandKeyFetcherWithCacheComponentName[] =
    "EncryptionKeyFetcherWithCache";
constexpr milliseconds kLogPeriod = milliseconds(1000);

// Non-retryable key fetching errors
const absl::flat_hash_set<StatusCode> kUnretryableKeyFetchingErrors = {
    SC_CPIO_KEY_NOT_FOUND, SC_CPIO_KEY_COUNT_MISMATCH, SC_CPIO_ENTITY_NOT_FOUND,
    SC_CPIO_INVALID_ARGUMENT};

uint64_t GetKeyCacheLifetimeSeconds(
    const KeyFetcherOptions& key_fetcher_options) {
  return key_fetcher_options.key_cache_lifetime.count();
}

uint64_t GetFetchingFailureCacheLifetimeSeconds(
    const KeyFetcherOptions& key_fetcher_options) {
  return key_fetcher_options.fetching_failure_cache_lifetime.count();
}

BaseKeyFetcherOptions GetBaseKeyFetcherOptions(
    const KeyFetcherOptions& key_fetcher_options) {
  BaseKeyFetcherOptions base_options;
  base_options.enable_prefetch = key_fetcher_options.prefetch_keys;
  base_options.enable_retry_in_prefetch_and_autorefresh =
      key_fetcher_options.prefetch_retry;
  base_options.max_prefetch_wait_time = std::chrono::milliseconds(
      key_fetcher_options.max_prefetch_wait_time_millis);
  base_options.prefetch_keys_max_age =
      key_fetcher_options.prefetch_keys_max_age;
  base_options.on_demand_fetching_waiting_timeout =
      key_fetcher_options.on_demand_fetching_waiting_timeout;
  base_options.prefetch_config_map =
      key_fetcher_options.encryption_key_prefetch_config_map;
  base_options.enable_auto_refresh_keys =
      key_fetcher_options.encryption_key_enable_auto_refresh;
  base_options.auto_refresh_time_duration =
      key_fetcher_options.encryption_key_auto_refresh_time_duration;
  base_options.enable_incremental_prefetch =
      key_fetcher_options.encryption_key_enable_incremental_prefetch;
  base_options.incremental_prefetch_time_duration =
      key_fetcher_options.encryption_key_incremental_prefetch_time_duration;
  return base_options;
}

}  // namespace

OndemandKeyFetcherWithCache::OndemandKeyFetcherWithCache(
    shared_ptr<AsyncExecutorInterface>& async_executor,
    PrivateKeyClientInterface& key_client,
    DualWritingMetricClientInterface& metric_client,
    const KeyCoordinatorConfiguration& key_service_options,
    KeyFetcherOptions key_fetcher_options, const std::string& metric_namespace)
    : CoordinatorKeyFetcherWithCacheBase<std::string>(
          key_client, metric_client, key_service_options,
          GetBaseKeyFetcherOptions(key_fetcher_options),
          kOndemandKeyFetcherWithCacheComponentName, KeyType::kEncryptionKey,
          metric_namespace),
      key_cache_(
          GetKeyCacheLifetimeSeconds(key_fetcher_options),
          true /* extend_entry_lifetime_on_access */,
          true /* block_entry_while_eviction */,
          [](auto&, auto&, auto should_delete_entry) {
            should_delete_entry(true);
          } /*function on before garbage collection*/,
          async_executor),
      fetching_failure_cache_(
          GetFetchingFailureCacheLifetimeSeconds(key_fetcher_options),
          false /* extend_entry_lifetime_on_access */,
          true /* block_entry_while_eviction */,
          [](auto&, auto&, auto should_delete_entry) {
            should_delete_entry(true);
          } /*function on before garbage collection*/,
          async_executor, key_fetcher_options.use_read_lock_for_cache_read) {}

ExecutionResult OndemandKeyFetcherWithCache::Init() noexcept {
  RETURN_AND_LOG_IF_FAILURE(key_cache_.Init(),
                            kOndemandKeyFetcherWithCacheComponentName,
                            kZeroUuid, "Failed to init key_cache_.");
  RETURN_AND_LOG_IF_FAILURE(
      fetching_failure_cache_.Init(), kOndemandKeyFetcherWithCacheComponentName,
      kZeroUuid, "Failed to init fetching_failure_cache_.");
  return CoordinatorKeyFetcherWithCacheBase::Init();
}

ExecutionResult OndemandKeyFetcherWithCache::Run() noexcept {
  RETURN_AND_LOG_IF_FAILURE(key_cache_.Run(),
                            kOndemandKeyFetcherWithCacheComponentName,
                            kZeroUuid, "Failed to run key_cache_.");
  RETURN_AND_LOG_IF_FAILURE(
      fetching_failure_cache_.Run(), kOndemandKeyFetcherWithCacheComponentName,
      kZeroUuid, "Failed to run fetching_failure_cache_.");
  return CoordinatorKeyFetcherWithCacheBase::Run();
}

ExecutionResult OndemandKeyFetcherWithCache::Stop() noexcept {
  RETURN_IF_FAILURE(CoordinatorKeyFetcherWithCacheBase::Stop());
  RETURN_AND_LOG_IF_FAILURE(key_cache_.Stop(),
                            kOndemandKeyFetcherWithCacheComponentName,
                            kZeroUuid, "Failed to stop key_cache_.");
  RETURN_AND_LOG_IF_FAILURE(
      fetching_failure_cache_.Stop(), kOndemandKeyFetcherWithCacheComponentName,
      kZeroUuid, "Failed to stop fetching_failure_cache_.");
  return SuccessExecutionResult();
}

core::ExecutionResultOr<Key> OndemandKeyFetcherWithCache::GetKey(
    const std::string& key_id) noexcept {
  return GetKeyInternal(key_id, KeyFetchingType::kOnDemand);
}

core::ExecutionResultOr<bool> OndemandKeyFetcherWithCache::ValidateKey(
    const std::string& key_id) noexcept {
  ASSIGN_OR_RETURN(auto key,
                   GetKeyInternal(key_id, KeyFetchingType::kValidateKey));
  if (!key.private_key.empty()) {
    return true;
  }
  return false;
}

void OndemandKeyFetcherWithCache::CacheValidKey(
    const vector<Key>& valid_keys) noexcept {
  for (auto valid_key : valid_keys) {
    Key key;
    pair<string, Key> key_pair;
    key_pair.first = valid_key.key_id;
    key_pair.second = std::move(valid_key);
    // Skip execution result checking and ignore the insert failure. Key cache
    // will refetch the key if the insertion failed.
    key_cache_.Insert(key_pair, key);
  }
}

void OndemandKeyFetcherWithCache::CacheFailureResult(
    std::string key_id, ExecutionResult failure_result) noexcept {
  pair<std::string, ExecutionResult> failed_key_pair;
  failed_key_pair.first = key_id;
  failed_key_pair.second = std::move(failure_result);
  // Remove the old failure result if it exits. Ignore the erase result.
  fetching_failure_cache_.Erase(key_id);
  // Ignore the insert result if another thread already insert it.
  fetching_failure_cache_.Insert(failed_key_pair, failure_result);
}

optional<Key> OndemandKeyFetcherWithCache::GetKeyFromValidKeyCache(
    const std::string& key_id) noexcept {
  Key key;
  auto execution_result = key_cache_.Find(key_id, key);
  if (execution_result.Successful()) {
    return key;
  }
  return nullopt;
}

core::ExecutionResultOr<Key> OndemandKeyFetcherWithCache::GetKeyInternal(
    const std::string& key_id, absl::string_view key_fetching_type) noexcept {
  auto key = GetKeyFromValidKeyCache(key_id);
  if (key.has_value()) {
    // Use allowed_keysets_name_ which might be a list to represent the keyset
    // because we don't have exact keyset_name available in the cache.
    PushKeyCacheStatusMetric(metric_client_, key_type_, allowed_keysets_name_,
                             KeyCacheStatus::kValidKeyCacheHit);
    return key.value();
  }
  auto failure_result = GetFetchingFailureFromCache(key_id);
  // Only return directly when the failure is not retryable.
  if (failure_result.has_value() &&
      kUnretryableKeyFetchingErrors.contains(failure_result->status_code)) {
    PushKeyCacheStatusMetric(metric_client_, key_type_, allowed_keysets_name_,
                             KeyCacheStatus::kInvalidKeyCacheHit);
    return failure_result.value();
  }

  PushKeyCacheStatusMetric(metric_client_, key_type_, allowed_keysets_name_,
                           KeyCacheStatus::kValidKeyCacheMiss);

  if (!FetchingInProgress(key_id)) {
    // This is to double confirm there is no thread finished key fetching
    // and key caching but the in progress status is not updated yet.
    key = GetKeyFromValidKeyCache(key_id);
    if (key.has_value()) {
      return key.value();
    }
    failure_result = GetFetchingFailureFromCache(key_id);
    // Only return directly when the failure is not retryable.
    if (failure_result.has_value() &&
        kUnretryableKeyFetchingErrors.contains(failure_result->status_code)) {
      return failure_result.value();
    }

    // Failing to mark IN_PROGRESS status means some other thread is already
    // fetching the key. So it will fall to the WaitForKeyReady() process.
    if (MarkFetchingInProgress(key_id)) {
      auto fetched_key_or = FetchKeys(key_id, key_fetching_type);
      // Cache the result before marking the fetching finished so that threads
      // waiting in WaitForKeyReady() can see it.
      if (fetched_key_or.Successful()) {
        CacheValidKey({*fetched_key_or});
      } else {
        CacheFailureResult(key_id, fetched_key_or.result());
      }
      MarkFetchingFinished(key_id);
      return fetched_key_or;
    }
  }

  WaitForKeyReady(key_id);

  key = GetKeyFromValidKeyCache(key_id);
  if (key.has_value()) {
    return key.value();
  }
  failure_result = GetFetchingFailureFromCache(key_id);
  // Another thread just finished key fetching and it is useless to retry
  // immediately, so we return directly.
  if (failure_result.has_value()) {
    return failure_result.value();
  }

  // It means the waiting timeout When this happens.
  auto timeout_failure =
      FailureExecutionResult(SC_CPIO_KEY_FETCHER_FETCHING_TIMEOUT);
  SCP_ERROR(kOndemandKeyFetcherWithCacheComponentName, kZeroUuid,
            timeout_failure, "The key fetching failed for key %s.",
            key_id.c_str());
  return timeout_failure;
}

optional<ExecutionResult>
OndemandKeyFetcherWithCache::GetFetchingFailureFromCache(
    const std::string& key_id) noexcept {
  ExecutionResult failure_result;
  auto is_key_found =
      fetching_failure_cache_.Find(key_id, failure_result).Successful();
  if (is_key_found) {
    SCP_ERROR_EVERY_PERIOD(
        kLogPeriod, kOndemandKeyFetcherWithCacheComponentName, kZeroUuid,
        failure_result, "The key fetching failure for %s is cached.",
        key_id.c_str());
    return failure_result;
  }
  return nullopt;
}

void OndemandKeyFetcherWithCache::MarkFetchingFinished(
    const string& key_id) noexcept {
  std::unique_lock lock(in_progress_key_cache_mutex_);
  in_progress_key_cache_.erase(key_id);
  lock.unlock();
}

bool OndemandKeyFetcherWithCache::MarkFetchingInProgress(
    const string& key_id) noexcept {
  std::unique_lock lock(in_progress_key_cache_mutex_);
  if (auto it = in_progress_key_cache_.find(key_id);
      it != in_progress_key_cache_.end()) {
    lock.unlock();
    return false;
  } else {
    in_progress_key_cache_.insert(key_id);
    lock.unlock();
    return true;
  }
}

bool OndemandKeyFetcherWithCache::FetchingInProgress(
    const string& key_id) noexcept {
  bool in_progress = false;
  std::shared_lock lock(in_progress_key_cache_mutex_);
  auto it = in_progress_key_cache_.find(key_id);
  in_progress = it != in_progress_key_cache_.end();
  lock.unlock();
  return in_progress;
}

core::ExecutionResultOr<Key> OndemandKeyFetcherWithCache::FetchKeys(
    const std::string& key_id, absl::string_view key_fetching_type) noexcept {
  return FetchKeysById(key_id, key_fetching_type);
}

bool OndemandKeyFetcherWithCache::ShouldPerformAutoRefresh(
    absl::string_view keyset_name) noexcept {
  return true;
}
}  // namespace google::scp::cpio
