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

  virtual ~CoordinatorKeyFetcherWithCacheBase() { StopAutoRefreshThread(); }

  core::ExecutionResult Init() noexcept;

  core::ExecutionResult Run() noexcept;

  core::ExecutionResult Stop() noexcept;

 protected:
  /// Cache valid keys.
  virtual void CacheValidKey(const std::vector<Key>& valid_keys) noexcept = 0;

  /**
   * @brief Get the Key From Valid Key Cache
   *
   * @param lookup_key key used to lookup in the cache, can be string for key id
   * or timestamp
   * @return std::optional<Key> found key
   */
  virtual std::optional<Key> GetKeyFromValidKeyCache(
      const LookupKeyT& lookup_key) noexcept = 0;

  /**
   * @brief Get the Key Fetching Failure from Cache for the Given Lookup Key
   *
   * @param lookup_key key used to lookup in the cache, can be string for key id
   * or timestamp
   * @return std::optional<ExecutionResult> found failure result
   */
  virtual std::optional<core::ExecutionResult> GetFetchingFailureFromCache(
      const LookupKeyT& lookup_key) noexcept = 0;

  /**
   * @brief Cache failure result and lookup key for fetching failures
   *
   * @param lookup_key key used to lookup in the cache, can be string for key id
   * or timestamp
   * @param failure_result failure result
   */
  virtual void CacheFailureResult(
      LookupKeyT lookup_key, core::ExecutionResult failure_result) noexcept = 0;

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
   * @brief Fetch and validate the key for the given lookup key from remote.
   * Called by GetKeyInternal() on a cache miss. Implementations must not cache
   * the result: GetKeyInternal() caches the returned key or failure.
   *
   * @param lookup_key key used to lookup in the cache, can be string for key id
   * or timestamp
   * @param key_fetching_type the key fetching type
   * @return ExecutionResultOr<Key> fetched key or failure
   */
  virtual core::ExecutionResultOr<Key> FetchKeys(
      const LookupKeyT& lookup_key,
      absl::string_view key_fetching_type) noexcept = 0;

  /**
   * @brief Fetch the key with the given key ID using the ListPrivateKeys API
   * and validate it: exactly one key must be returned, and it must belong to an
   * allowed keyset. Does not cache the result.
   *
   * @param key_id the key ID to fetch
   * @param key_fetching_type the key fetching type
   * @return ExecutionResultOr<Key> fetched key or failure
   */
  core::ExecutionResultOr<Key> FetchKeysById(
      const std::string& key_id, absl::string_view key_fetching_type) noexcept;

  // Get the key from valid key cache or fetch it from remote.
  core::ExecutionResultOr<Key> GetKeyInternal(
      const LookupKeyT& lookup_key,
      absl::string_view key_fetching_type) noexcept;

  /// Stop the auto refresh background thread if running.
  void StopAutoRefreshThread() noexcept;

 private:
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
  // ListActiveKeys API and caches them. For prefetch and auto-refresh, retries
  // once on failure if enable_retry_in_prefetch_and_autorefresh is set.
  void FetchKeysInTimeRange(
      absl::string_view key_fetching_type, const std::string& keyset_name,
      const google::protobuf::Timestamp& start_time,
      const google::protobuf::Timestamp& end_time) noexcept;

  // Prefetches keys with the given key IDs for the keyset using the
  // ListPrivateKeys API and caches them. Retries once on failure if
  // enable_retry_in_prefetch_and_autorefresh is set.
  void PrefetchKeysByIds(
      const std::string& keyset_name,
      const std::optional<google::protobuf::RepeatedPtrField<std::string>>&
          key_ids) noexcept;

  /// Wait for the key fetching finishing.
  void WaitForKeyReady(const LookupKeyT& lookup_key) noexcept;

  // Function to convert an error during key fetching to a string
  // for metric recording.
  std::string MapToKeyFetchingErrorString(
      core::StatusCode status_code) noexcept;

  /// Background worker loop that periodically triggers AutoRefreshKeys for all
  /// configured keysets until stopped.
  void AutoRefreshThreadLoopFunction() noexcept;

  /// Fetches active keys for a single keyset and updates the cache.
  void AutoRefreshKeys(std::string keyset_name) noexcept;

  PrivateKeyClientInterface& key_client_;

  const google::cmrt::sdk::private_key_service::v1::ListPrivateKeysRequest
      list_private_keys_request_base_;
  const google::cmrt::sdk::private_key_service::v1::
      ListActiveEncryptionKeysRequest list_active_keys_request_base_;
  const std::set<std::string> allowed_keysets_list_;

  BaseKeyFetcherOptions key_fetcher_options_;

  DualWritingMetricClientInterface& metric_client_;
  std::string allowed_keysets_name_;
  std::string component_name_;
  std::string key_type_;

  std::thread auto_refresh_thread_;
  absl::Notification stop_notification_;
};
}  // namespace google::scp::cpio
