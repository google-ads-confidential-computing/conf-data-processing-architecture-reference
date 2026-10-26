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

#include "coordinator_key_fetcher_with_cache_base.h"

#include <memory>
#include <optional>
#include <random>
#include <shared_mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "absl/strings/str_join.h"
#include "core/common/global_logger/src/global_logger.h"
#include "google/protobuf/util/time_util.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/cpio/interface/error_codes.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"

#include "error_codes.h"
#include "key_fetching_utils.h"

using google::cmrt::sdk::private_key_service::v1::GetKeysetMetadataRequest;
using google::cmrt::sdk::private_key_service::v1::
    ListActiveEncryptionKeysRequest;
using google::cmrt::sdk::private_key_service::v1::
    ListActiveEncryptionKeysResponse;
using google::cmrt::sdk::private_key_service::v1::ListPrivateKeysRequest;
using google::cmrt::sdk::private_key_service::v1::ListPrivateKeysResponse;
using google::cmrt::sdk::v1::KeyCoordinatorConfiguration;
using google::protobuf::util::TimeUtil;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::StatusCode;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::core::common::TimeProvider;
using google::scp::core::errors::SC_CPIO_ENTITY_NOT_FOUND;
using google::scp::core::errors::SC_CPIO_INVALID_ARGUMENT;
using google::scp::core::errors::SC_CPIO_KEY_COUNT_MISMATCH;
using google::scp::core::errors::SC_CPIO_KEY_NOT_FOUND;
using google::scp::cpio::KeyFetchingType;
using std::mt19937;
using std::pair;
using std::random_device;
using std::shared_ptr;
using std::string;
using std::uniform_int_distribution;
using std::vector;
using std::chrono::duration_cast;
using std::chrono::hours;
using std::chrono::milliseconds;
using std::chrono::nanoseconds;
using std::chrono::seconds;
using std::chrono::system_clock;
using std::this_thread::sleep_for;

