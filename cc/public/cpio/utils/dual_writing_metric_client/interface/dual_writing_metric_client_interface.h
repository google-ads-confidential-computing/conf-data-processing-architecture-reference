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

#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "absl/container/flat_hash_map.h"
#include "absl/strings/str_cat.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"
#include "public/cpio/utils/metric_instance/interface/metric_instance_factory_interface.h"
#include "public/cpio/utils/metric_instance/interface/type_def.h"

namespace google::scp::cpio {

class MetricWrapper {
 public:
  explicit MetricWrapper(cmrt::sdk::metric_service::v1::Metric metric)
      : metric(std::move(metric)) {}

  MetricWrapper() = default;

  cmrt::sdk::metric_service::v1::Metric Increment(
      uint64_t value = 1,
      std::optional<std::string_view> event_code = std::nullopt) {
    return event_code.has_value() ? WithValue(absl::StrCat(value), *event_code)
                                  : WithValue(absl::StrCat(value));
  }

  cmrt::sdk::metric_service::v1::Metric WithValue(std::string_view value) {
    cmrt::sdk::metric_service::v1::Metric ret(metric);
    ret.set_value(value);
    return ret;
  }

  cmrt::sdk::metric_service::v1::Metric WithValue(std::string_view value,
                                                  std::string_view event_code) {
    auto ret = WithValue(value);
    (*ret.mutable_labels())[kEventCodeLabelKey] = event_code;
    return ret;
  }

  cmrt::sdk::metric_service::v1::Metric metric;

 private:
  static constexpr char kEventCodeLabelKey[] = "EventCode";
};

// This class is used when tracking metrics in both OpenTelemetry and/or GCP
// required based on configuration.
class DualWritingMetricClientInterface {
 public:
  virtual ~DualWritingMetricClientInterface() = default;

  // The below methods all perform the necessary steps to construct a metric and
  // track it internally.
  // The returned Metric objects are returned so clients can easily just copy
  // the Metric, fill in the value field, and call PutMetrics with it.

  ///////////////////// AggregateMetrics //////////////////////////////////////

  /**
   * @brief Construct an AggregateMetric. The AggregateMetric will
   * track a specific set of event metrics, which are defined by the
   * metric_definition and event_code_labels_list.
   *
   * @param metric_definition the basic metric information for the aggregate
   * metric instance. event_name in AggregateMetric will be used.
   * @return A base Metric template to use when putting metrics.
   */
  virtual MetricWrapper CreateAggregateMetric(
      google::scp::cpio::MetricDefinition metric_definition) noexcept = 0;
  /**
   * @brief Construct an AggregateMetric. The AggregateMetric will
   * track a specific set of event metrics, which are defined by the
   * metric_definition and event_code_labels_list.
   *
   * @param metric_definition the basic metric information for the aggregate
   * metric instance.
   * @param event_code_labels_list The event labels associated with the set of
   * metrics, where each metric has a unique event label.
   * @return A base Metric template to use when putting metrics.
   */
  virtual MetricWrapper CreateAggregateMetric(
      google::scp::cpio::MetricDefinition metric_definition,
      const std::vector<std::string>& event_code_labels_list) noexcept = 0;

  ///////////////////// TimeAggregateMetrics //////////////////////////////////

  /**
   * @brief Construct n TimeAggregateMetric. The TimeAggregateMetric
   * will track a specific set of event metrics, which are defined by the
   * metric_definition and event_code_labels_list.
   *
   * @param metric_definition the basic metric information for the aggregate
   * metric instance.
   * @return A base Metric template to use when putting metrics.
   */
  virtual MetricWrapper CreateTimeAggregateMetric(
      google::scp::cpio::MetricDefinition metric_definition) noexcept = 0;

  /**
   * @brief Construct n TimeAggregateMetric. The TimeAggregateMetric
   * will track a specific set of event metrics, which are defined by the
   * metric_definition and event_code_labels_list.
   *
   * @param metric_definition the basic metric information for the aggregate
   * metric instance.
   * @param event_code_labels_list The event labels associated with the set of
   * metrics, where each metric has a unique event label.
   * @return A base Metric template to use when putting metrics.
   */
  virtual MetricWrapper CreateTimeAggregateMetric(
      google::scp::cpio::MetricDefinition metric_definition,
      const std::vector<std::string>& event_code_labels_list) noexcept = 0;

  // The below methods are for Init, Run, and Stop-ing the internally tracked
  // metrics.

  // component_name and method_name for these methods are used as identifiers IF
  // the metrics created above have "MethodName" and "ComponentName" labels (e.g
  // the MetricDefinition is created with labels from
  // CreateMetricLabelsWithComponentSignature). Otherwise, the empty string can
  // be used.
  virtual core::ExecutionResult InitMetric(
      std::string_view metric_name, std::string_view component_name,
      std::string_view method_name) noexcept = 0;
  virtual core::ExecutionResult RunMetric(
      std::string_view metric_name, std::string_view component_name,
      std::string_view method_name) noexcept = 0;
  virtual core::ExecutionResult StopMetric(
      std::string_view metric_name, std::string_view component_name,
      std::string_view method_name) noexcept = 0;

  // Updates the corresponding metrics (in both OpenTelemetry and GCP).
  virtual core::ExecutionResult PutMetric(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept = 0;

  // Same as above, but for when updating a metric for which there is no GCM
  // equivalent (no need to use Create...Metric for any of these).
  // e.g. wanting 2 levels of labels to be applied (Application and Operation)
  // If Otel client is not provided for this instance, this function will just
  // return success.
  virtual core::ExecutionResult PutOtelMetric(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept = 0;
};

}  // namespace google::scp::cpio
