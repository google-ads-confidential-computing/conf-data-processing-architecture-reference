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

#pragma once

#include <memory>
#include <string>
#include <vector>

#include "core/interface/async_context.h"
#include "cpio/client_providers/interface/instance_client_provider_interface.h"
#include "cpio/client_providers/otel_metric_client_provider/src/otel_metric_client_provider.h"
#include "opentelemetry/sdk/metrics/meter.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/metric_client/type_def.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"

namespace google::scp::cpio::client_providers {
/*! @copydoc OtelMetricClientProvider
 */
class AwsOtelMetricClientProvider : public OtelMetricClientProvider {
 public:
  /**
   * @brief Constructs a new AWS OpenTelemetry Metric Client Provider.
   *
   * @param metric_client_options the configurations for Metric Client.
   * @param instance_client_provider the Instance Client Provider.
   */
  explicit AwsOtelMetricClientProvider(
      const std::shared_ptr<MetricClientOptions>& metric_client_options,
      const std::shared_ptr<InstanceClientProviderInterface>&
          instance_client_provider,
      const std::shared_ptr<opentelemetry::metrics::Meter>& otel_meter)
      : OtelMetricClientProvider(metric_client_options,
                                 instance_client_provider, otel_meter) {}

  AwsOtelMetricClientProvider() = delete;

  core::ExecutionResult Run() noexcept override;
};
}  // namespace google::scp::cpio::client_providers
