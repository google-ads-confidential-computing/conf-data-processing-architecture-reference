/*
 * Copyright 2025 Google LLC
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

#include "aws_otel_metric_client_provider.h"

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "core/common/uuid/src/uuid.h"
#include "cpio/client_providers/instance_client_provider/src/aws/aws_instance_client_utils.h"
#include "cpio/client_providers/interface/otel_metric_client_provider_interface.h"
#include "cpio/client_providers/otel_metric_client_provider/src/opentelemetry_utils.h"
#include "cpio/common/src/common_error_codes.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/metric_client/type_def.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"

using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::core::errors::SC_COMMON_ERRORS_UNIMPLEMENTED;
using opentelemetry::sdk::resource::Resource;
using std::make_shared;
using std::shared_ptr;
using std::string;
using std::vector;

static constexpr char kAwsOtelMetricClientProvider[] =
    "AwsOtelMetricClientProvider";

namespace google::scp::cpio::client_providers {

ExecutionResult AwsOtelMetricClientProvider::Run() noexcept {
  auto failure_exec_result =
      FailureExecutionResult(SC_COMMON_ERRORS_UNIMPLEMENTED);
  SCP_ERROR(kAwsOtelMetricClientProvider, kZeroUuid, failure_exec_result,
            "Running AWS OpenTelemetry Metric Client Provider is not yet "
            "supported.");
  return failure_exec_result;
}

#ifndef TEST_CPIO
std::shared_ptr<MetricClientInterface> OtelMetricClientProviderFactory::Create(
    const shared_ptr<MetricClientOptions>& options,
    const shared_ptr<InstanceClientProviderInterface>&
        instance_client_provider) {
  return make_shared<AwsOtelMetricClientProvider>(
      options, instance_client_provider,
      OpenTelemetryUtils::CreateOpenTelemetryMeter(options));
}
#endif
}  // namespace google::scp::cpio::client_providers
