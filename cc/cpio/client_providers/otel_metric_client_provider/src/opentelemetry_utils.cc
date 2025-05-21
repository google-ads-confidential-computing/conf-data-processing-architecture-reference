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

#include "opentelemetry_utils.h"

#include <iostream>
#include <memory>
#include <utility>

#include "opentelemetry/exporters/otlp/otlp_grpc_metric_exporter_factory.h"
#include "opentelemetry/exporters/otlp/otlp_grpc_metric_exporter_options.h"
#include "opentelemetry/metrics/provider.h"
#include "opentelemetry/sdk/metrics/aggregation/default_aggregation.h"
#include "opentelemetry/sdk/metrics/export/periodic_exporting_metric_reader.h"
#include "opentelemetry/sdk/metrics/export/periodic_exporting_metric_reader_factory.h"
#include "opentelemetry/sdk/metrics/meter_context_factory.h"
#include "opentelemetry/sdk/metrics/meter_provider.h"
#include "opentelemetry/sdk/metrics/meter_provider_factory.h"
#include "opentelemetry/sdk/metrics/view/view_registry_factory.h"
#include "opentelemetry/sdk/resource/semantic_conventions.h"
#include "public/cpio/interface/metric_client/type_def.h"

using google::scp::cpio::MetricClientOptions;
using opentelemetry::exporter::otlp::OtlpGrpcMetricExporterFactory;
using opentelemetry::metrics::Meter;
using opentelemetry::metrics::MeterProvider;
using opentelemetry::metrics::Provider;
using opentelemetry::sdk::metrics::MeterContextFactory;
using opentelemetry::sdk::metrics::MeterProviderFactory;
using opentelemetry::sdk::metrics::PeriodicExportingMetricReaderFactory;
using opentelemetry::sdk::metrics::PeriodicExportingMetricReaderOptions;
using opentelemetry::sdk::metrics::ViewRegistryFactory;
using opentelemetry::sdk::resource::Resource;
using opentelemetry::sdk::resource::ResourceAttributes;
using std::shared_ptr;
using std::string;

namespace google::scp::cpio::client_providers {

constexpr auto kExportTimeout = std::chrono::milliseconds(1000);

shared_ptr<Meter> OpenTelemetryUtils::CreateOpenTelemetryMeter(
    const shared_ptr<MetricClientOptions>& options, const string& instance_id,
    const string& zone) noexcept {
  opentelemetry::exporter::otlp::OtlpGrpcMetricExporterOptions opts;
  opts.endpoint = options->remote_metric_collector_address;
  opts.use_ssl_credentials = false;
  auto exporter = OtlpGrpcMetricExporterFactory::Create(opts);

  PeriodicExportingMetricReaderOptions reader_options;
  reader_options.export_interval_millis = options->metric_exporter_interval;
  reader_options.export_timeout_millis = kExportTimeout;
  auto reader = PeriodicExportingMetricReaderFactory::Create(
      std::move(exporter), reader_options);

  const auto resource_attributes =
      ResourceAttributes{{"gcp.resource_type", "gce_instance"},
                         {"gcp.gce_instance.zone", zone},
                         {"gcp.gce_instance.instance_id", instance_id}};

  auto context = MeterContextFactory::Create(
      ViewRegistryFactory::Create(), Resource::Create(resource_attributes));
  context->AddMetricReader(std::move(reader));
  auto u_provider = MeterProviderFactory::Create(std::move(context));
  shared_ptr<MeterProvider> provider(std::move(u_provider));
  Provider::SetMeterProvider(provider);
  return Provider::GetMeterProvider()->GetMeter("scp-otel-metric-client",
                                                "1.0");
}

}  // namespace google::scp::cpio::client_providers
