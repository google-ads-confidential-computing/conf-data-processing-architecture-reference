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

#pragma once

#include <optional>
#include <set>
#include <shared_mutex>
#include <string>
#include <thread>
#include <unordered_set>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/synchronization/notification.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/private_key_client/private_key_client_interface.h"
#include "public/cpio/utils/dual_writing_metric_client/interface/dual_writing_metric_client_interface.h"
#include "public/cpio/utils/key_fetching/interface/key_fetcher_with_cache_interface.h"
#include "public/cpio/utils/key_fetching/proto/key_coordinator_configuration.pb.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"

#include "error_codes.h"

namespace google::scp::cpio {

struct BaseKeyFetcherOptions {
  BaseKeyFetcherOptions() = default;
  virtual ~BaseKeyFetcherOptions() = default;

  bool enable_prefetch = false;
  bool enable_retry_in_prefetch_and_autorefresh = true;
  std::chrono::milliseconds max_prefetch_wait_time =
      std::chrono::milliseconds(10 * 1000);  // 10s
  std::chrono::seconds prefetch_keys_max_age =
      std::chrono::seconds(14 * 24 * 60 * 60);  // 14 days
  std::chrono::milliseconds on_demand_fetching_waiting_timeout =
      std::chrono::milliseconds(2000);  // 2s
  absl::flat_hash_map<
      std::string,
      google::cmrt::sdk::v1::EncryptionKeyPrefetchConfig::KeysetPrefetchConfig>
      prefetch_config_map;
  bool enable_auto_refresh_keys = true;
  std::chrono::seconds auto_refresh_time_duration =
      std::chrono::seconds(24 * 60 * 60);  // 24 hours
  bool enable_incremental_prefetch = false;
  std::chrono::seconds incremental_prefetch_time_duration =
      std::chrono::seconds(5 * 60);  // 5m
};

/**
 * @brief Base class for cache that fetch keys from key coordinators.
 *
 * @tparam LookupKeyT The type of the key used to look up keys in the cache.
 * Possible types are `std::string` (key IDs for cache of encryption keys) and
 * `core::Timestamp` (timestamps for cache of SID keys).
 */
template <typename LookupKeyT>
class CoordinatorKeyFetcherWithCacheBase {
 public:
  explicit CoordinatorKeyFetcherWithCacheBase(
      PrivateKeyClientInterface& key_client,
      DualWritingMetricClientInterface& metric_client,
      const google::cmrt::sdk::v1::KeyCoordinatorConfiguration&
          key_service_options,
      BaseKeyFetcherOptions key_fetcher_options,
      absl::string_view component_name, absl::string_view key_type,
      const std::string& metric_namespace = {});

  virtual ~CoordinatorKeyFetcherWithCacheBase() { StopBackgroundThreads(); }

  core::ExecutionResult Init() noexcept;

  core::ExecutionResult Run() noexcept;

  core::ExecutionResult Stop() noexcept;

 protected:
  /// Cache valid keys.
  virtual void CacheValidKey(const std::vector<Key>& valid_keys) noexcept = 0;

  /// Mark key fetching status as finished.
  virtual void MarkFetchingFinished(const LookupKeyT& lookup_key) noexcept = 0;
  /**
   * @brief Mark the key fetching status as in progress when it is not yet.
   *
   * @return true made the operation.
   * @return false the status is already in progress and skip add.
   */
  virtual bool MarkFetchingInProgress(
      const LookupKeyT& lookup_key) noexcept = 0;
  /// Check if the key fetching is in progress.
  virtual bool FetchingInProgress(const LookupKeyT& lookup_key) noexcept = 0;

  /// Determine if the auto refresh for a given keyset should be performed.
  virtual bool ShouldPerformAutoRefresh(
      absl::string_view keyset_name) noexcept = 0;

