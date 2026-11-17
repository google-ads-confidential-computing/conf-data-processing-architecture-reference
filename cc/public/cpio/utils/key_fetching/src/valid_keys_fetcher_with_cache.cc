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

#include "valid_keys_fetcher_with_cache.h"

#include <mutex>
#include <shared_mutex>
#include <string>
#include <utility>
#include <vector>

#include <google/protobuf/util/time_util.h>

#include "core/common/time_provider/src/time_provider.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/cpio/utils/key_fetching/src/error_codes.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_utils.h"

using google::cmrt::sdk::v1::KeyCoordinatorConfiguration;
using google::protobuf::util::TimeUtil;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::Timestamp;
using google::scp::core::common::kZeroUuid;
using google::scp::core::common::TimeProvider;
using google::scp::core::errors::SC_CPIO_KEY_FETCHER_CACHE_INSERT_FAILURE;
using google::scp::core::errors::
    SC_CPIO_KEY_FETCHER_INVALID_KEY_SELECTION_TIMESTAMP;
using google::scp::core::errors::SC_CPIO_KEY_NOT_FOUND;
using google::scp::cpio::DualWritingMetricClientInterface;
using google::scp::cpio::KeyFetchingType;
using google::scp::cpio::KeyType;
using google::scp::cpio::PrivateKeyClientInterface;
using std::make_pair;
using std::string;
using std::vector;
using std::chrono::duration_cast;
using std::chrono::hours;
using std::chrono::nanoseconds;
using std::chrono::seconds;

namespace google::scp::cpio {
namespace {
constexpr char kValidKeysFetcherWithCacheComponentName[] =
    "AutoRefreshKeyFetcherWithCache";
constexpr size_t kNetworkLatencyBufferInNanoseconds =
    duration_cast<nanoseconds>(seconds(60)).count();  // 60 seconds
// For valid keys on-demand fetching, fetch active keys 12 hours prior to the
// timestamp.
constexpr size_t kValidKeysFetchActiveKeysStartTimeSubDiffInHours = 12;

// For valid keys on-demand fetching, fetch active keys 7 days in advance.
constexpr size_t kValidKeysFetchActiveKeysEndTimeAddedDiffInHours = 7 * 24;

KeyCoordinatorConfiguration GetKeyServiceOptions(
    const std::vector<
        google::cmrt::sdk::private_key_service::v1::PrivateKeyEndpoint>&
        private_key_endpoints,
    const std::string& keyset_name) {
  KeyCoordinatorConfiguration key_service_options;
  key_service_options.mutable_private_key_endpoints()->Add(
      private_key_endpoints.begin(), private_key_endpoints.end());
  key_service_options.add_key_namespace(keyset_name);
  return key_service_options;
}

BaseKeyFetcherOptions GetBaseKeyFetcherOptions(
    const KeyFetcherOptions& key_fetcher_options) {
  BaseKeyFetcherOptions base_options;
  base_options.enable_prefetch = true;
  base_options.enable_retry_in_prefetch_and_autorefresh =
      key_fetcher_options.prefetch_retry;
  base_options.max_prefetch_wait_time = std::chrono::milliseconds(
      key_fetcher_options.max_prefetch_wait_time_millis);
  base_options.prefetch_keys_max_age =
      hours(kValidKeysFetchActiveKeysStartTimeSubDiffInHours);  // 12 hours
  base_options.on_demand_fetching_waiting_timeout =
      key_fetcher_options.on_demand_fetching_waiting_timeout;
  base_options.auto_refresh_time_duration =
      key_fetcher_options.auto_refresh_time_duration;
  base_options.enable_backfill = true;
  return base_options;
}
}  // namespace

ValidKeysFetcherWithCache::ValidKeysFetcherWithCache(
    PrivateKeyClientInterface& key_client,
    const std::vector<
        google::cmrt::sdk::private_key_service::v1::PrivateKeyEndpoint>&
        private_key_endpoints,
    const std::string& keyset_name,
    DualWritingMetricClientInterface& metric_client,
    KeyFetcherOptions key_fetcher_options, const string& metric_namespace)
    : CoordinatorKeyFetcherWithCacheBase<core::Timestamp>(
          key_client, metric_client,
          GetKeyServiceOptions(private_key_endpoints, keyset_name),
          GetBaseKeyFetcherOptions(key_fetcher_options),
          kValidKeysFetcherWithCacheComponentName, KeyType::kSidKey,
          metric_namespace),
      keyset_name_(keyset_name),
      key_fetcher_options_(std::move(key_fetcher_options)),
      key_fetching_status_(FetchingStatus::FINISHED) {}

void ValidKeysFetcherWithCache::CacheValidKey(
    const vector<Key>& keys_list) noexcept {
  // Skips the key cache refresh if the response is empty.
  if (keys_list.empty()) {
    return;
  }

  std::unique_lock lock(key_cache_mutex_);
  // Clear the cache and repopulate with the latest set of keys.
  key_cache_map_.clear();
  for (auto& key : keys_list) {
    if (!key_cache_map_.insert(make_pair(key.expiration_timestamp, key))
             .second) {
      // Key insert failed.
      SCP_ERROR(
          kValidKeysFetcherWithCacheComponentName, kZeroUuid,
          FailureExecutionResult(SC_CPIO_KEY_FETCHER_CACHE_INSERT_FAILURE),
          "Insert key %s into cache btree_map failed", key.key_id.c_str());
    }
  }
}

core::ExecutionResultOr<vector<Key>>
ValidKeysFetcherWithCache::GetKeysFromCache(
    core::Timestamp timestamp_ns) noexcept {
  std::shared_lock lock(key_cache_mutex_);
  vector<Key> keys_to_return;
  // Finds the first key with an expiration_time that is not less than
  // `timestamp_ns`
  auto key_it = key_cache_map_.lower_bound(timestamp_ns);
  while (key_it != key_cache_map_.end()) {
    if (IsKeyActiveAt(key_it->second, timestamp_ns)) {
      keys_to_return.push_back(key_it->second);
    }
    key_it++;
  }

  if (keys_to_return.empty()) {
    auto failure = FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND);
    SCP_ERROR(kValidKeysFetcherWithCacheComponentName, kZeroUuid, failure,
              "No active key return for keyset %s", keyset_name_.c_str());
    return failure;
  }

