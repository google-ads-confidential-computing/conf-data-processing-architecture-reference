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

#include "metric_utils.h"

#include <map>
#include <string_view>
#include <utility>

#include "absl/container/flat_hash_map.h"

using google::cmrt::sdk::metric_service::v1::Metric;
using google::cmrt::sdk::metric_service::v1::MetricType;
using std::make_shared;
using std::map;
using std::move;
using std::shared_ptr;
using std::string;
using std::string_view;
using std::vector;

namespace {
constexpr char kMethodName[] = "MethodName";
constexpr char kComponentName[] = "ComponentName";

}  // namespace

namespace google::scp::cpio {

void MetricUtils::GetPutMetricsRequest(
    shared_ptr<cmrt::sdk::metric_service::v1::PutMetricsRequest>&
        record_metric_request,
    const MetricDefinition& metric_info, const string& metric_value) noexcept {
  auto metric = record_metric_request->add_metrics();
  metric->set_value(metric_value);
  metric->set_name(metric_info.name);
  metric->set_unit(metric_info.unit);

  // Adds the labels from metric_info and additional_labels.
  auto labels = metric->mutable_labels();
  if (metric_info.labels.size() > 0) {
    for (const auto& label : metric_info.labels) {
      labels->insert(
          protobuf::MapPair<string, string>(label.first, label.second));
    }
  }

  *metric->mutable_timestamp() = protobuf::util::TimeUtil::GetCurrentTime();

  if (metric_info.metric_namespace.has_value()) {
    record_metric_request->set_metric_namespace(
        metric_info.metric_namespace.value());
  }
}

map<string, string> MetricUtils::CreateMetricLabelsWithComponentSignature(
    string component_name, string method_name) noexcept {
  map<string, string> labels;
  labels[kComponentName] = move(component_name);
  if (!method_name.empty()) {
    labels[kMethodName] = move(method_name);
  }

  return labels;
}

Metric MetricUtils::ConvertMetricDefinitionToMetric(
    const MetricDefinition& metric_definition, MetricType type) {
  Metric metric;
  metric.set_name(metric_definition.name);
  metric.set_unit(metric_definition.unit);
  metric.set_type(type);
  for (const auto& [k, v] : metric_definition.labels) {
    metric.mutable_labels()->emplace(k, v);
  }
  return metric;
}

absl::flat_hash_map<string, Metric> MetricUtils::MakeMetricsForEventCodes(
    const MetricDefinition& metric_definition, MetricType metric_type,
    const vector<string> event_code_labels_list,
    const string& event_code_name) {
  absl::flat_hash_map<string, Metric> metrics;
  for (string_view event_code : event_code_labels_list) {
    MetricDefinition labeled_definition(metric_definition);
    labeled_definition.labels[event_code_name] = event_code;
    metrics[event_code] =
        ConvertMetricDefinitionToMetric(labeled_definition, metric_type);
  }
  return metrics;
}

}  // namespace google::scp::cpio
