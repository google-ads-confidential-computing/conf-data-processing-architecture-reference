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

#include "gcp_metric_client_provider.h"

#include <string>
#include <vector>

#include "core/common/uuid/src/uuid.h"
#include "core/interface/async_context.h"
#include "cpio/client_providers/common/src/gcp/gcp_utils.h"
#include "cpio/client_providers/instance_client_provider/src/gcp/gcp_instance_client_utils.h"
#include "cpio/common/src/gcp/gcp_utils.h"
#include "google/cloud/future.h"
#include "google/cloud/monitoring/metric_client.h"
#include "google/cloud/monitoring/metric_connection.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/core/interface/execution_result_or_macros.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"

#include "error_codes.h"
#include "gcp_metric_client_utils.h"

using google::cloud::future;
using google::cloud::Status;
using google::cloud::StatusCode;
using google::cloud::monitoring::MakeMetricServiceConnection;
using google::cloud::monitoring::MetricServiceClient;
using google::cmrt::sdk::metric_service::v1::Metric;
using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::monitoring::v3::CreateTimeSeriesRequest;
using google::monitoring::v3::TimeSeries;
using google::scp::core::AsyncContext;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::cpio::client_providers::GcpInstanceClientUtils;
using google::scp::cpio::client_providers::GcpInstanceResourceNameDetails;
using google::scp::cpio::client_providers::GcpMetricClientUtils;
using std::bind;
using std::make_shared;
using std::pair;
using std::shared_ptr;
using std::string;
using std::vector;
using std::placeholders::_1;

namespace {
constexpr char kGcpMetricClientProvider[] = "GcpMetricClientProvider";

// The limit of GCP metric client time series list size is 200.
constexpr size_t kGcpTimeSeriesSizeLimit = 200;

// A pair of a CreateTimeSeriesRequest and a shared pointer to a vector of
// AsyncContext objects.The CreateTimeSeriesRequest object contains the
// time_series (metrics) that are to be created, and the AsyncContext objects
// represent the contexts in which the metrics were collected.
typedef pair<
    CreateTimeSeriesRequest,
    shared_ptr<vector<AsyncContext<PutMetricsRequest, PutMetricsResponse>>>>
    TimeSeriesRequestContextsPair;
}  // namespace

