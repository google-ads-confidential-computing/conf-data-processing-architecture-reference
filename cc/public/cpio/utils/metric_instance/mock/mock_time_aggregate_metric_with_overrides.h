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
#include <utility>
#include <vector>

#include "core/interface/async_context.h"
#include "core/interface/async_executor_interface.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"
#include "public/cpio/utils/metric_instance/interface/type_def.h"
#include "public/cpio/utils/metric_instance/src/time_aggregate_metric.h"

namespace google::scp::cpio {
class MockTimeAggregateMetricOverrides : public TimeAggregateMetric {
 public:
  explicit MockTimeAggregateMetricOverrides(
      core::AsyncExecutorInterface* async_executor,
      MetricClientInterface* metric_client, MetricDefinition metric_info,
      const core::TimeDuration aggregation_time_duration_ms,
      const std::vector<std::string>& event_list = {})
      : TimeAggregateMetric(async_executor, metric_client,
                            std::move(metric_info),
                            aggregation_time_duration_ms, event_list) {
    // For mocking purposes, we should be able to accept incoming metric even if
    // the mock is not Run().
    can_accept_incoming_increments_ = true;
  }

  std::function<void()> run_metric_push_mock;

  std::function<void(const core::AverageTimeDuration& average_time_duration,
                     const MetricDefinition& metric_info)>
      metric_push_handler_mock;

  std::function<core::ExecutionResult()> schedule_metric_push_mock;

  core::ExecutionResult Run() noexcept { return TimeAggregateMetric::Run(); }

  std::pair<size_t, core::AverageTimeDuration> GetCounter(
      const std::string& event_code = std::string()) {
    if (event_code.empty()) {
      return TimeAggregateMetric::average_duration_counter_;
    }

    auto event = event_counters_.find(event_code);
    if (event != event_counters_.end()) {
      return event->second;
    }
    return std::pair(0, 0);
  }

  core::ExecutionResultOr<MetricDefinition> GetMetricInfo(
      const std::string& event_code) {
    const auto& event = event_metric_infos_.find(event_code);
    if (event != event_metric_infos_.end()) {
      return event->second;
    }
    return core::FailureExecutionResult(SC_UNKNOWN);
  }

  void MetricPushHandler(const core::AverageTimeDuration& average_time_duration,
                         const MetricDefinition& metric_info) noexcept {
    if (metric_push_handler_mock) {
      return metric_push_handler_mock(average_time_duration, metric_info);
    }
    return TimeAggregateMetric::MetricPushHandler(average_time_duration,
                                                  metric_info);
  }

  void RunMetricPush() noexcept {
    if (run_metric_push_mock) {
      return run_metric_push_mock();
    }
    return TimeAggregateMetric::RunMetricPush();
  }

  core::ExecutionResult ScheduleMetricPush() noexcept {
    if (schedule_metric_push_mock) {
      return schedule_metric_push_mock();
    }
    return TimeAggregateMetric::ScheduleMetricPush();
  }
};

}  // namespace google::scp::cpio