  /**
   * @brief Fetch the key with the given key ID using the ListPrivateKeys API
   * and validate it: exactly one key must be returned, and it must belong to an
   * allowed keyset. For prefetch and auto-refresh, retries once on failure if
   * enable_retry_in_prefetch_and_autorefresh is set. Does not cache the result.
   *
   * @param key_id the key ID to fetch
   * @param key_fetching_type the key fetching type
   * @return ExecutionResultOr<Key> fetched key or failure
   */
  core::ExecutionResultOr<Key> FetchKeysById(
      const std::string& key_id, absl::string_view key_fetching_type) noexcept;

  /// Stop background threads (auto refresh and incremental prefetch) if
  /// running.
  void StopBackgroundThreads() noexcept;

  /// Wait for the key fetching finishing.
  void WaitForKeyReady(const LookupKeyT& lookup_key) noexcept;

  DualWritingMetricClientInterface& metric_client_;
  std::string key_type_;
  std::string allowed_keysets_name_;

 private:
  struct IncrementalPrefetchCursor {
    // The earliest timestamp to prefetch back to (thread start time -
    // prefetch_total_duration).
    core::Timestamp earliest_prefetch_start_time;
    // The end time for the next incremental prefetch window, updated as we
    // step backward toward `cutoff`.
    core::Timestamp end_time;
    // The step duration in nanoseconds for each incremental prefetch step.
    int64_t step_duration_in_ns;
  };

  // The input keyset_name is only used for metrics.
  core::ExecutionResultOr<
      google::cmrt::sdk::private_key_service::v1::ListPrivateKeysResponse>
  ListPrivateKeys(
      const google::cmrt::sdk::private_key_service::v1::ListPrivateKeysRequest&
          request,
      absl::string_view key_fetching_type, absl::string_view keyset_name);

  core::ExecutionResultOr<google::cmrt::sdk::private_key_service::v1::
                              ListActiveEncryptionKeysResponse>
  ListActiveKeys(const google::cmrt::sdk::private_key_service::v1::
                     ListActiveEncryptionKeysRequest& request,
                 absl::string_view key_fetching_type,
                 absl::string_view keyset_name);

  // Helper function to sleep a random duration.
  void SleepRandomDuration() noexcept;

  /// Prefetch recent keys.
  void PrefetchKeys() noexcept;

  // Fetches active keys in [start_time, end_time] for the keyset using the
  // ListActiveKeys API. For prefetch and auto-refresh, retries once on failure
  // if enable_retry_in_prefetch_and_autorefresh is set.
  core::ExecutionResultOr<std::vector<Key>> FetchKeysInTimeRange(
      absl::string_view key_fetching_type, const std::string& keyset_name,
      const google::protobuf::Timestamp& start_time,
      const google::protobuf::Timestamp& end_time) noexcept;

  // Function to convert an error during key fetching to a string
  // for metric recording.
  std::string MapToKeyFetchingErrorString(
      core::StatusCode status_code) noexcept;

  /// Background worker loop that periodically triggers AutoRefreshKeys for all
  /// configured keysets until stopped.
  void AutoRefreshThreadLoopFunction() noexcept;

  /// Fetches active keys for a single keyset and updates the cache.
  void AutoRefreshKeys(std::string keyset_name) noexcept;

  /// Background worker loop that periodically triggers IncrementalPrefetchKeys
  /// for all configured keysets until stopped.
  void IncrementalPrefetchThreadLoopFunction() noexcept;

  /// Fetches historical keys for a single keyset stepping backward into the
  /// past. Returns true if a fetch was performed, or false if the lookback
  /// cutoff has already been reached.
  bool IncrementalPrefetchKeys(const std::string& keyset_name,
                               IncrementalPrefetchCursor& cursor) noexcept;

  PrivateKeyClientInterface& key_client_;

  const google::cmrt::sdk::private_key_service::v1::ListPrivateKeysRequest
      list_private_keys_request_base_;
  const google::cmrt::sdk::private_key_service::v1::
      ListActiveEncryptionKeysRequest list_active_keys_request_base_;
  const std::set<std::string> allowed_keysets_list_;

  BaseKeyFetcherOptions key_fetcher_options_;
  std::string component_name_;

  std::thread auto_refresh_thread_;
  absl::Notification stop_notification_;

  std::thread incremental_prefetch_thread_;
};
}  // namespace google::scp::cpio
