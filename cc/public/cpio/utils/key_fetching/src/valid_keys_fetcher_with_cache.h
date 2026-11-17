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

#include <shared_mutex>
#include <string>
#include <vector>

#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/private_key_client/private_key_client_interface.h"
#include "public/cpio/utils/dual_writing_metric_client/interface/dual_writing_metric_client_interface.h"
#include "public/cpio/utils/key_fetching/interface/valid_keys_cache_interface.h"

#include "coordinator_key_fetcher_with_cache_base.h"

namespace google::scp::cpio {

///
/// @brief Auto-refresh Key cache for the coordinator's keys. This is only for
/// Hmac keys for now. NOTE: This cache caches keys forever without any eviction
/// policy. The expected usage of this is in situations where the number of keys
/// will not be significant.
/// The cache ensures the relevant keys are always available, right from the
/// point when Run() is invoked.
///
class ValidKeysFetcherWithCache
    : public CoordinatorKeyFetcherWithCacheBase<core::Timestamp>,
      public ValidKeysCacheInterface {
 public:
  ValidKeysFetcherWithCache(
      PrivateKeyClientInterface& key_client,
      const std::vector<
          google::cmrt::sdk::private_key_service::v1::PrivateKeyEndpoint>&
          private_key_endpoints,
      const std::string& keyset_name,
      DualWritingMetricClientInterface& metric_client,
      KeyFetcherOptions key_fetcher_options,
      const std::string& metric_namespace = {});

  ~ValidKeysFetcherWithCache() override { StopBackgroundThreads(); }

  core::ExecutionResultOr<std::vector<Key>> GetValidKeys(
      core::Timestamp key_selection_timestamp_ns =
          google::scp::core::common::TimeProvider::
              GetWallTimestampInNanosecondsAsClockTicks()) noexcept override;

  core::ExecutionResult Init() noexcept override {
    return CoordinatorKeyFetcherWithCacheBase::Init();
  }

  /// @brief Runs the cache refresher and makes the latest set of keys available
  /// immediately for use after this call.
  /// @return
  core::ExecutionResult Run() noexcept override {
    return CoordinatorKeyFetcherWithCacheBase::Run();
  }

  /// @brief Stops the cache refresher.
  /// @return
  core::ExecutionResult Stop() noexcept override {
    return CoordinatorKeyFetcherWithCacheBase::Stop();
  }

 private:
  enum class FetchingStatus { UNKNOWN = 0, IN_PROGRESS = 1, FINISHED = 2 };

  /**
   * @brief Insert keys into key cache.
   *
   * @param keys keys to cache.
   */
  void CacheValidKey(const std::vector<Key>& keys) noexcept override;

  // Gets valid cached keys based on the specified timestamp.
  core::ExecutionResultOr<std::vector<Key>> GetKeysFromCache(
      core::Timestamp timestamp_ns) noexcept;

  /**
   * @brief On-demand key fetching from key service and cache the keys.
   */
  void OnDemandFetchAndCacheKeys() noexcept;

  core::ExecutionResult ValidateKeySelectionTimestamp(
      core::Timestamp key_selection_timestamp_ns) noexcept;

  /// Set the fetching status to FINISHED.
  void MarkFetchingFinished() noexcept;
  /**
   * @brief Try to set the fetching status to IN_PROGRESS when it is not yet.
   *
   * @return true made the update.
   * @return false the status is already IN_PROGRESS and the update is skipped.
   */
  bool MarkFetchingInProgress() noexcept;
  /// Check if the fetching status is IN_PROGRESS.
  bool FetchingInProgress(
      const core::Timestamp& timestamp_ns) noexcept override;

  /// Determine if the auto refresh for a given keyset should be performed.
  bool ShouldPerformAutoRefresh(
      const std::string& keyset_name) noexcept override;

  const std::string keyset_name_;
  KeyFetcherOptions key_fetcher_options_;

  std::shared_mutex key_cache_mutex_;

  std::shared_mutex status_mutex_;
  FetchingStatus key_fetching_status_;
  absl::btree_map<core::Timestamp, Key> key_cache_map_;
};
}  // namespace google::scp::cpio