namespace google::scp::cpio {
namespace {
constexpr milliseconds kThreadSleepIntervalForKeyReady = milliseconds(5);

// Fetching keys during auto-refresh that were active 12 hours earlier and
// caching them helps prevent latency issues between the client and server.
constexpr size_t kAutoRefreshFetchActiveKeysStartTimeSubDiffInHours = 12;

// To prevent a gap in key rotation, fetch active keys seven days in advance
// during auto-refresh.
constexpr size_t kAutoRefreshFetchActiveKeysEndTimeAddedDiffInHours = 7 * 24;

// Check active key count 1 day in the future to ensure key service has enough
// active keys.
constexpr size_t kCheckActiveKeysCountAddedDiffInHours = 24;

ListPrivateKeysRequest GetListPrivateKeysRequestBase(
    const KeyCoordinatorConfiguration& key_service_options) {
  ListPrivateKeysRequest request;
  for (const auto& coordinator_info :
       key_service_options.private_key_endpoints()) {
    auto* coordinator = request.add_key_endpoints();
    coordinator->set_endpoint(coordinator_info.endpoint());
    coordinator->set_gcp_wip_provider(coordinator_info.gcp_wip_provider());
    coordinator->set_gcp_cloud_function_url(
        coordinator_info.gcp_cloud_function_url());
  }
  return request;
}

ListActiveEncryptionKeysRequest GetListActiveKeysRequestBase(
    const KeyCoordinatorConfiguration& key_service_options) {
  ListActiveEncryptionKeysRequest request;
  for (const auto& coordinator_info :
       key_service_options.private_key_endpoints()) {
    auto* coordinator = request.add_key_endpoints();
    coordinator->set_endpoint(coordinator_info.endpoint());
    coordinator->set_gcp_wip_provider(coordinator_info.gcp_wip_provider());
    coordinator->set_gcp_cloud_function_url(
        coordinator_info.gcp_cloud_function_url());
  }
  return request;
}

GetKeysetMetadataRequest GetKeysetMetadataRequestBase(
    const KeyCoordinatorConfiguration& key_service_options) {
  GetKeysetMetadataRequest get_keyset_metadata_request;
  for (const auto& coordinator_info :
       key_service_options.private_key_endpoints()) {
    // Any coordinator info can be used to fetch the keyset metadata.
    get_keyset_metadata_request.set_private_key_endpoint(
        coordinator_info.endpoint());
    return get_keyset_metadata_request;
  }
  return get_keyset_metadata_request;
}
}  // namespace

template <typename LookupKeyT>
CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::
    CoordinatorKeyFetcherWithCacheBase(
        PrivateKeyClientInterface& key_client,
        DualWritingMetricClientInterface& metric_client,
        const KeyCoordinatorConfiguration& key_service_options,
        BaseKeyFetcherOptions key_fetcher_options,
        absl::string_view component_name, absl::string_view key_type,
        const std::string& metric_namespace)
    : metric_client_(metric_client),
      key_type_(key_type),
      key_client_(key_client),
      list_private_keys_request_base_(
          GetListPrivateKeysRequestBase(key_service_options)),
      list_active_keys_request_base_(
          GetListActiveKeysRequestBase(key_service_options)),
      get_keyset_metadata_request_base_(
          GetKeysetMetadataRequestBase(key_service_options)),
      allowed_keysets_list_(key_service_options.key_namespace().begin(),
                            key_service_options.key_namespace().end()),
      key_fetcher_options_(std::move(key_fetcher_options)),
      component_name_(component_name) {
  allowed_keysets_name_ = absl::StrJoin(allowed_keysets_list_, "_");
}

template <typename LookupKeyT>
ExecutionResult
CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::Init() noexcept {
  return SuccessExecutionResult();
}

template <typename LookupKeyT>
ExecutionResult CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::Run() noexcept {
  if (key_fetcher_options_.enable_prefetch) {
    PrefetchKeys();
  }

  if (key_fetcher_options_.enable_auto_refresh_keys) {
    auto_refresh_thread_ =
        std::thread(&CoordinatorKeyFetcherWithCacheBase<
                        LookupKeyT>::AutoRefreshThreadLoopFunction,
                    this);
  }

  if (key_fetcher_options_.enable_incremental_prefetch) {
    incremental_prefetch_thread_ =
        std::thread(&CoordinatorKeyFetcherWithCacheBase<
                        LookupKeyT>::IncrementalPrefetchThreadLoopFunction,
                    this);
  }
  return SuccessExecutionResult();
}

template <typename LookupKeyT>
ExecutionResult
CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::Stop() noexcept {
  StopBackgroundThreads();
  return SuccessExecutionResult();
}

template <typename LookupKeyT>
void CoordinatorKeyFetcherWithCacheBase<
    LookupKeyT>::StopBackgroundThreads() noexcept {
  if (key_fetcher_options_.enable_auto_refresh_keys ||
      key_fetcher_options_.enable_incremental_prefetch) {
    if (!stop_notification_.HasBeenNotified()) {
      stop_notification_.Notify();
      if (key_fetcher_options_.enable_auto_refresh_keys &&
          auto_refresh_thread_.joinable()) {
        auto_refresh_thread_.join();
      }
      if (key_fetcher_options_.enable_incremental_prefetch &&
          incremental_prefetch_thread_.joinable()) {
        incremental_prefetch_thread_.join();
      }
    }
  }
}

template <typename LookupKeyT>
core::ExecutionResultOr<ListPrivateKeysResponse>
CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::ListPrivateKeys(
    const ListPrivateKeysRequest& request, absl::string_view key_fetching_type,
    absl::string_view keyset_name) {
  PushKeyFetchingRequestMetric(metric_client_, key_type_, key_fetching_type,
                               keyset_name);
  auto fetching_start_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();

  auto response_or = key_client_.ListPrivateKeysSync(request);

  auto fetching_end_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();
  auto latency_in_millis =
      duration_cast<milliseconds>(
          nanoseconds(fetching_end_time_in_ns - fetching_start_time_in_ns))
          .count();
  PushKeyFetchingLatencyMetric(metric_client_, key_type_, key_fetching_type,
                               keyset_name, latency_in_millis);

  if (!response_or.Successful()) {
    PushKeyFetchingErrorMetric(
        metric_client_, key_type_, key_fetching_type, keyset_name,
        MapToKeyFetchingErrorString(response_or.result().status_code));
  }
  return response_or;
}

template <typename LookupKeyT>
core::ExecutionResultOr<ListActiveEncryptionKeysResponse>
CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::ListActiveKeys(
    const ListActiveEncryptionKeysRequest& request,
    absl::string_view key_fetching_type, absl::string_view keyset_name) {
  PushKeyFetchingRequestMetric(metric_client_, key_type_, key_fetching_type,
                               keyset_name);
  auto fetching_start_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();

  auto response_or = key_client_.ListActiveEncryptionKeysSync(request);

  auto fetching_end_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();
  auto latency_in_millis =
      duration_cast<milliseconds>(
          nanoseconds(fetching_end_time_in_ns - fetching_start_time_in_ns))
          .count();
  PushKeyFetchingLatencyMetric(metric_client_, key_type_, key_fetching_type,
                               keyset_name, latency_in_millis);

  if (!response_or.Successful()) {
    PushKeyFetchingErrorMetric(
        metric_client_, key_type_, key_fetching_type, keyset_name,
        MapToKeyFetchingErrorString(response_or.result().status_code));
  }
  return response_or;
}

template <typename LookupKeyT>
void CoordinatorKeyFetcherWithCacheBase<
    LookupKeyT>::SleepRandomDuration() noexcept {
  static random_device random_device_local;
  static mt19937 random_generator(random_device_local());
  uniform_int_distribution<uint64_t> distribution;
  auto max_delay_ms = key_fetcher_options_.max_prefetch_wait_time.count();
  sleep_for(milliseconds(distribution(random_generator) % max_delay_ms));
}

template <typename LookupKeyT>
void CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::PrefetchKeys() noexcept {
  // Sleep for a random delay to prevent multiple servers from prefetching at
  // once.
  SleepRandomDuration();
  auto one_week = duration_cast<nanoseconds>(hours(24 * 7)).count();
  for (const auto& keyset_name : allowed_keysets_list_) {
    FetchAndCacheKeysetMetadata(KeyFetchingType::kPrefetch, keyset_name);
    auto now = TimeProvider::GetWallTimestampInNanosecondsAsClockTicks();
    auto prefetch_config_iterator =
        key_fetcher_options_.prefetch_config_map.find(keyset_name);

    // New Encryption Key Prefetching Configuration
    if (prefetch_config_iterator !=
        key_fetcher_options_.prefetch_config_map.end()) {
      const auto& keyset_config = prefetch_config_iterator->second;

      if (keyset_config.key_ids_size() > 0) {
        SCP_INFO(component_name_, kZeroUuid,
                 "Prefetching with ListPrivateKeys for keyset %s.",
                 keyset_name.c_str());
        // Fetch one key ID at a time so a failure for one key does not affect
        // the others. Current use cases only have one key ID, so this doesn't
        // make much difference.
        for (const auto& key_id : keyset_config.key_ids()) {
          auto key_or = FetchKeysById(key_id, KeyFetchingType::kPrefetch);
          if (key_or.Successful()) {
            CacheValidKey({*key_or});
          }
        }
      }
      if (keyset_config.has_prefetch_duration()) {
        // ListActiveKeys needs to specify its start and end time
        // Start time using new prefetch config is (now - duration)
        // End time using new prefetch config is (now + 1 week)
        // We will prefetch for one future week by default to ensure reasonable
        // future keys are in the cache.
        auto duration_nanos = keyset_config.prefetch_duration().nanos();
        auto duration_seconds = keyset_config.prefetch_duration().seconds();
        int64_t duration =
            duration_cast<nanoseconds>(seconds(duration_seconds) +
                                       nanoseconds(duration_nanos))
                .count();
        auto start_time = TimeUtil::NanosecondsToTimestamp(now - duration);
        auto end_time = TimeUtil::NanosecondsToTimestamp(now + one_week);
        auto keys_or = FetchKeysInTimeRange(KeyFetchingType::kPrefetch,
                                            keyset_name, start_time, end_time);
        if (keys_or.Successful()) {
          CacheValidKey(*keys_or);
        }
      }
    } else {
      // ListActiveKeys needs to specify its start and end time to ensure the
      // behaviours for ListPrivateKeys and ListActiveKeys are in sync in the
      // legacy prefetch. Start time for the old prefetch system is (now - age
      // + 1 week) End time for the old prefetch system is (now + 1 week)
      int64_t age =
          duration_cast<nanoseconds>(key_fetcher_options_.prefetch_keys_max_age)
              .count();
      auto start_time = TimeUtil::NanosecondsToTimestamp(now - age + one_week);
      auto end_time = TimeUtil::NanosecondsToTimestamp(now + one_week);
      auto keys_or = FetchKeysInTimeRange(KeyFetchingType::kPrefetch,
                                          keyset_name, start_time, end_time);
      if (keys_or.Successful()) {
        CacheValidKey(*keys_or);
      }
    }
  }
}

template <typename LookupKeyT>
core::ExecutionResultOr<std::vector<Key>>
CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::FetchKeysInTimeRange(
    absl::string_view key_fetching_type, const std::string& keyset_name,
    const google::protobuf::Timestamp& start_time,
    const google::protobuf::Timestamp& end_time) noexcept {
  ListActiveEncryptionKeysRequest fetch_request =
      list_active_keys_request_base_;
  fetch_request.set_key_set_name(keyset_name);
  *fetch_request.mutable_query_time_range()->mutable_start_time() = start_time;
  *fetch_request.mutable_query_time_range()->mutable_end_time() = end_time;
  SCP_INFO(component_name_, kZeroUuid,
           "Fetching with ListActiveKeys for keyset %s.", keyset_name.c_str());

  auto response_or =
      ListActiveKeys(fetch_request, key_fetching_type, keyset_name);

  if (!response_or.Successful() &&
      (key_fetching_type == KeyFetchingType::kPrefetch ||
       key_fetching_type == KeyFetchingType::kAutoRefresh) &&
      key_fetcher_options_.enable_retry_in_prefetch_and_autorefresh) {
    SleepRandomDuration();
    SCP_INFO(component_name_, kZeroUuid,
             "Retrying fetching with ListActiveKeys for keyset %s.",
             keyset_name.c_str());
    response_or = ListActiveKeys(fetch_request, KeyFetchingType::kPrefetchRetry,
                                 keyset_name);
  }
  if (!response_or.Successful()) {
    SCP_ERROR(component_name_, kZeroUuid, response_or.result(),
              "Fetching with ListActiveKeys failed for keyset %s.",
              keyset_name.c_str());
    return response_or.result();
  }
  return ExtractKeys(response_or->private_keys());
}

template <typename LookupKeyT>
core::ExecutionResultOr<Key>
CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::FetchKeysById(
    const std::string& key_id, absl::string_view key_fetching_type) noexcept {
  ListPrivateKeysRequest request = list_private_keys_request_base_;
  request.add_key_ids(key_id);

  // We don't know the exact keyset yet for most cases, so use the
  // allowed_keysets_name which may be a list for metric recording.
  auto response_or =
      ListPrivateKeys(request, key_fetching_type, allowed_keysets_name_);

  if (!response_or.Successful() &&
      (key_fetching_type == KeyFetchingType::kPrefetch ||
       key_fetching_type == KeyFetchingType::kAutoRefresh) &&
      key_fetcher_options_.enable_retry_in_prefetch_and_autorefresh) {
    SleepRandomDuration();
    SCP_INFO(component_name_, kZeroUuid,
             "Retrying fetching with ListPrivateKeys for key ID %s.",
             key_id.c_str());
    response_or = ListPrivateKeys(request, KeyFetchingType::kPrefetchRetry,
                                  allowed_keysets_name_);
  }
  auto fetching_result = response_or.result();

  // The error metric for failed ListPrivateKeysResponse is already pushed. Here
  // we only push for validation failure.
  if (fetching_result.Successful() && response_or->private_keys().size() != 1) {
    fetching_result = response_or->private_keys().size() == 0
                          ? FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)
                          : FailureExecutionResult(SC_CPIO_KEY_COUNT_MISMATCH);

    PushKeyFetchingErrorMetric(
        metric_client_, key_type_, key_fetching_type, allowed_keysets_name_,
        MapToKeyFetchingErrorString(fetching_result.status_code));
  }

