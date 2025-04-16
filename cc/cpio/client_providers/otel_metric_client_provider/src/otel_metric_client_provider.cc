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

#include "otel_metric_client_provider.h"

#include <functional>
#include <map>
#include <memory>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "core/common/time_provider/src/time_provider.h"
#include "core/common/uuid/src/uuid.h"
#include "core/interface/async_context.h"
#include "core/interface/async_executor_interface.h"
#include "cpio/client_providers/interface/type_def.h"
#include "cpio/client_providers/metric_client_provider/src/common/error_codes.h"
#include "cpio/common/src/common_error_codes.h"
#include "google/protobuf/any.pb.h"
#include "public/core/interface/execution_result.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"

using google::cmrt::sdk::metric_service::v1::Metric;
using google::cmrt::sdk::metric_service::v1::MetricType;
using google::cmrt::sdk::metric_service::v1::MetricUnit_Name;
using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::scp::core::AsyncContext;
using google::scp::core::ExecutionResult;
using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::core::errors::SC_COMMON_ERRORS_UNIMPLEMENTED;
using google::scp::core::errors::SC_METRIC_CLIENT_PROVIDER_METRIC_NAME_NOT_SET;
using google::scp::core::errors::SC_METRIC_CLIENT_PROVIDER_METRIC_NOT_SET;
using google::scp::core::errors::SC_METRIC_CLIENT_PROVIDER_METRIC_TYPE_NOT_SET;
using google::scp::core::errors::SC_METRIC_CLIENT_PROVIDER_METRIC_VALUE_NOT_SET;
using google::scp::core::errors::SC_METRIC_CLIENT_PROVIDER_NAMESPACE_NOT_SET;
using google::scp::core::errors::
    SC_METRIC_CLIENT_PROVIDER_OTEL_REMOTE_COLLECTOR_ADDRESS_NOT_SET;
using opentelemetry::metrics::Counter;
using opentelemetry::metrics::Gauge;
using opentelemetry::metrics::Histogram;
using std::bind;
using std::make_shared;
using std::move;
using std::shared_ptr;
using std::stod;
using std::string;
using std::vector;
using std::chrono::milliseconds;
using std::chrono::nanoseconds;

static constexpr char kOtelMetricClientProvider[] = "OtelMetricClientProvider";

