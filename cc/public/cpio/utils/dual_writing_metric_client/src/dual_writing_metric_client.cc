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

#include "dual_writing_metric_client.h"

#include <iostream>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "absl/strings/str_format.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/core/interface/execution_result_or_macros.h"
#include "public/cpio/interface/metric_client/type_def.h"
#include "public/cpio/utils/metric_instance/src/metric_utils.h"

#include "error_codes.h"

using google::cmrt::sdk::metric_service::v1::Metric;
using google::cmrt::sdk::metric_service::v1::MetricType;
using google::cmrt::sdk::metric_service::v1::MetricUnit;
using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using std::cerr;
using std::endl;
using std::make_shared;
using std::nullopt;
using std::optional;
using std::stoull;
using std::string;
using std::string_view;
using std::vector;

namespace google::scp::cpio {
namespace {

constexpr char kDualWritingMetricClientName[] = "DualWritingMetricClient";
constexpr char kEventCodeName[] = "EventCode";

constexpr char kComponentNameLabel[] = "ComponentName";
constexpr char kMethodNameLabel[] = "MethodName";

string GetMetricIdentifier(string_view metric_name, string_view component_name,
                           string_view method_name) {
  return absl::StrFormat("%s.%s.%s", metric_name, component_name, method_name);
}

string GetMetricIdentifier(const Metric& metric) {
  string_view component_name;
  if (metric.labels().contains(kComponentNameLabel)) {
    component_name = metric.labels().at(kComponentNameLabel);
  }
  string_view method_name;
  if (metric.labels().contains(kMethodNameLabel)) {
    method_name = metric.labels().at(kMethodNameLabel);
  }
  return GetMetricIdentifier(metric.name(), component_name, method_name);
}

string FindEventCodeIfPresent(const Metric& metric) {
  for (const auto& [key, label] : metric.labels()) {
    if (key == kEventCodeName) {
      return label;
    }
  }
  return "";
}

}  // namespace

DualWritingMetricClient::DualWritingMetricClient(
    MetricClientInterface* otel_metric_client,
    MetricInstanceFactoryInterface* metric_instance_factory,
    string_view otel_namespace)
    : otel_metric_client_(otel_metric_client),
      metric_instance_factory_(metric_instance_factory),
      otel_namespace_(otel_namespace) {
  if (otel_metric_client_ == nullptr && metric_instance_factory_ == nullptr) {
    cerr << "DualWritingMetricClient needs at least one of an OtelMetricClient "
            "and a MetricInstanceFactory"
         << endl;
    exit(EXIT_FAILURE);
  }
}

DualWritingMetricClient::~DualWritingMetricClient() {
  for (const auto& [metric_id, metric] : aggregate_metrics_) {
    LOG_IF_FAILURE(metric->Stop(), kDualWritingMetricClientName, kZeroUuid,
                   "Failed to stop Metric %s", metric_id.c_str());
  }
  for (const auto& [metric_id, metric] : time_aggregate_metrics_) {
    LOG_IF_FAILURE(metric->Stop(), kDualWritingMetricClientName, kZeroUuid,
                   "Failed to stop Metric %s", metric_id.c_str());
  }
}

MetricWrapper DualWritingMetricClient::CreateAggregateMetric(
    MetricDefinition metric_definition) noexcept {
  auto metric = MetricWrapper(MetricUtils::ConvertMetricDefinitionToMetric(
      metric_definition, MetricType::METRIC_TYPE_COUNTER));
  if (metric_instance_factory_) {
    if (aggregate_metrics_.contains(GetMetricIdentifier(metric.metric))) {
      SCP_WARNING(kDualWritingMetricClientName, kZeroUuid,
                  "AggregateMetric [%s] already exists in the "
                  "DualWriteMetricClient. The old one is being destroyed.",
                  metric.metric.ShortDebugString().c_str());
    }
    aggregate_metrics_[GetMetricIdentifier(metric.metric)] =
        metric_instance_factory_->ConstructAggregateMetricInstance(
            std::move(metric_definition));
  }
  return metric;
}

MetricWrapper DualWritingMetricClient::CreateAggregateMetric(
    MetricDefinition metric_definition,
    const vector<string>& event_code_labels_list) noexcept {
  auto metric = MetricWrapper(MetricUtils::ConvertMetricDefinitionToMetric(
      metric_definition, MetricType::METRIC_TYPE_COUNTER));
  if (metric_instance_factory_) {
    if (aggregate_metrics_.contains(GetMetricIdentifier(metric.metric))) {
      SCP_WARNING(kDualWritingMetricClientName, kZeroUuid,
                  "AggregateMetric [%s] already exists in the "
                  "DualWriteMetricClient. The old one is being destroyed.",
                  metric.metric.ShortDebugString().c_str());
    }
    aggregate_metrics_[GetMetricIdentifier(metric.metric)] =
        metric_instance_factory_->ConstructAggregateMetricInstance(
            std::move(metric_definition), event_code_labels_list);
  }
  return metric;
}

MetricWrapper DualWritingMetricClient::CreateTimeAggregateMetric(
    MetricDefinition metric_definition) noexcept {
  auto metric = MetricWrapper(MetricUtils::ConvertMetricDefinitionToMetric(
      metric_definition, MetricType::METRIC_TYPE_HISTOGRAM));
  if (metric_instance_factory_) {
    if (time_aggregate_metrics_.contains(GetMetricIdentifier(metric.metric))) {
      SCP_WARNING(kDualWritingMetricClientName, kZeroUuid,
                  "TimeAggregateMetric [%s] already exists in the "
                  "DualWriteMetricClient. The old one is being destroyed.",
                  metric.metric.ShortDebugString().c_str());
    }
    time_aggregate_metrics_[GetMetricIdentifier(metric.metric)] =
        metric_instance_factory_->ConstructTimeAggregateMetricInstance(
            std::move(metric_definition));
  }
  return metric;
}

MetricWrapper DualWritingMetricClient::CreateTimeAggregateMetric(
    MetricDefinition metric_definition,
    const vector<string>& event_code_labels_list) noexcept {
  auto metric = MetricWrapper(MetricUtils::ConvertMetricDefinitionToMetric(
      metric_definition, MetricType::METRIC_TYPE_HISTOGRAM));
  if (metric_instance_factory_) {
    if (time_aggregate_metrics_.contains(GetMetricIdentifier(metric.metric))) {
      SCP_WARNING(kDualWritingMetricClientName, kZeroUuid,
                  "TimeAggregateMetric [%s] already exists in the "
                  "DualWriteMetricClient. The old one is being destroyed.",
                  metric.metric.ShortDebugString().c_str());
    }
    time_aggregate_metrics_[GetMetricIdentifier(metric.metric)] =
        metric_instance_factory_->ConstructTimeAggregateMetricInstance(
            std::move(metric_definition), event_code_labels_list);
  }
  return metric;
}

core::ExecutionResult DualWritingMetricClient::InitMetric(
    string_view metric_name, string_view component_name,
    string_view method_name) noexcept {
  auto metric_identifier =
      GetMetricIdentifier(metric_name, component_name, method_name);
  if (auto it = aggregate_metrics_.find(metric_identifier);
      it != aggregate_metrics_.end()) {
    return it->second->Init();
  }
  if (auto it = time_aggregate_metrics_.find(metric_identifier);
      it != time_aggregate_metrics_.end()) {
    return it->second->Init();
  }
  if (metric_instance_factory_) {
    // Metric not found when legacy metrics are enabled.
    auto result = FailureExecutionResult(SC_CPIO_METRIC_NOT_FOUND);
    SCP_ERROR(kDualWritingMetricClientName, kZeroUuid, result,
              "Tried to Init a metric (%s) that was not found",
              metric_identifier.c_str());
    return result;
  }
  return SuccessExecutionResult();
}

core::ExecutionResult DualWritingMetricClient::RunMetric(
    string_view metric_name, string_view component_name,
    string_view method_name) noexcept {
  auto metric_identifier =
      GetMetricIdentifier(metric_name, component_name, method_name);
  if (auto it = aggregate_metrics_.find(metric_identifier);
      it != aggregate_metrics_.end()) {
    return it->second->Run();
  }
  if (auto it = time_aggregate_metrics_.find(metric_identifier);
      it != time_aggregate_metrics_.end()) {
    return it->second->Run();
  }
  if (metric_instance_factory_) {
    // Metric not found when legacy metrics are enabled.
    auto result = FailureExecutionResult(SC_CPIO_METRIC_NOT_FOUND);
    SCP_ERROR(kDualWritingMetricClientName, kZeroUuid, result,
              "Tried to Run a metric (%s) that was not found",
              metric_identifier.c_str());
    return result;
  }
  return SuccessExecutionResult();
}

core::ExecutionResult DualWritingMetricClient::StopMetric(
    string_view metric_name, string_view component_name,
    string_view method_name) noexcept {
  auto metric_identifier =
      GetMetricIdentifier(metric_name, component_name, method_name);
  if (auto it = aggregate_metrics_.find(metric_identifier);
      it != aggregate_metrics_.end()) {
    auto res = it->second->Stop();
    aggregate_metrics_.erase(it);
    return res;
  }
  if (auto it = time_aggregate_metrics_.find(metric_identifier);
      it != time_aggregate_metrics_.end()) {
    auto res = it->second->Stop();
    time_aggregate_metrics_.erase(it);
    return res;
  }
  if (metric_instance_factory_) {
    // Metric not found when legacy metrics are enabled.
    auto result = FailureExecutionResult(SC_CPIO_METRIC_NOT_FOUND);
    SCP_ERROR(kDualWritingMetricClientName, kZeroUuid, result,
              "Tried to Stop a metric (%s) that was not found",
              metric_identifier.c_str());
    return result;
  }
  return SuccessExecutionResult();
}

core::ExecutionResult DualWritingMetricClient::PutMetric(
    const Metric& metric) noexcept {
  string metric_id = GetMetricIdentifier(metric);
  core::ExecutionResult instance_factory_result = SuccessExecutionResult();
  if (metric_instance_factory_) {
    string event_code = FindEventCodeIfPresent(metric);
    // Find the metric in one of the maps.
    if (auto it = aggregate_metrics_.find(metric_id);
        it != aggregate_metrics_.end()) {
      auto& metric_instance = it->second;
      uint64_t number_value = stoull(metric.value());
      instance_factory_result =
          metric_instance->IncrementBy(number_value, event_code);
    } else if (auto it = time_aggregate_metrics_.find(metric_id);
               it != time_aggregate_metrics_.end()) {
      auto& metric_instance = it->second;
      instance_factory_result =
          metric_instance->RecordDuration(stoull(metric.value()), event_code);
    } else {
      auto result = FailureExecutionResult(SC_CPIO_METRIC_NOT_FOUND);
      SCP_ERROR(kDualWritingMetricClientName, kZeroUuid, result,
                "Failed to find metric %s to record", metric_id.c_str());
      return result;
    }
  }
  core::ExecutionResult otel_result = SuccessExecutionResult();
  if (otel_metric_client_) {
    otel_result = PutOtelMetric(metric);
  }
  RETURN_IF_FAILURE(otel_result);
  return instance_factory_result;
}

core::ExecutionResult DualWritingMetricClient::PutOtelMetric(
    const Metric& metric) noexcept {
  if (!otel_metric_client_) {
    return SuccessExecutionResult();
  }
  PutMetricsRequest request;
  request.set_metric_namespace(otel_namespace_);
  *request.add_metrics() = metric;
  return otel_metric_client_->PutMetricsSync(request).result();
}

}  // namespace google::scp::cpio
