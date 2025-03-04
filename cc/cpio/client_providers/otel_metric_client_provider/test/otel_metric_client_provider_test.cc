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

#include "cc/cpio/client_providers/otel_metric_client_provider/src/otel_metric_client_provider.h"

#include <map>
#include <memory>
#include <utility>
#include <vector>

#include <google/protobuf/util/time_util.h>

#include "core/test/utils/scp_test_base.h"
#include "cpio/client_providers/instance_client_provider/mock/mock_instance_client_provider.h"
#include "cpio/client_providers/interface/metric_client_provider_interface.h"
#include "cpio/client_providers/metric_client_provider/src/common/error_codes.h"
#include "cpio/client_providers/otel_metric_client_provider/mock/mock_opentelemetry.h"
#include "public/core/interface/execution_result.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/interface/metric_client/metric_client_interface.h"
#include "public/cpio/interface/metric_client/type_def.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"

using google::cmrt::sdk::metric_service::v1::MetricType;
using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::scp::core::AsyncContext;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::errors::SC_METRIC_CLIENT_PROVIDER_METRIC_NOT_SET;
using google::scp::core::test::ResultIs;
using google::scp::core::test::ScpTestBase;
using google::scp::cpio::client_providers::mock::MockInstanceClientProvider;
using std::make_shared;
using std::make_unique;
using std::shared_ptr;
using std::stod;
using std::string;
using std::unique_ptr;
using std::vector;
using testing::ByMove;
using testing::Matcher;
using testing::Return;

namespace {
constexpr char kInstanceResourceName[] =
    R"(//compute.googleapis.com/projects/123456789/zones/us-central1-c/instances/987654321)";
}  // namespace

MATCHER_P(ContainsOtelLabels, kv, "") {
  std::map<std::string, std::string> result;
  arg.ForEachKeyValue(
      [&result](std::string_view key,
                opentelemetry::common::AttributeValue value) noexcept {
        result.insert(
            {std::string(key), std::string(std::get<std::string_view>(value))});
        return true;
      });
  return std::equal(kv.begin(), kv.end(), result.begin());
}

namespace google::scp::cpio::client_providers::gcp_metric_client::test {

class OtelMetricClientProviderTest : public ScpTestBase {
 protected:
  void SetUp() override {
    instance_client_provider_mock_ = make_shared<MockInstanceClientProvider>();
    instance_client_provider_mock_->instance_resource_name =
        kInstanceResourceName;
    mock_meter_ = make_shared<MockMeter>();
    metric_client_provider_ = CreateClientProvider();
  }

  unique_ptr<OtelMetricClientProvider> CreateClientProvider() {
    auto metric_client_options = make_shared<MetricClientOptions>();
    metric_client_options->enable_remote_metric_aggregation = true;
    metric_client_options->remote_metric_collector_address =
        "collector.gcp.host:4317";
    return make_unique<OtelMetricClientProvider>(
        metric_client_options, instance_client_provider_mock_, mock_meter_);
  }

  shared_ptr<MockInstanceClientProvider> instance_client_provider_mock_;
  shared_ptr<OtelMetricClientProvider> metric_client_provider_;
  shared_ptr<MockMeter> mock_meter_;
};

TEST_F(OtelMetricClientProviderTest, RunTest) {
  EXPECT_SUCCESS(metric_client_provider_->Init());
  EXPECT_SUCCESS(metric_client_provider_->Run());
}

TEST_F(OtelMetricClientProviderTest, RecordMetricsCorrectly) {
  auto mock_counter = make_unique<MockCounter<double>>();
  auto mock_gauge = make_unique<MockGauge<double>>();
  auto mock_histogram = make_unique<MockHistogram<double>>();

  auto* mock_gauge_ptr = mock_gauge.get();
  auto* mock_counter_ptr = mock_counter.get();
  auto* mock_histogram_ptr = mock_histogram.get();

  metric_client_provider_->Init();
  metric_client_provider_->Run();

  std::map<std::string, std::string> labels = {{"instance", "unit_test"}};
  auto request = std::make_shared<PutMetricsRequest>();
  auto counter_metric = request->add_metrics();
  counter_metric->set_name("counter");
  counter_metric->set_value("1");
  counter_metric->set_type(MetricType::METRIC_TYPE_COUNTER);
  *counter_metric->mutable_labels() = {labels.begin(), labels.end()};
  auto gauge_metric = request->add_metrics();
  gauge_metric->set_name("gauge");
  gauge_metric->set_value("2");
  gauge_metric->set_type(MetricType::METRIC_TYPE_GAUGE);
  *gauge_metric->mutable_labels() = {labels.begin(), labels.end()};
  auto histogram_metric = request->add_metrics();
  histogram_metric->set_name("histogram");
  histogram_metric->set_value("3");
  histogram_metric->set_type(MetricType::METRIC_TYPE_HISTOGRAM);
  *histogram_metric->mutable_labels() = {labels.begin(), labels.end()};
  auto counter_metric_second_time = request->add_metrics();
  counter_metric_second_time->set_name("counter");
  counter_metric_second_time->set_value("1");
  counter_metric_second_time->set_type(MetricType::METRIC_TYPE_COUNTER);
  *counter_metric_second_time->mutable_labels() = {labels.begin(),
                                                   labels.end()};

  AsyncContext<PutMetricsRequest, PutMetricsResponse> context(
      request,
      [&](AsyncContext<PutMetricsRequest, PutMetricsResponse>& context) {
        EXPECT_SUCCESS(context.result);
      });

  // CreateDoubleCounter should only be called once even though there are two
  // additions
  EXPECT_CALL(*mock_meter_, CreateDoubleCounter)
      .Times(1)
      .WillOnce(Return(ByMove(std::move(mock_counter))));
  EXPECT_CALL(*mock_meter_, CreateDoubleGauge)
      .Times(1)
      .WillOnce(Return(ByMove(std::move(mock_gauge))));
  EXPECT_CALL(*mock_meter_, CreateDoubleHistogram)
      .Times(1)
      .WillOnce(Return(ByMove(std::move(mock_histogram))));
  EXPECT_CALL(*mock_counter_ptr,
              Add(1, Matcher<const opentelemetry::common::KeyValueIterable&>(
                         ContainsOtelLabels(labels))))
      .Times(2);
  EXPECT_CALL(*mock_gauge_ptr,
              Record(2, Matcher<const opentelemetry::common::KeyValueIterable&>(
                            ContainsOtelLabels(labels))))
      .Times(1);
  EXPECT_CALL(*mock_histogram_ptr,
              Record(3, Matcher<const opentelemetry::common::KeyValueIterable&>(
                            ContainsOtelLabels(labels))))
      .Times(1);
  metric_client_provider_->PutMetrics(context);
}

TEST_F(OtelMetricClientProviderTest, FailsWhenNoMetricInRequest) {
  auto request = make_shared<PutMetricsRequest>();
  AsyncContext<PutMetricsRequest, PutMetricsResponse> context(
      move(request),
      [&](AsyncContext<PutMetricsRequest, PutMetricsResponse>& context) {
        EXPECT_THAT(context.result,
                    ResultIs(FailureExecutionResult(
                        SC_METRIC_CLIENT_PROVIDER_METRIC_NOT_SET)));
      });

  EXPECT_SUCCESS(metric_client_provider_->Init());
  EXPECT_SUCCESS(metric_client_provider_->Run());
  metric_client_provider_->PutMetrics(context);
  EXPECT_SUCCESS(metric_client_provider_->Stop());
}

}  // namespace google::scp::cpio::client_providers::gcp_metric_client::test
