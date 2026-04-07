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
#include <vector>

#include "absl/container/flat_hash_map.h"
#include "public/cpio/interface/metric_client/metric_client_interface.h"
#include "public/cpio/utils/dual_writing_metric_client/interface/dual_writing_metric_client_interface.h"
#include "public/cpio/utils/metric_instance/interface/metric_instance_factory_interface.h"

namespace google::scp::cpio {

class DualWritingMetricClient : public DualWritingMetricClientInterface {
 public:
  // At least one of otel_metric_client and metric_instance_factory
  // must be provided. Both may be provided to record metrics to both.
  DualWritingMetricClient(
      MetricClientInterface* otel_metric_client,
      MetricInstanceFactoryInterface* metric_instance_factory,
      std::string_view otel_namespace);

  ~DualWritingMetricClient();

  MetricWrapper CreateAggregateMetric(
      MetricDefinition metric_definition) noexcept override;
  MetricWrapper CreateAggregateMetric(
      MetricDefinition metric_definition,
      const std::vector<std::string>& event_code_labels_list) noexcept override;

  MetricWrapper CreateTimeAggregateMetric(
      MetricDefinition metric_definition) noexcept override;
  MetricWrapper CreateTimeAggregateMetric(
      MetricDefinition metric_definition,
      const std::vector<std::string>& event_code_labels_list) noexcept override;

  core::ExecutionResult InitMetric(
      std::string_view metric_name, std::string_view component_name,
      std::string_view method_name) noexcept override;
  core::ExecutionResult RunMetric(
      std::string_view metric_name, std::string_view component_name,
      std::string_view method_name) noexcept override;
  core::ExecutionResult StopMetric(
      std::string_view metric_name, std::string_view component_name,
      std::string_view method_name) noexcept override;

  core::ExecutionResult PutMetric(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept
      override;
  core::ExecutionResult PutOtelMetric(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept
      override;

 private:
  MetricClientInterface* otel_metric_client_;
  MetricInstanceFactoryInterface* metric_instance_factory_;
  std::string otel_namespace_;

  absl::flat_hash_map<std::string, std::unique_ptr<AggregateMetricInterface>>
      aggregate_metrics_;
  absl::flat_hash_map<std::string,
                      std::unique_ptr<TimeAggregateMetricInterface>>
      time_aggregate_metrics_;
  absl::flat_hash_map<std::string, std::optional<std::string>>
      metric_name_to_namespace_;
};

}  // namespace google::scp::cpio
