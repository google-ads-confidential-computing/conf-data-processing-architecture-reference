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

#include <memory>
#include <string>

#include "cpio/client_providers/otel_metric_client_provider/src/aws/aws_otel_metric_client_provider.h"
#include "opentelemetry/sdk/metrics/meter.h"

namespace google::scp::cpio::client_providers {
/*! @copydoc AwsMetricClientProvider
 */
class TestAwsOtelMetricClientProvider : public AwsOtelMetricClientProvider {
 public:
  explicit TestAwsOtelMetricClientProvider(
      const std::shared_ptr<MetricClientOptions>& metric_client_options,
      const std::shared_ptr<InstanceClientProviderInterface>&
          instance_client_provider,
      const std::shared_ptr<opentelemetry::metrics::Meter>& otel_meter)
      : AwsOtelMetricClientProvider(metric_client_options,
                                    instance_client_provider, otel_meter) {}
};
}  // namespace google::scp::cpio::client_providers
