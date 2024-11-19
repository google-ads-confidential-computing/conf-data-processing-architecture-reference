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

#include "time_aggregate_metric.h"

#include <chrono>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "absl/strings/str_join.h"
#include "core/common/time_provider/src/time_provider.h"
#include "core/interface/async_context.h"
#include "core/interface/async_executor_interface.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"
#include "public/cpio/utils/metric_instance/interface/type_def.h"

#include "error_codes.h"
#include "metric_utils.h"

using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::scp::core::AsyncContext;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::AsyncPriority;
using google::scp::core::AverageTimeDuration;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::TimeDuration;
using google::scp::core::Timestamp;
using google::scp::core::common::TimeProvider;
using google::scp::core::common::Uuid;
using google::scp::core::errors::SC_CUSTOMIZED_METRIC_EVENT_CODE_NOT_EXIST;
using google::scp::core::errors::
    SC_CUSTOMIZED_METRIC_INVALID_AGGREGATION_INTERVAL_DURATION;
using google::scp::core::errors::SC_CUSTOMIZED_METRIC_PUSH_CANNOT_SCHEDULE;
using google::scp::cpio::MetricClientInterface;
using std::make_pair;
using std::make_shared;
using std::map;
using std::move;
using std::mutex;
using std::pair;
using std::shared_ptr;
using std::string;
using std::to_string;
using std::vector;
using std::chrono::milliseconds;
using std::this_thread::sleep_for;

static constexpr char kTimeAggregateMetric[] = "TimeAggregateMetric";

static constexpr milliseconds kStopWaitSleepDuration = milliseconds(500);
static constexpr TimeDuration kMinimumPushIntervalDuration = 5000;