  return keys_to_return;
}

core::ExecutionResult ValidKeysFetcherWithCache::ValidateKeySelectionTimestamp(
    core::Timestamp key_selection_timestamp_ns) noexcept {
  auto now_ns = TimeProvider::GetWallTimestampInNanosecondsAsClockTicks();
  // The key selection timestamp is valid if it is within
  // [now - kNetworkLatencyBufferInNanoseconds, now +
  // kNetworkLatencyBufferInNanoseconds].
  if (key_selection_timestamp_ns <=
          now_ns + kNetworkLatencyBufferInNanoseconds &&
      key_selection_timestamp_ns >=
          now_ns - kNetworkLatencyBufferInNanoseconds) {
    return SuccessExecutionResult();
  }

  // Try to fetch the keyset metadata when backfill_days isn't being
  // cached.
  if (GetBackfillDays(keyset_name_) < 0) {
    FetchAndCacheKeysetMetadata(KeyFetchingType::kOnDemand, keyset_name_);
  }

  // Keyset with backfill days allows key selection timestamp to be
  // backfilled.
  int backfill_days = GetBackfillDays(keyset_name_);
  if (backfill_days > 0) {
    auto backfill_duration_in_ns =
        duration_cast<nanoseconds>(hours(24 * backfill_days)).count();
    // The key selection timestamp is valid if it is within
    // [now - backfill_duration_in_ns - kNetworkLatencyBufferInNanoseconds,
    // now + kNetworkLatencyBufferInNanoseconds].
    if (key_selection_timestamp_ns <=
            now_ns + kNetworkLatencyBufferInNanoseconds &&
        key_selection_timestamp_ns >= now_ns - backfill_duration_in_ns -
                                          kNetworkLatencyBufferInNanoseconds) {
      return SuccessExecutionResult();
    }
  }

  return FailureExecutionResult(
      SC_CPIO_KEY_FETCHER_INVALID_KEY_SELECTION_TIMESTAMP);
}