  if (!fetching_result.Successful()) {
    SCP_ERROR(component_name_, kZeroUuid, fetching_result,
              "The key fetching failed for key ID %s", key_id.c_str());
    return fetching_result;
  }

  auto& fetched_key = response_or->private_keys(0);
  const auto& keyset_name = fetched_key.key_set_name();

  // Checks that the key belongs to the allowed key sets configured by the
  // application configuration.
  if (!keyset_name.empty() && allowed_keysets_list_.count(keyset_name) == 0) {
    auto failure = FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND);
    SCP_ERROR(component_name_, kZeroUuid, failure,
              "The key set %s for this key does not belong to "
              "the key sets allowed by the application",
              keyset_name.c_str());
    // Log new key fetching error metric using OpenTelemetry
    PushKeyFetchingErrorMetric(
        metric_client_, key_type_, key_fetching_type, allowed_keysets_name_,
        MapToKeyFetchingErrorString(failure.status_code));
    return failure;
  }

  auto keys = ExtractKeys(response_or->private_keys());
  auto current_time = TimeProvider::GetWallTimestampInNanosecondsAsClockTicks();
  auto key_age_in_days =
      duration_cast<hours>(
          nanoseconds(current_time - keys[0].activation_timestamp))
          .count() /
      24;
  PushKeyAgeInDaysMetric(metric_client_, key_type_, key_fetching_type,
                         keyset_name, key_age_in_days);

  SCP_INFO(component_name_, kZeroUuid,
           "OndemandFetchingKeyId: %s | KeysetName: %s | KeyAge: %d",
           key_id.c_str(), keyset_name.c_str(), key_age_in_days);
  // Already checked the size is 1.
  return keys[0];
}