namespace google::scp::cpio {
double CalculateAverageDuration(
    const size_t current_counter,
    const AverageTimeDuration& current_average_duration,
    const TimeDuration& new_duration) noexcept {
  return current_average_duration / (current_counter + 1.0) * current_counter +
         new_duration / (current_counter + 1.0);
}

TimeAggregateMetric::TimeAggregateMetric(
    core::AsyncExecutorInterface* async_executor,
    MetricClientInterface* metric_client, MetricDefinition metric_info,
    TimeDuration push_interval_duration_in_ms)
    : async_executor_(async_executor),
      metric_client_(metric_client),
      metric_info_(move(metric_info)),
      push_interval_duration_in_ms_(push_interval_duration_in_ms),
      average_duration_counter_(make_pair(0, 0)),
      is_running_(false),
      can_accept_incoming_increments_(false),
      object_activity_id_(Uuid::GenerateUuid()) {}

TimeAggregateMetric::TimeAggregateMetric(
    core::AsyncExecutorInterface* async_executor,
    MetricClientInterface* metric_client, MetricDefinition metric_info,
    TimeDuration push_interval_duration_in_ms,
    const vector<string>& event_code_labels_list,
    const std::string& event_code_label_key)
    : async_executor_(async_executor),
      metric_client_(metric_client),
      metric_info_(move(metric_info)),
      push_interval_duration_in_ms_(push_interval_duration_in_ms),
      average_duration_counter_(make_pair(0, 0)),
      is_running_(false),
      can_accept_incoming_increments_(false),
      object_activity_id_(Uuid::GenerateUuid()) {
  for (const auto& event_code : event_code_labels_list) {
    map<string, string> labels;
    labels[event_code_label_key] = event_code;

    // a copy of metric_info_
    auto event_metric = MetricDefinition(metric_info_);
    event_metric.AddMetricLabels(move(labels));

    event_counters_[event_code] = make_pair(0, 0);
    event_metric_infos_.insert(
        pair<string, MetricDefinition>(event_code, move(event_metric)));
  }
}

ExecutionResult TimeAggregateMetric::Init() noexcept {
  if (push_interval_duration_in_ms_ < kMinimumPushIntervalDuration) {
    return FailureExecutionResult(
        SC_CUSTOMIZED_METRIC_INVALID_AGGREGATION_INTERVAL_DURATION);
  }
  return SuccessExecutionResult();
}

ExecutionResult TimeAggregateMetric::Run() noexcept {
  if (is_running_) {
    return FailureExecutionResult(
        core::errors::SC_CUSTOMIZED_METRIC_ALREADY_RUNNING);
  }
  is_running_ = true;
  can_accept_incoming_increments_ = true;
  return ScheduleMetricPush();
}

ExecutionResult TimeAggregateMetric::Stop() noexcept {
  if (!is_running_) {
    return FailureExecutionResult(
        core::errors::SC_CUSTOMIZED_METRIC_NOT_RUNNING);
  }

  can_accept_incoming_increments_ = false;

  // Wait until all of the event counters are flushed.
  while (average_duration_counter_.first > 0) {
    SCP_DEBUG(kTimeAggregateMetric, object_activity_id_,
              "Waiting for the counter to be flushed. Current value '%llu'",
              average_duration_counter_.first);
    sleep_for(kStopWaitSleepDuration);
  }
  for (auto& [_, event_counter] : event_counters_) {
    while (event_counter.first > 0) {
      SCP_DEBUG(kTimeAggregateMetric, object_activity_id_,
                "Waiting for the counter to be flushed. Current value '%llu'",
                event_counter.first);
      sleep_for(kStopWaitSleepDuration);
    }
  }

  // Take the schedule mutex to disallow new tasks to be scheduled while
  // stopping
  task_schedule_mutex_.lock();
  is_running_ = false;
  task_schedule_mutex_.unlock();

  // At this point no more tasks can be scheduled, so it is safe to access this
  // 'current_cancellation_callback_' member, i.e. in other words, there is no
  // concurrent write on this while we are accessing it.

  if (current_cancellation_callback_) {
    current_cancellation_callback_();
  }
  return SuccessExecutionResult();
}

ExecutionResult TimeAggregateMetric::RecordDuration(
    const TimeDuration& duration_in_ms, const string& event_code) noexcept {
  if (!can_accept_incoming_increments_) {
    return FailureExecutionResult(
        core::errors::SC_CUSTOMIZED_METRIC_CANNOT_INCREMENT_WHEN_NOT_RUNNING);
  }

  std::unique_lock lock(record_mutex_);

  if (event_code.empty()) {
    average_duration_counter_.second = CalculateAverageDuration(
        average_duration_counter_.first, average_duration_counter_.second,
        duration_in_ms);
    average_duration_counter_.first++;
    return SuccessExecutionResult();
  }

  auto event = event_counters_.find(event_code);
  if (event == event_counters_.end()) {
    return FailureExecutionResult(SC_CUSTOMIZED_METRIC_EVENT_CODE_NOT_EXIST);
  }
  event->second.second = CalculateAverageDuration(
      event->second.first, event->second.second, duration_in_ms);
  event->second.first++;
  return SuccessExecutionResult();
}

void TimeAggregateMetric::MetricPushHandler(
    const AverageTimeDuration& average_time_duration,
    const MetricDefinition& metric_info) noexcept {
  auto metric_value = string(to_string(average_time_duration));
  auto record_metric_request = make_shared<PutMetricsRequest>();
  MetricUtils::GetPutMetricsRequest(record_metric_request, metric_info,
                                    metric_value);

  AsyncContext<PutMetricsRequest, PutMetricsResponse> record_metric_context(
      move(record_metric_request),
      [&](AsyncContext<PutMetricsRequest, PutMetricsResponse>& context) {
        if (!context.result.Successful()) {
          std::vector<string> metric_names;
          for (auto& metric : context.request->metrics()) {
            metric_names.push_back(metric.name());
          }
          // TODO: Create an alert or reschedule
          SCP_CRITICAL(kTimeAggregateMetric, object_activity_id_,
                       context.result,
                       "PutMetrics returned a failure for '%llu' metrics. The "
                       "metrics are: '%s'",
                       context.request->metrics().size(),
                       absl::StrJoin(metric_names, ", ").c_str());
        }
      },
      object_activity_id_, object_activity_id_);

  auto metrics_count = record_metric_context.request->metrics().size();
  metric_client_->PutMetrics(record_metric_context);
}

void TimeAggregateMetric::RunMetricPush() noexcept {
  std::unique_lock lock(record_mutex_);
  auto value = average_duration_counter_.second;
  if (value > 0) {
    MetricPushHandler(value, metric_info_);
    average_duration_counter_ = make_pair(0, 0);
  }

  for (auto event = event_counters_.begin(); event != event_counters_.end();
       ++event) {
    auto value = event->second.second;
    if (value > 0) {
      MetricPushHandler(value, event_metric_infos_.at(event->first));
      event->second = make_pair(0, 0);
    }
  }
  return;
}

ExecutionResult TimeAggregateMetric::ScheduleMetricPush() noexcept {
  std::unique_lock lock(task_schedule_mutex_);

  if (!is_running_) {
    return FailureExecutionResult(
        core::errors::SC_CUSTOMIZED_METRIC_NOT_RUNNING);
  }

  Timestamp next_push_time = (TimeProvider::GetSteadyTimestampInNanoseconds() +
                              milliseconds(push_interval_duration_in_ms_))
                                 .count();
  auto execution_result = async_executor_->ScheduleFor(
      [this]() {
        RunMetricPush();
        if (auto execution_result = ScheduleMetricPush();
            !execution_result.Successful()) {
          // TODO: Create an alert or reschedule
          SCP_EMERGENCY(kTimeAggregateMetric, object_activity_id_,
                        execution_result,
                        "Cannot schedule PutMetrics on AsyncExecutor. There "
                        "will be a metrics loss after this since no more "
                        "pushes will be done.");
        }
      },
      next_push_time, current_cancellation_callback_);
  if (!execution_result.Successful()) {
    return FailureExecutionResult(SC_CUSTOMIZED_METRIC_PUSH_CANNOT_SCHEDULE);
  }

  return SuccessExecutionResult();
}

}  // namespace google::scp::cpio
