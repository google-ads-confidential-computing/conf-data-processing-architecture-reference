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

#include <string>
#include <vector>

#include "public/core/interface/execution_result.h"
#include "public/cpio/utils/dual_writing_metric_client/interface/dual_writing_metric_client_interface.h"

namespace google::scp::cpio {

class NoopDualWritingMetricClient : public DualWritingMetricClientInterface {
 public:
  MetricWrapper CreateAggregateMetric(
      google::scp::cpio::MetricDefinition metric_definition) noexcept override {
    return MetricWrapper();
  }

  MetricWrapper CreateAggregateMetric(
      google::scp::cpio::MetricDefinition metric_definition,
      const std::vector<std::string>& event_code_labels_list) noexcept
      override {
    return MetricWrapper();
  }

  MetricWrapper CreateTimeAggregateMetric(
      google::scp::cpio::MetricDefinition metric_definition) noexcept override {
    return MetricWrapper();
  }

  MetricWrapper CreateTimeAggregateMetric(
      google::scp::cpio::MetricDefinition metric_definition,
      const std::vector<std::string>& event_code_labels_list) noexcept
      override {
    return MetricWrapper();
  }

  core::ExecutionResult InitMetric(
      std::string_view metric_name, std::string_view component_name,
      std::string_view method_name) noexcept override {
    return core::SuccessExecutionResult();
  }

  core::ExecutionResult RunMetric(
      std::string_view metric_name, std::string_view component_name,
      std::string_view method_name) noexcept override {
    return core::SuccessExecutionResult();
  }

  core::ExecutionResult StopMetric(
      std::string_view metric_name, std::string_view component_name,
      std::string_view method_name) noexcept override {
    return core::SuccessExecutionResult();
  }

  core::ExecutionResult PutMetric(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept
      override {
    return core::SuccessExecutionResult();
  }

  core::ExecutionResult PutOtelMetric(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept
      override {
    return core::SuccessExecutionResult();
  }
};

}  // namespace google::scp::cpio