template <typename LookupKeyT>
void CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::WaitForKeyReady(
    const LookupKeyT& lookup_key) noexcept {
  auto start_time = system_clock::now();
  auto end_time = start_time;
  while (FetchingInProgress(lookup_key) &&
         (end_time - start_time) <
             key_fetcher_options_.on_demand_fetching_waiting_timeout) {
    sleep_for(kThreadSleepIntervalForKeyReady);
    end_time = system_clock::now();
  }
}

template <typename LookupKeyT>
string
CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::MapToKeyFetchingErrorString(
    StatusCode status_code) noexcept {
  if (status_code == SC_CPIO_KEY_NOT_FOUND ||
      status_code == SC_CPIO_ENTITY_NOT_FOUND ||
      status_code == SC_CPIO_INVALID_ARGUMENT) {
    return KeyFetchingErrorType::kInvalidKeyId;
  }
  return KeyFetchingErrorType::kGenericError;
}

template <typename LookupKeyT>
void CoordinatorKeyFetcherWithCacheBase<
    LookupKeyT>::AutoRefreshThreadLoopFunction() noexcept {
  while (!stop_notification_.WaitForNotificationWithTimeout(
      absl::Seconds(key_fetcher_options_.auto_refresh_time_duration.count()))) {
    for (const auto& keyset_name : allowed_keysets_list_) {
      // Try to fetch the keyset metadata when active_key_count isn't
      // being cached.
      if (GetExpectedActiveKeyCount(keyset_name) == 0) {
        FetchAndCacheKeysetMetadata(KeyFetchingType::kAutoRefresh, keyset_name);
      }
      AutoRefreshKeys(keyset_name);
    }
  }
}

