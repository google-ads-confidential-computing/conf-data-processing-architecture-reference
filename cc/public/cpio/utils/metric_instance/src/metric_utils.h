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

#include <chrono>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include <google/protobuf/map.h>
#include <google/protobuf/util/time_util.h>

#include "core/interface/async_executor_interface.h"
#include "google/cloud/status.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"
#include "public/cpio/utils/metric_instance/interface/aggregate_metric_interface.h"
#include "public/cpio/utils/metric_instance/interface/type_def.h"
#include "public/cpio/utils/metric_instance/src/aggregate_metric.h"
#include "public/cpio/utils/metric_instance/src/simple_metric.h"

namespace google::scp::cpio {
class MetricUtils {
 public:
  /**
   * @brief Get the PutMetricsRequest protobuf object.
   *
   * @param[out] record_metric_request
   * @param metric_info The metric definition including name, unit, and labels.
   * @param metric_value The value of the metric.
   */
  static void GetPutMetricsRequest(
      std::shared_ptr<cmrt::sdk::metric_service::v1::PutMetricsRequest>&
          record_metric_request,
      const MetricDefinition& metric_info,
      const std::string& metric_value) noexcept;

  /**
   * @brief Create a Metric Labels With Component Signature object
   *
   * @param component_name the component name value for metric label
   * `ComponentName`.
   * @param method_name the method name value for metric label `MethodName`.
   * @return map<string, string> a map of metric labels.
   */
  static std::map<std::string, std::string>
  CreateMetricLabelsWithComponentSignature(
      std::string component_name,
      std::string method_name = std::string()) noexcept;

  // Converts a MetricDefinition object into a Metric object with the given
  // MetricType.
  static cmrt::sdk::metric_service::v1::Metric ConvertMetricDefinitionToMetric(
      const MetricDefinition& metric_definition,
      cmrt::sdk::metric_service::v1::MetricType type);

  static constexpr char kEventCodeLabelKey[] = "EventCode";

  // Creates a map from the event codes in event_code_labels_list to instances
  // of Metrics. The returned Metric's are copies of metric_definition with the
  // label properly applied.
  static absl::flat_hash_map<std::string, cmrt::sdk::metric_service::v1::Metric>
  MakeMetricsForEventCodes(
      const MetricDefinition& metric_definition,
      cmrt::sdk::metric_service::v1::MetricType metric_type,
      const std::vector<std::string> event_code_labels_list,
      const std::string& event_code_name = kEventCodeLabelKey);

  /**
   * @brief Push the GCP KMS Decryption Error Rate Metric.
   *
   * @param metric_client The metric client.
   * @param status The GCP status returned from KMS decryption operation.
   */
  static void PushGcpKmsDecryptionErrorRateMetric(
      const std::shared_ptr<MetricClientInterface> metric_client,
      const google::cloud::Status status) noexcept;

  /**
   * @brief Push the given metric using the given metric client.
   *
   * @param metric_client The metric client.
   * @param metric The metric to push.
   */
  static core::ExecutionResult PutMetric(
      const std::shared_ptr<MetricClientInterface> metric_client,
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept;
};

}  // namespace google::scp::cpio