namespace google::scp::cpio::client_providers {

ExecutionResult OtelMetricClientProvider::Init() noexcept {
  if (metric_client_options_->remote_metric_collector_address.empty()) {
    auto execution_result = FailureExecutionResult(
        SC_METRIC_CLIENT_PROVIDER_OTEL_REMOTE_COLLECTOR_ADDRESS_NOT_SET);
    SCP_ERROR(kOtelMetricClientProvider, kZeroUuid, execution_result,
              "Should set the OpenTelemetry remote collector host address.");
    return execution_result;
  }

  return SuccessExecutionResult();
}

ExecutionResult OtelMetricClientProvider::Run() noexcept {
  return SuccessExecutionResult();
}

ExecutionResult OtelMetricClientProvider::Stop() noexcept {
  return SuccessExecutionResult();
}

void OtelMetricClientProvider::PutMetrics(
    AsyncContext<PutMetricsRequest, PutMetricsResponse>&
        record_metric_context) noexcept {
  auto execution_result =
      OtelMetricClientProvider::ValidateRequest(*record_metric_context.request);
  if (!execution_result.Successful()) {
    SCP_ERROR_CONTEXT(kOtelMetricClientProvider, record_metric_context,
                      execution_result, "Invalid metric.");
    record_metric_context.result = execution_result;
    record_metric_context.Finish();
    return;
  }

  for (auto metric : record_metric_context.request->metrics()) {
    RecordMetric(metric);
  }
}

void OtelMetricClientProvider::RecordMetric(const Metric& metric) noexcept {
  const std::string metric_name = metric.name();
  std::map<std::string, std::string> labels(metric.labels().begin(),
                                            metric.labels().end());
  // Merge fixed_labels_ (usually containing resource labels) with
  // the original label set.
  for (const auto& [key, value] : fixed_labels_) {
    labels[key] = value;
  }

  auto labelkv =
      opentelemetry::common::KeyValueIterableView<decltype(labels)>{labels};
  if (metric.type() == MetricType::METRIC_TYPE_GAUGE) {
    GetOrCreateGauge(metric)->Record(stod(metric.value()), labelkv);
    return;
  }

  if (metric.type() == MetricType::METRIC_TYPE_HISTOGRAM) {
    GetOrCreateHistogram(metric)->Record(stod(metric.value()), labelkv);
    return;
  }

  GetOrCreateCounter(metric)->Add(stod(metric.value()), labelkv);
}

ExecutionResult OtelMetricClientProvider::ValidateRequest(
    const PutMetricsRequest& request) noexcept {
  if (request.metrics().empty()) {
    return FailureExecutionResult(SC_METRIC_CLIENT_PROVIDER_METRIC_NOT_SET);
  }
  for (auto metric : request.metrics()) {
    if (metric.name().empty()) {
      return FailureExecutionResult(
          SC_METRIC_CLIENT_PROVIDER_METRIC_NAME_NOT_SET);
    }
    if (metric.value().empty()) {
      return FailureExecutionResult(
          SC_METRIC_CLIENT_PROVIDER_METRIC_VALUE_NOT_SET);
    }
    if (metric.type() == MetricType::METRIC_TYPE_UNKNOWN) {
      return FailureExecutionResult(
          SC_METRIC_CLIENT_PROVIDER_METRIC_TYPE_NOT_SET);
    }
  }
  return SuccessExecutionResult();
}

ExecutionResultOr<PutMetricsResponse> OtelMetricClientProvider::PutMetricsSync(
    PutMetricsRequest request) noexcept {
  RETURN_IF_FAILURE(OtelMetricClientProvider::ValidateRequest(request));
  for (auto metric : request.metrics()) {
    RecordMetric(metric);
  }
  return PutMetricsResponse();
}

Counter<double>* OtelMetricClientProvider::GetOrCreateCounter(
    const Metric& metric) noexcept {
  std::lock_guard lock(counter_cache_mutex_);
  auto it = counter_cache_.find(metric.name());
  if (it != counter_cache_.end()) {
    return it->second.get();
  } else {
    auto new_counter = otel_meter_->CreateDoubleCounter(
        metric.name(), /*description*/ "", MetricUnit_Name(metric.unit()));
    auto* counter_ptr = new_counter.get();
    counter_cache_[metric.name()] = std::move(new_counter);
    return counter_ptr;
  }
}

Gauge<double>* OtelMetricClientProvider::GetOrCreateGauge(
    const Metric& metric) noexcept {
  std::lock_guard lock(gauge_cache_mutex_);
  auto it = gauge_cache_.find(metric.name());
  if (it != gauge_cache_.end()) {
    return it->second.get();
  } else {
    auto new_gauge = otel_meter_->CreateDoubleGauge(
        metric.name(), /*description*/ "", MetricUnit_Name(metric.unit()));
    auto* gauge_ptr = new_gauge.get();
    gauge_cache_[metric.name()] = std::move(new_gauge);
    return gauge_ptr;
  }
}

Histogram<double>* OtelMetricClientProvider::GetOrCreateHistogram(
    const Metric& metric) noexcept {
  std::lock_guard lock(histogram_cache_mutex_);
  auto it = histogram_cache_.find(metric.name());
  if (it != histogram_cache_.end()) {
    return it->second.get();
  } else {
    auto new_histogram = otel_meter_->CreateDoubleHistogram(
        metric.name(), /*description*/ "", MetricUnit_Name(metric.unit()));
    auto* histogram_ptr = new_histogram.get();
    histogram_cache_[metric.name()] = std::move(new_histogram);
    return histogram_ptr;
  }
}

}  // namespace google::scp::cpio::client_providers
