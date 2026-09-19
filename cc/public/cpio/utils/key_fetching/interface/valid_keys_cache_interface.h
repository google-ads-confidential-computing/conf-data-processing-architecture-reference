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

#include <vector>

#include "core/common/time_provider/src/time_provider.h"
#include "core/interface/service_interface.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/utils/key_fetching/interface/key_fetcher_with_cache_interface.h"

namespace google::scp::cpio {

/// @brief Interface to cache a set of valid keys for a given timestamp from a
/// key management service.
class ValidKeysCacheInterface : public google::scp::core::ServiceInterface {
 public:
  virtual ~ValidKeysCacheInterface() = default;

  /// @brief Retrieves valid active keys for given timestamp.
  /// @param key_selection_timestamp_ns The timestamp to select valid keys. This
  /// is optional field for key selection algorithm that requires a timestamp.
  /// If not provided, the current wall time will be used.
  /// NOTE: This list must be sorted in ascending order by the keys'
  /// expiration_timestamp.
  /// @return Valid key list or key not found error.
  virtual core::ExecutionResultOr<std::vector<Key>> GetValidKeys(
      core::Timestamp key_selection_timestamp_ns = google::scp::core::common::
          TimeProvider::GetWallTimestampInNanosecondsAsClockTicks()) noexcept {
    // Default not implemented error
    return core::FailureExecutionResult(SC_UNKNOWN);
  }
};

}  // namespace google::scp::cpio
