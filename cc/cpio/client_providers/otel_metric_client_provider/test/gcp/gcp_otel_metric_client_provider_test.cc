// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "cc/cpio/client_providers/otel_metric_client_provider/src/gcp/gcp_otel_metric_client_provider.h"

#include <memory>
#include <vector>

#include <google/protobuf/util/time_util.h>

#include "absl/strings/str_cat.h"
#include "core/test/utils/conditional_wait.h"
#include "core/test/utils/scp_test_base.h"
#include "cpio/client_providers/instance_client_provider/mock/mock_instance_client_provider.h"
#include "cpio/client_providers/interface/metric_client_provider_interface.h"
#include "cpio/client_providers/otel_metric_client_provider/mock/mock_opentelemetry.h"
#include "public/core/interface/execution_result.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/interface/metric_client/metric_client_interface.h"
#include "public/cpio/interface/metric_client/type_def.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"

using absl::StrCat;
using google::cmrt::sdk::metric_service::v1::MetricType;
using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::scp::core::AsyncContext;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::test::ScpTestBase;
using google::scp::cpio::client_providers::mock::MockInstanceClientProvider;
using std::make_shared;
using std::make_unique;
using std::shared_ptr;
using std::stod;
using std::string;
using std::to_string;
using std::unique_ptr;
using std::vector;
using std::chrono::duration_cast;
using testing::ByMove;
using testing::Matcher;
using testing::Return;

namespace {
constexpr char kInstanceResourceName[] =
    R"(//compute.googleapis.com/projects/123456789/zones/us-central1-c/instances/987654321)";

}  // namespace

MATCHER_P(ContainsOtelLabels, kv, "Invalid labels") {
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

class GcpOtelMetricClientProviderTest : public ScpTestBase {
 protected:
  void SetUp() override {
    instance_client_provider_mock_ = make_shared<MockInstanceClientProvider>();
    instance_client_provider_mock_->instance_resource_name =
        kInstanceResourceName;
    mock_meter_ = make_shared<MockMeter>();
  }

  unique_ptr<GcpOtelMetricClientProvider> CreateClientProvider() {
    auto metric_client_options = make_shared<MetricClientOptions>();
    metric_client_options->enable_remote_metric_aggregation = true;
    metric_client_options->remote_metric_collector_address =
        "collector.gcp.host:4317";
    return make_unique<GcpOtelMetricClientProvider>(
        metric_client_options, instance_client_provider_mock_, mock_meter_);
  }

  shared_ptr<MockInstanceClientProvider> instance_client_provider_mock_;
  shared_ptr<GcpOtelMetricClientProvider> metric_client_provider_;
  shared_ptr<MockMeter> mock_meter_;
};

TEST_F(GcpOtelMetricClientProviderTest, RunTest) {
  metric_client_provider_ = CreateClientProvider();
  EXPECT_SUCCESS(metric_client_provider_->Init());
  EXPECT_SUCCESS(metric_client_provider_->Run());
}

TEST_F(GcpOtelMetricClientProviderTest,
       RecordMetricsCorrectlyWithMergedLabels) {
  metric_client_provider_ = CreateClientProvider();
  metric_client_provider_->Init();
  metric_client_provider_->Run();

  auto mock_counter = make_unique<MockCounter<double>>();
  auto mock_counter_ptr = mock_counter.get();

  std::map<std::string, std::string> labels = {{"test_label", "unit_test"}};
  auto request = std::make_shared<PutMetricsRequest>();
  auto counter_metric = request->add_metrics();
  counter_metric->set_name("counter");
  counter_metric->set_value("1");
  counter_metric->set_type(MetricType::METRIC_TYPE_COUNTER);
  *counter_metric->mutable_labels() = {labels.begin(), labels.end()};

  std::map<std::string, std::string> expected_labels = {
      {"test_label", "unit_test"},
      {"exported_instance_id", "987654321"},
      {"exported_zone", "us-central1-c"}};

  EXPECT_CALL(*mock_meter_, CreateDoubleCounter)
      .WillOnce(Return(ByMove(std::move(mock_counter))));
  EXPECT_CALL(*mock_counter_ptr,
              Add(1, Matcher<const opentelemetry::common::KeyValueIterable&>(
                         ContainsOtelLabels(expected_labels))));
  EXPECT_TRUE(metric_client_provider_->PutMetricsSync(*request).Successful());
}
}  // namespace google::scp::cpio::client_providers::gcp_metric_client::test
