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

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "core/interface/async_context.h"
#include "core/interface/async_executor_interface.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/metric_client/metric_client_interface.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"
#include "public/cpio/utils/metric_instance/interface/time_aggregate_metric_interface.h"
#include "public/cpio/utils/metric_instance/interface/type_def.h"

#include "error_codes.h"

namespace google::scp::cpio {
double CalculateAverageDuration(
    const size_t current_counter,
    const core::AverageTimeDuration& current_average_duration,
    const core::TimeDuration& new_duration) noexcept;

/*! @copydoc TimeAggregateMetricInterface
 */
class TimeAggregateMetric : public TimeAggregateMetricInterface {
 public:
  explicit TimeAggregateMetric(core::AsyncExecutorInterface* async_executor,
                               MetricClientInterface* metric_client,
                               MetricDefinition metric_info,
                               core::TimeDuration push_interval_duration_in_ms);

  explicit TimeAggregateMetric(
      core::AsyncExecutorInterface* async_executor,
      MetricClientInterface* metric_client, MetricDefinition metric_info,
      core::TimeDuration push_interval_duration_in_ms,
      const std::vector<std::string>& event_code_labels_list,
      const std::string& event_code_label_key = kEventCodeLabelKey);

  core::ExecutionResult Init() noexcept override;

  core::ExecutionResult Run() noexcept override;

  core::ExecutionResult Stop() noexcept override;

  core::ExecutionResult RecordDuration(
      const core::TimeDuration& duration_in_ms,
      const std::string& event_code = std::string()) noexcept override;

 protected:
  /**
   * @brief Runs the actual metric push logic for one duration data.
   *
   * @param average_time_duration the average duration to be the metric value.
   * @param metric_info Metric info specifies the metric's name, unit,
   * namespace, and labels.
   */
  virtual void MetricPushHandler(
      const core::AverageTimeDuration& average_time_duration,
      const MetricDefinition& metric_info) noexcept;

  /**
   * @brief Goes over all counters and push metric when the counter has valid
   * value. Also reset some counters. This operation must be error free to
   * avoid memory increases overtime. In the case of errors an alert must be
   * raised.
   */
  virtual void RunMetricPush() noexcept;

  /**
   * @brief Schedules a round of metric push in the next time_duration_.
   *
   * @return core::ExecutionResult
   */
  virtual core::ExecutionResult ScheduleMetricPush() noexcept;

  double CalculateAverageDurationCounter(
      const size_t current_counter,
      const core::AverageTimeDuration& current_average_duration,
      const core::TimeDuration& new_duration) noexcept;

  /// The unordered_map contains the event codes paired with its counter.
  /// The event_counter is associated with the event_code.
  std::unordered_map<std::string, std::pair<size_t, core::AverageTimeDuration>>
      event_counters_;

  /// The unordered_map contains the event codes paired with its metric
  /// definition.
  std::unordered_map<std::string, const MetricDefinition> event_metric_infos_;

  /// An instance to the async executor.
  core::AsyncExecutorInterface* async_executor_;
  /// Metric client instance.
  MetricClientInterface* metric_client_;
  /// Metric general information.
  const MetricDefinition metric_info_;

  /// The time duration of the aggregated metric push interval in milliseconds.
  core::TimeDuration push_interval_duration_in_ms_;

  /// The default counter in AggregateMetric. This counter doesn't have
  /// event_code or metric_tag, and it defined by metric_info_ only. When
  /// construct AggregateMetric without event_code_labels_list, this is the only
  /// default counter in AggregateMetric.
  std::pair<size_t, core::AverageTimeDuration> average_duration_counter_;

  /// The cancellation callback.
  std::function<bool()> current_cancellation_callback_;

  /// Indicates whether the component stopped
  std::atomic<bool> is_running_;

  /// Indicates whether the component can take metric increments
  std::atomic<bool> can_accept_incoming_increments_;

  static constexpr char kEventCodeLabelKey[] = "EventCode";

  /// @brief activity ID for the lifetime of the object
  const core::common::Uuid object_activity_id_;

  /// @brief mutex to protect scheduling new tasks while stopping the component
  std::mutex task_schedule_mutex_;

  /// @brief mutex to protect scheduling new tasks while stopping the component
  std::mutex record_mutex_;
};
}  // namespace google::scp::cpio