template <typename LookupKeyT>
void CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::AutoRefreshKeys(
    std::string keyset_name) noexcept {
  if (!ShouldPerformAutoRefresh(keyset_name)) {
    return;
  }
  SCP_DEBUG(component_name_, kZeroUuid, "Refreshing keys for keyset %s",
            keyset_name.c_str());

  auto now = TimeProvider::GetWallTimestampInNanosecondsAsClockTicks();
  auto key_fetching_query_start_time = TimeUtil::NanosecondsToTimestamp(
      now - duration_cast<nanoseconds>(
                hours(kAutoRefreshFetchActiveKeysStartTimeSubDiffInHours))
                .count());
  auto key_fetching_query_end_time = TimeUtil::NanosecondsToTimestamp(
      now + duration_cast<nanoseconds>(
                hours(kAutoRefreshFetchActiveKeysEndTimeAddedDiffInHours))
                .count());

  auto keys_or = FetchKeysInTimeRange(
      KeyFetchingType::kAutoRefresh, keyset_name, key_fetching_query_start_time,
      key_fetching_query_end_time);
  if (keys_or.Successful()) {
    CheckEnoughActiveKeys(KeyFetchingType::kAutoRefresh, keyset_name, *keys_or);
    CacheValidKey(*keys_or);
  }
}

