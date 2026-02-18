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

#include "public/cpio/utils/metric_instance/src/metric_utils.h"

#include <gtest/gtest.h>

#include <chrono>
#include <map>
#include <memory>
#include <optional>
#include <string>

#include "core/interface/async_context.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/mock/metric_client/mock_metric_client.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"

using google::cloud::Status;
using google::cloud::StatusCode;
using google::cmrt::sdk::metric_service::v1::Metric;
using google::cmrt::sdk::metric_service::v1::MetricType;
using google::cmrt::sdk::metric_service::v1::MetricUnit;
using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::scp::cpio::MockMetricClient;
using std::make_shared;
using std::map;
using std::stoi;
using std::string;
using testing::_;
namespace {
constexpr char kMetricName[] = "FrontEndRequestCount";
constexpr char kMetricValue[] = "1234";
constexpr char kNamespace[] = "PBS";
constexpr char kComponentValue[] = "component_name";
constexpr char kMethodValue[] = "method_name";

}  // namespace

namespace google::scp::cpio {

TEST(MetricUtilsTest, GetPutMetricsRequest) {
  auto metric_info = MetricDefinition(
      kMetricName, MetricUnit::METRIC_UNIT_UNKNOWN, kNamespace,
      map<string, string>(
          {{"Key1", "Value1"}, {"Key2", "Value2"}, {"Key3", "Value3"}}));

  auto record_metric_request = make_shared<PutMetricsRequest>();
  MetricUtils::GetPutMetricsRequest(record_metric_request, metric_info,
                                    kMetricValue);

  EXPECT_EQ(record_metric_request->metric_namespace(), kNamespace);
  EXPECT_EQ(record_metric_request->metrics()[0].name(), kMetricName);
  EXPECT_EQ(record_metric_request->metrics()[0].unit(),
            MetricUnit::METRIC_UNIT_UNKNOWN);
  EXPECT_EQ(record_metric_request->metrics()[0].value(), kMetricValue);
  EXPECT_EQ(record_metric_request->metrics()[0].labels().size(), 3);
  EXPECT_TRUE(record_metric_request->metrics()[0]
                  .labels()
                  .find(string("Key1"))
                  ->second == string("Value1"));
}

TEST(MetricUtilsTest, GetPutMetricsRequestWithoutNamespace) {
  auto metric_info = MetricDefinition(
      kMetricName, MetricUnit::METRIC_UNIT_UNKNOWN, std::nullopt, {});

  auto record_metric_request = make_shared<PutMetricsRequest>();
  MetricUtils::GetPutMetricsRequest(record_metric_request, metric_info,
                                    kMetricValue);

  EXPECT_EQ(record_metric_request->metric_namespace(), "");
}

TEST(MetricUtilsTest, CreateMetricLabelsWithComponentSignature) {
  auto metric_labels1 = MetricUtils::CreateMetricLabelsWithComponentSignature(
      kComponentValue, kMethodValue);
  EXPECT_EQ(metric_labels1.size(), 2);
  EXPECT_EQ(metric_labels1.find("ComponentName")->second, kComponentValue);
  EXPECT_EQ(metric_labels1.find("MethodName")->second, kMethodValue);

  auto metric_labels2 =
      MetricUtils::CreateMetricLabelsWithComponentSignature(kComponentValue);
  EXPECT_EQ(metric_labels2.size(), 1);
  EXPECT_EQ(metric_labels2.find("ComponentName")->second, kComponentValue);
}

TEST(MetricUtilsTest, PushGcpKmsDecryptionErrorRateMetric) {
  auto metric_client = make_shared<MockMetricClient>();
  auto status = Status(StatusCode::kUnavailable, "Test error message");

  EXPECT_CALL(*metric_client, PutMetricsSync)
      .Times(1)
      .WillOnce([](const PutMetricsRequest& request) {
        EXPECT_EQ(request.metrics_size(), 1);
        const auto& actual_metric = request.metrics(0);
        EXPECT_EQ(actual_metric.name(), "GcpKmsDecryptionErrorRate");
        EXPECT_EQ(actual_metric.type(), MetricType::METRIC_TYPE_COUNTER);
        EXPECT_EQ(actual_metric.value(), "1");
        EXPECT_EQ(actual_metric.labels().at("error_code"), "UNAVAILABLE");
        return PutMetricsResponse();
      });

  MetricUtils::PushGcpKmsDecryptionErrorRateMetric(metric_client, status);
}

TEST(MetricUtilsTest, PushGcpKmsDecryptionLatencyMetric) {
  auto metric_client = make_shared<MockMetricClient>();
  uint64_t latency_ms = 250;

  EXPECT_CALL(*metric_client, PutMetricsSync)
      .Times(1)
      .WillOnce([](const PutMetricsRequest& request) {
        EXPECT_EQ(request.metrics_size(), 1);
        const auto& actual_metric = request.metrics(0);
        EXPECT_EQ(actual_metric.name(), "GcpKmsDecryptionLatencyInMillis");
        EXPECT_EQ(actual_metric.type(), MetricType::METRIC_TYPE_HISTOGRAM);
        EXPECT_EQ(stoi(actual_metric.value()), 250);
        return PutMetricsResponse();
      });

  MetricUtils::PushGcpKmsDecryptionLatencyMetric(metric_client, latency_ms);
}

TEST(MetricUtilsTest, PutMetric) {
  auto metric_client = make_shared<MockMetricClient>();
  auto metric_label = MetricUtils::CreateMetricLabelsWithComponentSignature(
      kComponentValue, kMethodValue);
  Metric metric;
  metric.set_name(kMetricName);
  metric.set_unit(MetricUnit::METRIC_UNIT_COUNT);
  metric.set_value(kMetricValue);
  *metric.mutable_labels() = {metric_label.begin(), metric_label.end()};

  EXPECT_CALL(*metric_client, PutMetricsSync)
      .Times(1)
      .WillOnce([&metric](const PutMetricsRequest& request) {
        EXPECT_EQ(request.metrics_size(), 1);
        const auto& actual_metric = request.metrics(0);
        EXPECT_EQ(actual_metric.name(), metric.name());
        EXPECT_EQ(actual_metric.unit(), metric.unit());
        EXPECT_EQ(actual_metric.value(), metric.value());
        EXPECT_EQ(actual_metric.labels_size(), metric.labels_size());
        for (const auto& [key, value] : metric.labels()) {
          EXPECT_EQ(actual_metric.labels().at(key), value);
        }
        return PutMetricsResponse();
      });

  MetricUtils::PutMetric(metric_client, metric);
}

}  // namespace google::scp::cpio
