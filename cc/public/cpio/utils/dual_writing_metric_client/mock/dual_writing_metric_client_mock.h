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

#include <gmock/gmock.h>

#include <string>
#include <string_view>
#include <vector>

#include "public/cpio/utils/dual_writing_metric_client/interface/dual_writing_metric_client_interface.h"

namespace google::scp::cpio {

class DualWritingMetricClientMock : public DualWritingMetricClientInterface {
 public:
  DualWritingMetricClientMock() {
    ON_CALL(*this, CreateAggregateMetric(testing::_))
        .WillByDefault(testing::Return(MetricWrapper()));
    ON_CALL(*this, CreateAggregateMetric(testing::_, testing::_))
        .WillByDefault(testing::Return(MetricWrapper()));

    ON_CALL(*this, CreateTimeAggregateMetric(testing::_))
        .WillByDefault(testing::Return(MetricWrapper()));

    ON_CALL(*this, CreateTimeAggregateMetric(testing::_, testing::_))
        .WillByDefault(testing::Return(MetricWrapper()));

    ON_CALL(*this, InitMetric)
        .WillByDefault(testing::Return(core::SuccessExecutionResult()));
    ON_CALL(*this, RunMetric)
        .WillByDefault(testing::Return(core::SuccessExecutionResult()));
    ON_CALL(*this, StopMetric)
        .WillByDefault(testing::Return(core::SuccessExecutionResult()));

    ON_CALL(*this, PutMetric)
        .WillByDefault(testing::Return(core::SuccessExecutionResult()));
    ON_CALL(*this, PutOtelMetric)
        .WillByDefault(testing::Return(core::SuccessExecutionResult()));
  }

  MOCK_METHOD(MetricWrapper, CreateAggregateMetric, (MetricDefinition),
              (noexcept, override));
  MOCK_METHOD(MetricWrapper, CreateAggregateMetric,
              (MetricDefinition, const std::vector<std::string>&),
              (noexcept, override));

  MOCK_METHOD(MetricWrapper, CreateTimeAggregateMetric, (MetricDefinition),
              (noexcept, override));
  MOCK_METHOD(MetricWrapper, CreateTimeAggregateMetric,
              (MetricDefinition, const std::vector<std::string>&),
              (noexcept, override));

  MOCK_METHOD(core::ExecutionResult, InitMetric,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(core::ExecutionResult, RunMetric,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(core::ExecutionResult, StopMetric,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));

  MOCK_METHOD(core::ExecutionResult, PutMetric,
              (const google::cmrt::sdk::metric_service::v1::Metric&),
              (noexcept, override));
  MOCK_METHOD(core::ExecutionResult, PutOtelMetric,
              (const google::cmrt::sdk::metric_service::v1::Metric&),
              (noexcept, override));
};

}  // namespace google::scp::cpio