template <typename LookupKeyT>
void CoordinatorKeyFetcherWithCacheBase<
    LookupKeyT>::IncrementalPrefetchThreadLoopFunction() noexcept {
  absl::flat_hash_map<std::string, IncrementalPrefetchCursor>
      incremental_prefetch_cursor_map;
  auto now = TimeProvider::GetWallTimestampInNanosecondsAsClockTicks();
  for (const auto& [keyset_name, config] :
       key_fetcher_options_.prefetch_config_map) {
    if (allowed_keysets_list_.count(keyset_name) == 0) {
      continue;
    }
    auto step_duration_in_ns = TimeUtil::DurationToNanoseconds(
        config.incremental_prefetch_step_duration());
    auto prefetch_total_duration_in_ns = TimeUtil::DurationToNanoseconds(
        config.incremental_prefetch_total_duration());
    if (step_duration_in_ns <= 0 || prefetch_total_duration_in_ns <= 0) {
      continue;
    }

    auto prefetch_duration =
        TimeUtil::DurationToNanoseconds(config.prefetch_duration());
    incremental_prefetch_cursor_map[keyset_name] = IncrementalPrefetchCursor{
        .earliest_prefetch_start_time = now - prefetch_total_duration_in_ns,
        .end_time = prefetch_duration > 0 ? now - prefetch_duration : now,
        .step_duration_in_ns = step_duration_in_ns,
    };
  }

  while (!stop_notification_.WaitForNotificationWithTimeout(absl::Seconds(
      key_fetcher_options_.incremental_prefetch_time_duration.count()))) {
    bool has_remaining_work = false;
    for (auto& [keyset_name, cursor] : incremental_prefetch_cursor_map) {
      if (IncrementalPrefetchKeys(keyset_name, cursor)) {
        has_remaining_work = true;
      }
    }
    if (!has_remaining_work) {
      return;
    }
  }
}

