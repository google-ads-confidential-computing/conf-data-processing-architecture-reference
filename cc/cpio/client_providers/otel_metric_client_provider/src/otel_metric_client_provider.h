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

#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "core/interface/async_context.h"
#include "cpio/client_providers/interface/instance_client_provider_interface.h"
#include "cpio/client_providers/interface/metric_client_provider_interface.h"
#include "opentelemetry/sdk/metrics/meter.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/metric_client/metric_client_interface.h"

namespace google::scp::cpio::client_providers {
/*! @copydoc OtelMetricClientInterface
 */
class OtelMetricClientProvider : public MetricClientInterface {
 public:
  explicit OtelMetricClientProvider(
      const std::shared_ptr<MetricClientOptions>& metric_client_options,
      const std::shared_ptr<InstanceClientProviderInterface>&
          instance_client_provider,
      const std::shared_ptr<opentelemetry::metrics::Meter>& otel_meter)
      : metric_client_options_(metric_client_options),
        instance_client_provider_(instance_client_provider),
        otel_meter_(otel_meter) {}

  virtual ~OtelMetricClientProvider() = default;
  core::ExecutionResult Init() noexcept override;

  core::ExecutionResult Run() noexcept override;

  core::ExecutionResult Stop() noexcept override;

  void PutMetrics(
      core::AsyncContext<cmrt::sdk::metric_service::v1::PutMetricsRequest,
                         cmrt::sdk::metric_service::v1::PutMetricsResponse>&
          record_metric_context) noexcept override;

  core::ExecutionResultOr<
      google::cmrt::sdk::metric_service::v1::PutMetricsResponse>
  PutMetricsSync(google::cmrt::sdk::metric_service::v1::PutMetricsRequest
                     request) noexcept override;

 protected:
  core::ExecutionResult ValidateRequest(
      const google::cmrt::sdk::metric_service::v1::PutMetricsRequest&
          request) noexcept;

  void RecordMetric(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept;

  opentelemetry::metrics::Counter<double>* GetOrCreateCounter(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept;

  opentelemetry::metrics::Gauge<double>* GetOrCreateGauge(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept;

  opentelemetry::metrics::Histogram<double>* GetOrCreateHistogram(
      const google::cmrt::sdk::metric_service::v1::Metric& metric) noexcept;

  // The configuration for metric client.
  std::shared_ptr<MetricClientOptions> metric_client_options_;

  // Instance client provider to fetch cloud metadata.
  std::shared_ptr<InstanceClientProviderInterface> instance_client_provider_;

  // OpenTelemetry Meter object
  std::shared_ptr<opentelemetry::metrics::Meter> otel_meter_;

  // OpenTelemetry instrument object caches. Guarded by mutexes.
  std::mutex counter_cache_mutex_;
  std::map<std::string,
           std::unique_ptr<opentelemetry::metrics::Counter<double>>>
      counter_cache_;

  std::mutex histogram_cache_mutex_;
  std::map<std::string,
           std::unique_ptr<opentelemetry::metrics::Histogram<double>>>
      histogram_cache_;

  std::mutex gauge_cache_mutex_;
  std::map<std::string, std::unique_ptr<opentelemetry::metrics::Gauge<double>>>
      gauge_cache_;
};
}  // namespace google::scp::cpio::client_providers
