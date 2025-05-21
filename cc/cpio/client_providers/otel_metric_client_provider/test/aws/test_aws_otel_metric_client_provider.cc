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

#include "test_aws_otel_metric_client_provider.h"

#include <memory>
#include <string>

#include "cpio/client_providers/interface/otel_metric_client_provider_interface.h"
#include "cpio/client_providers/otel_metric_client_provider/src/opentelemetry_utils.h"
#include "opentelemetry/sdk/metrics/meter.h"
#include "public/cpio/interface/metric_client/metric_client_interface.h"

using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResult;
using opentelemetry::metrics::Meter;
using std::make_shared;
using std::shared_ptr;
using std::string;

namespace google::scp::cpio::client_providers {

shared_ptr<MetricClientInterface> OtelMetricClientProviderFactory::Create(
    const shared_ptr<MetricClientOptions>& options,
    const shared_ptr<InstanceClientProviderInterface>&
        instance_client_provider) {
  return make_shared<TestAwsOtelMetricClientProvider>(
      std::dynamic_pointer_cast<MetricClientOptions>(options),
      instance_client_provider,
      OpenTelemetryUtils::CreateOpenTelemetryMeter(options, "", ""));
}
}  // namespace google::scp::cpio::client_providers