core::ExecutionResultOr<vector<Key>> ValidKeysFetcherWithCache::GetValidKeys(
    core::Timestamp key_selection_timestamp_ns) noexcept {
  RETURN_AND_LOG_IF_FAILURE(
      ValidateKeySelectionTimestamp(key_selection_timestamp_ns),
      kValidKeysFetcherWithCacheComponentName, kZeroUuid,
      "Key selection timestamp %lld validation failed for keyset %s with "
      "backfill %d",
      key_selection_timestamp_ns, keyset_name_.c_str(),
      GetBackfillDays(keyset_name_));

  auto keys_in_cache = GetKeysFromCache(key_selection_timestamp_ns);

  // Log new key cache stats metric using OpenTelemetry
  PushKeyCacheStatusMetric(metric_client_, KeyType::kSidKey, keyset_name_,
                           keys_in_cache.Successful()
                               ? KeyCacheStatus::kValidKeyCacheHit
                               : KeyCacheStatus::kValidKeyCacheMiss);

  if (keys_in_cache.Successful()) {
    return keys_in_cache;
  }

  if (!FetchingInProgress(key_selection_timestamp_ns)) {
    // This is to double confirm there is no thread finished key fetching and
    // key caching but the status is not marked FINISHED yet.
    keys_in_cache = GetKeysFromCache(key_selection_timestamp_ns);
    if (keys_in_cache.Successful()) {
      return keys_in_cache;
    }

    if (MarkFetchingInProgress()) {
      // Fetching result is ignored.
      OnDemandFetchAndCacheKeys();
      MarkFetchingFinished();
      return GetKeysFromCache(key_selection_timestamp_ns);
    }
  }

  WaitForKeyReady(key_selection_timestamp_ns);
  // If the key fetching thread failed to fetch keys, all waiting thread will
  // return empty keys. When another batch of key fetching requests come, key
  // will be re-fetched from remote. This is OK since:
  // 1. CPIO PrivateKeyClient will retry the key fetching (exponential backoff
  // for ~6min) and key decryption (exponential backoff for ~5min). Retry
  // immediately here will not help.
  // 2. Key fetching failure here will cause the whole request failure and
  // return retryable error and retried outside.
  return GetKeysFromCache(key_selection_timestamp_ns);
}

void ValidKeysFetcherWithCache::OnDemandFetchAndCacheKeys() noexcept {
  auto now = TimeProvider::GetWallTimestampInNanosecondsAsClockTicks();
  auto key_fetching_query_start_time = GetKeyFetchingQueryStartTime(
      keyset_name_, now,
      hours(kValidKeysFetchActiveKeysStartTimeSubDiffInHours));
  auto key_fetching_query_end_time = TimeUtil::NanosecondsToTimestamp(
      now + duration_cast<nanoseconds>(
                hours(kValidKeysFetchActiveKeysEndTimeAddedDiffInHours))
                .count());
  auto keys_or = FetchKeysInTimeRange(KeyFetchingType::kOnDemand, keyset_name_,
                                      key_fetching_query_start_time,
                                      key_fetching_query_end_time);
  if (keys_or.Successful()) {
    CacheValidKey(*keys_or);
  }
}

void ValidKeysFetcherWithCache::MarkFetchingFinished() noexcept {
  std::unique_lock lock(status_mutex_);
  key_fetching_status_ = FetchingStatus::FINISHED;
  lock.unlock();
}

bool ValidKeysFetcherWithCache::MarkFetchingInProgress() noexcept {
  std::unique_lock lock(status_mutex_);
  if (key_fetching_status_ == FetchingStatus::IN_PROGRESS) {
    lock.unlock();
    return false;
  } else {
    key_fetching_status_ = FetchingStatus::IN_PROGRESS;
    lock.unlock();
    return true;
  }
}

bool ValidKeysFetcherWithCache::FetchingInProgress(
    const core::Timestamp& timestamp_ns) noexcept {
  bool in_progress = false;
  std::shared_lock lock(status_mutex_);
  in_progress = key_fetching_status_ == FetchingStatus::IN_PROGRESS;
  lock.unlock();
  return in_progress;
}

bool ValidKeysFetcherWithCache::ShouldPerformAutoRefresh(
    const std::string& keyset_name) noexcept {
  int expected_active_key_count = GetExpectedActiveKeyCount(keyset_name_);
  // Skips the key fetching if the cache is valid in schedule checking time.
  if (expected_active_key_count > 0) {
    auto check_timestamp =
        TimeProvider::GetWallTimestampInNanosecondsAsClockTicks() +
        duration_cast<nanoseconds>(
            hours(key_fetcher_options_.auto_refresh_key_cache_valid_in_days *
                  24))
            .count();
    auto keys_in_cache_or = GetKeysFromCache(check_timestamp);
    if (keys_in_cache_or.Successful() &&
        keys_in_cache_or->size() == expected_active_key_count) {
      return false;
    }
    SCP_ERROR(kValidKeysFetcherWithCacheComponentName, kZeroUuid,
              keys_in_cache_or.result(),
              "Auto-refresh checking: the number of active keys in cache "
              "doesn't match the keyset "
              "metadata for keyset %s. Cached active key count: %d, "
              "Keyset metadata active key count: %d",
              keyset_name_.c_str(),
              keys_in_cache_or.Successful() ? keys_in_cache_or->size() : -1,
              expected_active_key_count);
  }
  return true;
}
}  // namespace google::scp::cpio