namespace google::scp::cpio::client_providers {

ExecutionResult GcpMetricClientProvider::Run() noexcept {
  auto execution_result = MetricClientProvider::Run();
  if (!execution_result.Successful()) {
    SCP_ERROR(kGcpMetricClientProvider, kZeroUuid, execution_result,
              "Failed to initialize MetricClientProvider");
    return execution_result;
  }

  ASSIGN_OR_RETURN(instance_resource_,
                   GcpUtils::GetInstanceResource(kGcpMetricClientProvider,
                                                 instance_client_provider_));

  project_name_ =
      GcpUtils::GetProjectNameFromInstanceResource(instance_resource_);

  metric_service_client_ = metric_service_client_factory_->CreateClient();

  return SuccessExecutionResult();
}

shared_ptr<MetricServiceClient>
GcpMetricServiceClientFactory::CreateClient() noexcept {
  auto metric_service_connection = MakeMetricServiceConnection();
  return make_shared<MetricServiceClient>(metric_service_connection);
}

ExecutionResult GcpMetricClientProvider::MetricsBatchPush(
    const shared_ptr<
        vector<AsyncContext<PutMetricsRequest, PutMetricsResponse>>>&
        context_vector) noexcept {
  MetricServiceClient metric_client(*metric_service_client_);

  // When batch recording is not enabled, expect the namespace to be set on the
  // request. context_vector won't be empty.
  auto name_space = metric_client_options_->enable_batch_recording
                        ? metric_client_options_->namespace_for_batch_recording
                        : context_vector->back().request->metric_namespace();

  // Chops the metrics from context_vector to small piece of
  // requests_context_vector, and requests_context_vector is used in callback
  // function to set the response for requests.
  vector<TimeSeriesRequestContextsPair> ts_request_context_list;

  // Create a CreateTimeSeriesRequest with namespace assigned.
  CreateTimeSeriesRequest time_series_request;
  time_series_request.set_name(project_name_);

  ts_request_context_list.push_back(std::make_pair(
      time_series_request,
      make_shared<
          vector<AsyncContext<PutMetricsRequest, PutMetricsResponse>>>()));

  for (auto& context : *context_vector) {
    auto time_series_list_or = GcpMetricClientUtils::ParseRequestToTimeSeries(
        context.request, name_space);
    // Sets the result for the requests that failed in parsing to time series.
    if (!time_series_list_or.Successful()) {
      context.result = time_series_list_or.result();
      context.Finish();
      continue;
    }

    auto& time_series_list = time_series_list_or.value();
    // Add gce_instance resource info to TimeSeries data.
    GcpMetricClientUtils::AddResourceToTimeSeries(
        instance_resource_.project_id, instance_resource_.instance_id,
        instance_resource_.zone_id, time_series_list);

    // If the combined number of time series in the request and pending context
    // exceeds the GCP request time series limit of 200, push a new time series
    // request with an empty context vector.
    if (ts_request_context_list.back().first.time_series().size() +
            time_series_list.size() >
        kGcpTimeSeriesSizeLimit) {
      // Create a CreateTimeSeriesRequest with namespace assigned.
      CreateTimeSeriesRequest empty_time_series;
      empty_time_series.set_name(project_name_);

      ts_request_context_list.push_back(std::make_pair(
          empty_time_series,
          make_shared<
              vector<AsyncContext<PutMetricsRequest, PutMetricsResponse>>>()));
    }

    // Add the time series to the last time_series_request and add the context
    // to the corresponding context vector.
    auto& [last_time_series, last_context_chunk] =
        ts_request_context_list.back();
    last_time_series.mutable_time_series()->Add(time_series_list.begin(),
                                                time_series_list.end());
    last_context_chunk->push_back(context);
  }

  for (const auto& [time_series, context_chunk] : ts_request_context_list) {
    metric_client.AsyncCreateTimeSeries(time_series)
        .then(
            std::bind(&GcpMetricClientProvider::OnAsyncCreateTimeSeriesCallback,
                      this, context_chunk, _1));
    active_push_count_++;
  }

  return SuccessExecutionResult();
}

void GcpMetricClientProvider::OnAsyncCreateTimeSeriesCallback(
    shared_ptr<vector<AsyncContext<PutMetricsRequest, PutMetricsResponse>>>
        metric_requests_vector,
    future<Status> outcome) noexcept {
  active_push_count_--;
  auto outcome_status = outcome.get();
  auto result =
      google::scp::cpio::common::GcpUtils::GcpErrorConverter(outcome_status);

  if (!result.Successful()) {
    SCP_ERROR_CONTEXT(kGcpMetricClientProvider, metric_requests_vector->back(),
                      result, "The error is %s",
                      outcome_status.message().c_str());
  }

  for (auto& record_metric_context : *metric_requests_vector) {
    record_metric_context.response = make_shared<PutMetricsResponse>();
    record_metric_context.result = result;
    record_metric_context.Finish();
  }
  return;
}

#ifndef TEST_CPIO
shared_ptr<MetricClientInterface> MetricClientProviderFactory::Create(
    const shared_ptr<MetricClientOptions>& options,
    const shared_ptr<InstanceClientProviderInterface>& instance_client_provider,
    const shared_ptr<AsyncExecutorInterface>& async_executor,
    const shared_ptr<AsyncExecutorInterface>& io_async_executor) {
  return make_shared<GcpMetricClientProvider>(options, instance_client_provider,
                                              async_executor);
}
#endif
}  // namespace google::scp::cpio::client_providers