template <typename LookupKeyT>
bool CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::IncrementalPrefetchKeys(
    const std::string& keyset_name,
    IncrementalPrefetchCursor& cursor) noexcept {
  if (cursor.end_time <= cursor.earliest_prefetch_start_time) {
    return false;
  }

  auto end_time = cursor.end_time;
  auto start_time = std::max(cursor.earliest_prefetch_start_time,
                             end_time - cursor.step_duration_in_ns);

  auto start_timestamp = TimeUtil::NanosecondsToTimestamp(start_time);
  auto end_timestamp = TimeUtil::NanosecondsToTimestamp(end_time);
  SCP_INFO(component_name_, kZeroUuid,
           "Incremental prefetch keys for keyset %s from %s to %s.",
           keyset_name.c_str(), TimeUtil::ToString(start_timestamp).c_str(),
           TimeUtil::ToString(end_timestamp).c_str());

  auto keys_or =
      FetchKeysInTimeRange(KeyFetchingType::kIncrementalPrefetch, keyset_name,
                           start_timestamp, end_timestamp);
  if (keys_or.Successful()) {
    CacheValidKey(*keys_or);
  }
  cursor.end_time = start_time;
  return true;
}

template <typename LookupKeyT>
void CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::
    FetchAndCacheKeysetMetadata(absl::string_view key_fetching_type,
                                const std::string& keyset_name) noexcept {
  GetKeysetMetadataRequest get_keyset_metadata_request =
      get_keyset_metadata_request_base_;
  get_keyset_metadata_request.set_keyset_name(keyset_name);
  // Log keyset fetching request metric using OpenTelemetry
  PushKeyFetchingRequestMetric(metric_client_, KeyType::kKeysetMetadata,
                               key_fetching_type, keyset_name);

  if (auto response_or =
          key_client_.GetKeysetMetadataSync(get_keyset_metadata_request);
      response_or.Successful()) {
    std::unique_lock lock(keyset_metadata_mutex_);
    auto& metadata = keyset_metadata_map_[keyset_name];
    metadata.active_key_count = response_or->active_key_count();
    metadata.backfill_days = response_or->backfill_days();
  } else {
    // Log keyset fetching error metric using OpenTelemetry
    PushKeyFetchingErrorMetric(
        metric_client_, KeyType::kKeysetMetadata, key_fetching_type,
        keyset_name,
        MapToKeyFetchingErrorString(response_or.result().status_code));
    SCP_ERROR(component_name_, kZeroUuid, response_or.result(),
              "keyset metadata fetching failed for keyset %s",
              keyset_name.c_str());
  }
}

template <typename LookupKeyT>
int CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::GetExpectedActiveKeyCount(
    absl::string_view keyset_name) noexcept {
  std::shared_lock lock(keyset_metadata_mutex_);
  auto it = keyset_metadata_map_.find(keyset_name);
  return it != keyset_metadata_map_.end() ? it->second.active_key_count : 0;
}

template <typename LookupKeyT>
void CoordinatorKeyFetcherWithCacheBase<LookupKeyT>::CheckEnoughActiveKeys(
    absl::string_view key_fetching_type, const std::string& keyset_name,
    const std::vector<Key>& fetched_keys) noexcept {
  int expected_active_key_count = GetExpectedActiveKeyCount(keyset_name);
  if (expected_active_key_count <= 0) {
    return;
  }
  auto active_key_check_time_ns =
      TimeProvider::GetWallTimestampInNanosecondsAsClockTicks() +
      duration_cast<nanoseconds>(hours(kCheckActiveKeysCountAddedDiffInHours))
          .count();
  int fetched_active_key_count =
      GetActiveKeysAt(fetched_keys, active_key_check_time_ns).size();
  if (fetched_active_key_count != expected_active_key_count) {
    PushKeyFetchingErrorMetric(metric_client_, key_type_, key_fetching_type,
                               keyset_name,
                               KeyFetchingErrorType::kActiveKeyCountMismatch);
    SCP_ERROR_WITHOUT_RESULT(
        component_name_, kZeroUuid,
        "The number of fetched active keys in keyset %s does not match the "
        "expected active key count at one time point. Fetched active key "
        "count: %d, expected active key count: %d.",
        keyset_name.c_str(), fetched_active_key_count,
        expected_active_key_count);
  }
}

template class CoordinatorKeyFetcherWithCacheBase<std::string>;
template class CoordinatorKeyFetcherWithCacheBase<core::Timestamp>;

}  // namespace google::scp::cpio
