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
#include <string>

#include "core/interface/service_interface.h"
#include "core/interface/type_def.h"
#include "public/core/interface/execution_result.h"

namespace google::scp::cpio {

/**
 * @brief Provides time aggregate metric. It records the accumulative number
 * of time event in set period, and pushes the metrics data to the cloud server.
 */
class TimeAggregateMetricInterface : public core::ServiceInterface {
 public:
  virtual ~TimeAggregateMetricInterface() = default;
  /**
   * @brief Record a duration in a metric counter.

   * @param duration_in_ms The duration to Increment the counter
   * @param event_code The event_code used to identify the metric counter. If no
   * event_code provided, the function will act on the default counter.
   * @return core::ExecutionResult
   */
  virtual core::ExecutionResult RecordDuration(
      const google::scp::core::TimeDuration& duration_in_ms,
      const std::string& event_code = std::string()) noexcept = 0;
};
}  // namespace google::scp::cpio
