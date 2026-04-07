/*
 * Copyright 2026 Google LLC
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

#include "public/cpio/utils/dual_writing_metric_client/src/dual_writing_metric_client.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <utility>

#include "core/test/utils/proto_test_utils.h"
#include "core/test/utils/scp_test_base.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/mock/metric_client/mock_metric_client.h"
#include "public/cpio/utils/metric_instance/mock/mock_aggregate_metric.h"
#include "public/cpio/utils/metric_instance/mock/mock_metric_instance_factory.h"
#include "public/cpio/utils/metric_instance/mock/mock_time_aggregate_metric.h"
#include "public/cpio/utils/metric_instance/src/metric_utils.h"

using google::cmrt::sdk::metric_service::v1::Metric;
using google::cmrt::sdk::metric_service::v1::MetricType;
using google::cmrt::sdk::metric_service::v1::MetricUnit;
using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::scp::core::test::EqualsProto;
using google::scp::core::test::IsSuccessfulAndHolds;
using google::scp::core::test::ScpTestBase;
using google::scp::core::test::SubstituteAndParseTextToProto;
using google::scp::cpio::MetricClientInterface;
using google::scp::cpio::MetricDefinition;
using google::scp::cpio::MetricInstanceFactoryInterface;
using google::scp::cpio::MetricUtils;
using google::scp::cpio::MockAggregateMetric;
using google::scp::cpio::MockMetricClient;
using google::scp::cpio::MockMetricInstanceFactory;
using google::scp::cpio::MockTimeAggregateMetric;
using std::make_unique;
using std::move;
using std::nullopt;
using std::optional;
using std::stoull;
using std::string;
using std::string_view;
using std::vector;
using testing::ByMove;
using testing::Eq;
using testing::ExplainMatchResult;
using testing::NiceMock;
using testing::Optional;
using testing::Pair;
using testing::Return;
using testing::UnorderedElementsAre;

namespace google::scp::cpio {
namespace {
constexpr char kOtelNamespace[] = "OtelNamespace";
}  // namespace

TEST(MetricWrapperTest, IncrementWorks) {
  auto metric = MetricWrapper(SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_COUNT
    type: METRIC_TYPE_COUNTER
    labels { key: "label_key" value: "label_value" }
  )pb"));
  auto expected_metric = metric.metric;
  expected_metric.set_value("1");
  EXPECT_THAT(metric.Increment(), EqualsProto(expected_metric));
  expected_metric.set_value("5");
  (*expected_metric.mutable_labels())["EventCode"] = "event_1";
  EXPECT_THAT(metric.Increment(5, "event_1"), EqualsProto(expected_metric));
}

TEST(MetricWrapperTest, WithValueWorks) {
  auto metric = MetricWrapper(SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_COUNT
    type: METRIC_TYPE_COUNTER
    labels { key: "label_key" value: "label_value" }
  )pb"));
  auto expected_metric = metric.metric;
  expected_metric.set_value("boo");
  EXPECT_THAT(metric.WithValue("boo"), EqualsProto(expected_metric));
}

TEST(MetricWrapperTest, WithValueWorksWithEventCode) {
  auto metric = MetricWrapper(SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_COUNT
    type: METRIC_TYPE_COUNTER
    labels { key: "label_key" value: "label_value" }
  )pb"));
  auto expected_metric = metric.metric;
  expected_metric.set_value("boo");
  (*expected_metric.mutable_labels())["EventCode"] = "event_1";
  EXPECT_THAT(metric.WithValue("boo", "event_1"), EqualsProto(expected_metric));
}

class DualWritingMetricClientTest : public ScpTestBase {
 protected:
  DualWritingMetricClientTest()
      : dual_write_client_(&mock_client_, &mock_factory_, kOtelNamespace),
        legacy_client_(nullptr, &mock_factory_, kOtelNamespace),
        otel_client_(&mock_client_, nullptr, kOtelNamespace) {}

  MockMetricClient mock_client_;
  MockMetricInstanceFactory mock_factory_;
  DualWritingMetricClient dual_write_client_, legacy_client_, otel_client_;
};

MATCHER_P(MetricDefinitionEquals, expected_definition, "") {
  bool equals = true;
  if (!ExplainMatchResult(expected_definition.name, arg.name,
                          result_listener)) {
    equals = false;
  }
  if (!ExplainMatchResult(expected_definition.unit, arg.unit,
                          result_listener)) {
    equals = false;
  }
  if (expected_definition.metric_namespace) {
    if (!ExplainMatchResult(Optional(*expected_definition.metric_namespace),
                            arg.metric_namespace, result_listener)) {
      equals = false;
    }
  } else {
    if (!ExplainMatchResult(Eq(nullopt), arg.metric_namespace,
                            result_listener)) {
      equals = false;
    }
  }
  if (expected_definition.labels.size() != arg.labels.size()) {
    equals = false;
  }
  if (!std::all_of(expected_definition.labels.begin(),
                   expected_definition.labels.end(),
                   [&arg](const std::pair<string, string>& key_val) {
                     auto arg_item = arg.labels.find(key_val.first);
                     if (arg_item == arg.labels.end()) {
                       return false;
                     }
                     return arg_item->second == key_val.second;
                   })) {
    equals = false;
  }
  return equals;
}

// Used for matching MetricWrapper against Metric.
MATCHER_P(EqualsMetric, expected_metric, "") {
  return ExplainMatchResult(EqualsProto(expected_metric), arg.metric,
                            result_listener);
}

TEST_F(DualWritingMetricClientTest, CreateAggregateMetricWorks) {
  MetricDefinition metric_definition("name", MetricUnit::METRIC_UNIT_COUNT,
                                     "namespace",
                                     {{"label_key", "label_value"}});
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_COUNT
    type: METRIC_TYPE_COUNTER
    labels { key: "label_key" value: "label_value" }
  )pb");

  // One call for each client.
  auto dual_write_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* dual_write_metric_ptr = dual_write_metric.get();
  auto legacy_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* legacy_metric_ptr = legacy_metric.get();
  EXPECT_CALL(mock_factory_, ConstructAggregateMetricInstance(
                                 MetricDefinitionEquals(metric_definition)))
      .WillOnce(Return(ByMove(move(dual_write_metric))))
      .WillOnce(Return(ByMove(move(legacy_metric))));

  EXPECT_THAT(dual_write_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(legacy_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(otel_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_CALL(*dual_write_metric_ptr, Init)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(dual_write_client_.InitMetric("name", "", ""));
  EXPECT_CALL(*dual_write_metric_ptr, Run)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(dual_write_client_.RunMetric("name", "", ""));
  EXPECT_CALL(*dual_write_metric_ptr, Stop)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(dual_write_client_.StopMetric("name", "", ""));

  EXPECT_CALL(*legacy_metric_ptr, Init)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.InitMetric("name", "", ""));
  EXPECT_CALL(*legacy_metric_ptr, Run)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.RunMetric("name", "", ""));
  EXPECT_CALL(*legacy_metric_ptr, Stop)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.StopMetric("name", "", ""));
}

TEST_F(DualWritingMetricClientTest, CreateAggregateMetricWithEventCodes) {
  MetricDefinition metric_definition("name", MetricUnit::METRIC_UNIT_COUNT,
                                     "namespace",
                                     {{"label_key", "label_value"}});
  vector<string> event_codes{"event_1", "event_2"};
  // One call for each client.
  EXPECT_CALL(mock_factory_, ConstructAggregateMetricInstance(
                                 MetricDefinitionEquals(metric_definition),
                                 UnorderedElementsAre("event_1", "event_2")))
      .WillOnce(Return(ByMove(make_unique<NiceMock<MockAggregateMetric>>())))
      .WillOnce(Return(ByMove(make_unique<NiceMock<MockAggregateMetric>>())));
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_COUNT
    type: METRIC_TYPE_COUNTER
    labels { key: "label_key" value: "label_value" })pb");

  EXPECT_THAT(
      dual_write_client_.CreateAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));

  EXPECT_THAT(
      legacy_client_.CreateAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));

  EXPECT_THAT(
      otel_client_.CreateAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));
}

TEST_F(DualWritingMetricClientTest,
       CreateAggregateMetricWithComponentAndMethodNameWorks) {
  MetricDefinition metric_definition(
      "name", MetricUnit::METRIC_UNIT_COUNT, "namespace",
      MetricUtils::CreateMetricLabelsWithComponentSignature("component",
                                                            "method"));
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_COUNT
    type: METRIC_TYPE_COUNTER
    labels { key: "ComponentName" value: "component" }
    labels { key: "MethodName" value: "method" }
  )pb");

  // One call for each client.
  auto dual_write_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* dual_write_metric_ptr = dual_write_metric.get();
  auto legacy_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* legacy_metric_ptr = legacy_metric.get();
  EXPECT_CALL(mock_factory_, ConstructAggregateMetricInstance(
                                 MetricDefinitionEquals(metric_definition)))
      .WillOnce(Return(ByMove(move(dual_write_metric))))
      .WillOnce(Return(ByMove(move(legacy_metric))));

  EXPECT_THAT(dual_write_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(legacy_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(otel_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_CALL(*dual_write_metric_ptr, Init)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(dual_write_client_.InitMetric("name", "component", "method"));
  EXPECT_CALL(*dual_write_metric_ptr, Run)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(dual_write_client_.RunMetric("name", "component", "method"));
  EXPECT_CALL(*dual_write_metric_ptr, Stop)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(dual_write_client_.StopMetric("name", "component", "method"));

  EXPECT_CALL(*legacy_metric_ptr, Init)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.InitMetric("name", "component", "method"));
  EXPECT_CALL(*legacy_metric_ptr, Run)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.RunMetric("name", "component", "method"));
  EXPECT_CALL(*legacy_metric_ptr, Stop)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.StopMetric("name", "component", "method"));
}

TEST_F(DualWritingMetricClientTest, CreateTimeAggregateMetricWorks) {
  MetricDefinition metric_definition("name", MetricUnit::METRIC_UNIT_SECONDS,
                                     "namespace",
                                     {{"label_key", "label_value"}});
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_SECONDS
    type: METRIC_TYPE_HISTOGRAM
    labels { key: "label_key" value: "label_value" }
  )pb");

  // One call for each client.
  auto dual_write_metric = make_unique<NiceMock<MockTimeAggregateMetric>>();
  auto* dual_write_metric_ptr = dual_write_metric.get();
  auto legacy_metric = make_unique<NiceMock<MockTimeAggregateMetric>>();
  auto* legacy_metric_ptr = legacy_metric.get();
  EXPECT_CALL(mock_factory_, ConstructTimeAggregateMetricInstance(
                                 MetricDefinitionEquals(metric_definition)))
      .WillOnce(Return(ByMove(move(dual_write_metric))))
      .WillOnce(Return(ByMove(move(legacy_metric))));

  EXPECT_THAT(dual_write_client_.CreateTimeAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(legacy_client_.CreateTimeAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(otel_client_.CreateTimeAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_CALL(*dual_write_metric_ptr, Init)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(dual_write_client_.InitMetric("name", "", ""));
  EXPECT_CALL(*dual_write_metric_ptr, Run)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(dual_write_client_.RunMetric("name", "", ""));
  EXPECT_CALL(*dual_write_metric_ptr, Stop)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(dual_write_client_.StopMetric("name", "", ""));

  EXPECT_CALL(*legacy_metric_ptr, Init)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.InitMetric("name", "", ""));
  EXPECT_CALL(*legacy_metric_ptr, Run)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.RunMetric("name", "", ""));
  EXPECT_CALL(*legacy_metric_ptr, Stop)
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.StopMetric("name", "", ""));
}

TEST_F(DualWritingMetricClientTest, CreateTimeAggregateMetricWithEventCodes) {
  MetricDefinition metric_definition("name", MetricUnit::METRIC_UNIT_SECONDS,
                                     "namespace",
                                     {{"label_key", "label_value"}});
  vector<string> event_codes{"event_1", "event_2"};
  // One call for each client.
  EXPECT_CALL(mock_factory_, ConstructTimeAggregateMetricInstance(
                                 MetricDefinitionEquals(metric_definition),
                                 UnorderedElementsAre("event_1", "event_2")))
      .WillOnce(
          Return(ByMove(make_unique<NiceMock<MockTimeAggregateMetric>>())))
      .WillOnce(
          Return(ByMove(make_unique<NiceMock<MockTimeAggregateMetric>>())));
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_SECONDS
    type: METRIC_TYPE_HISTOGRAM
    labels { key: "label_key" value: "label_value" })pb");

  EXPECT_THAT(dual_write_client_.CreateTimeAggregateMetric(metric_definition,
                                                           event_codes),
              EqualsMetric(expected_metric));

  EXPECT_THAT(
      legacy_client_.CreateTimeAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));

  EXPECT_THAT(
      otel_client_.CreateTimeAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));
}

TEST_F(DualWritingMetricClientTest, PutMetricWorksWithAggregateMetric) {
  MetricDefinition metric_definition("name", MetricUnit::METRIC_UNIT_COUNT,
                                     "namespace",
                                     {{"label_key", "label_value"}});
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_COUNT
    type: METRIC_TYPE_COUNTER
    labels { key: "label_key" value: "label_value" }
  )pb");

  auto dual_write_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* dual_write_metric_ptr = dual_write_metric.get();
  auto legacy_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* legacy_metric_ptr = legacy_metric.get();
  // One call for each client.
  EXPECT_CALL(mock_factory_, ConstructAggregateMetricInstance(
                                 MetricDefinitionEquals(metric_definition)))
      .WillOnce(Return(ByMove(move(dual_write_metric))))
      .WillOnce(Return(ByMove(move(legacy_metric))));

  EXPECT_THAT(dual_write_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(legacy_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(otel_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  // For dual write we expect a call to IncrementBy on the mock metric, and a
  // call to PutMetricsSync on the mock client.
  expected_metric.set_value("5");
  EXPECT_CALL(*dual_write_metric_ptr, IncrementBy(5, string()))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace, expected_metric.DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(dual_write_client_.PutMetric(expected_metric));

  // For legacy client we expect just a call to IncrementBy
  EXPECT_CALL(*legacy_metric_ptr, IncrementBy(5, string()))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.PutMetric(expected_metric));

  // For otel client we expect just a call to PutMetricsSync
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace, expected_metric.DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(otel_client_.PutMetric(expected_metric));
}

TEST_F(DualWritingMetricClientTest,
       PutMetricWorksWithAggregateMetricWithComponentAndMethod) {
  MetricDefinition metric_definition(
      "name", MetricUnit::METRIC_UNIT_COUNT, "namespace",
      MetricUtils::CreateMetricLabelsWithComponentSignature("component",
                                                            "method"));
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_COUNT
    type: METRIC_TYPE_COUNTER
    labels { key: "ComponentName" value: "component" }
    labels { key: "MethodName" value: "method" }
  )pb");

  auto dual_write_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* dual_write_metric_ptr = dual_write_metric.get();
  auto legacy_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* legacy_metric_ptr = legacy_metric.get();
  // One call for each client.
  EXPECT_CALL(mock_factory_, ConstructAggregateMetricInstance(
                                 MetricDefinitionEquals(metric_definition)))
      .WillOnce(Return(ByMove(move(dual_write_metric))))
      .WillOnce(Return(ByMove(move(legacy_metric))));

  EXPECT_THAT(dual_write_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(legacy_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(otel_client_.CreateAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  // For dual write we expect a call to IncrementBy on the mock metric, and a
  // call to PutMetricsSync on the mock client.
  expected_metric.set_value("5");
  EXPECT_CALL(*dual_write_metric_ptr, IncrementBy(5, string()))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace, expected_metric.DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(dual_write_client_.PutMetric(expected_metric));

  // For legacy client we expect just a call to IncrementBy
  EXPECT_CALL(*legacy_metric_ptr, IncrementBy(5, string()))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.PutMetric(expected_metric));

  // For otel client we expect just a call to PutMetricsSync
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace, expected_metric.DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(otel_client_.PutMetric(expected_metric));
}

TEST_F(DualWritingMetricClientTest,
       PutMetricWorksWithAggregateMetricWithLabels) {
  MetricDefinition metric_definition("name", MetricUnit::METRIC_UNIT_COUNT,
                                     "namespace",
                                     {{"label_key", "label_value"}});
  vector<string> event_codes{"event_1", "event_2"};
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_COUNT
    type: METRIC_TYPE_COUNTER
    labels { key: "label_key" value: "label_value" }
  )pb");

  auto dual_write_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* dual_write_metric_ptr = dual_write_metric.get();
  auto legacy_metric = make_unique<NiceMock<MockAggregateMetric>>();
  auto* legacy_metric_ptr = legacy_metric.get();
  // One call for each client.
  EXPECT_CALL(mock_factory_,
              ConstructAggregateMetricInstance(
                  MetricDefinitionEquals(metric_definition), event_codes))
      .WillOnce(Return(ByMove(move(dual_write_metric))))
      .WillOnce(Return(ByMove(move(legacy_metric))));

  EXPECT_THAT(
      dual_write_client_.CreateAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));

  EXPECT_THAT(
      legacy_client_.CreateAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));

  EXPECT_THAT(
      otel_client_.CreateAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));

  MetricWrapper metric_wrapper(expected_metric);
  // For dual write we expect a call to IncrementBy on the mock metric, and a
  // call to PutMetric on the mock client.
  EXPECT_CALL(*dual_write_metric_ptr, IncrementBy(5, "event_1"))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace,
                      metric_wrapper.Increment(5, "event_1").DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(
      dual_write_client_.PutMetric(metric_wrapper.Increment(5, "event_1")));
  EXPECT_CALL(*dual_write_metric_ptr, IncrementBy(10, "event_2"))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace,
                      metric_wrapper.Increment(10, "event_2").DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(
      dual_write_client_.PutMetric(metric_wrapper.Increment(10, "event_2")));

  // For legacy client we expect just a call to IncrementBy
  EXPECT_CALL(*legacy_metric_ptr, IncrementBy(5, "event_1"))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(
      legacy_client_.PutMetric(metric_wrapper.Increment(5, "event_1")));
  EXPECT_CALL(*legacy_metric_ptr, IncrementBy(10, "event_2"))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(
      legacy_client_.PutMetric(metric_wrapper.Increment(10, "event_2")));

  // For otel client we expect just a call to PutMetricsSync
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace,
                      metric_wrapper.Increment(5, "event_1").DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(
      otel_client_.PutMetric(metric_wrapper.Increment(5, "event_1")));
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace,
                      metric_wrapper.Increment(10, "event_2").DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(
      otel_client_.PutMetric(metric_wrapper.Increment(10, "event_2")));
}

TEST_F(DualWritingMetricClientTest, PutMetricWorksWithTimeAggregateMetric) {
  MetricDefinition metric_definition("name", MetricUnit::METRIC_UNIT_SECONDS,
                                     "namespace",
                                     {{"label_key", "label_value"}});
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_SECONDS
    type: METRIC_TYPE_HISTOGRAM
    labels { key: "label_key" value: "label_value" }
  )pb");

  auto dual_write_metric = make_unique<NiceMock<MockTimeAggregateMetric>>();
  auto* dual_write_metric_ptr = dual_write_metric.get();
  auto legacy_metric = make_unique<NiceMock<MockTimeAggregateMetric>>();
  auto* legacy_metric_ptr = legacy_metric.get();
  // One call for each client.
  EXPECT_CALL(mock_factory_, ConstructTimeAggregateMetricInstance(
                                 MetricDefinitionEquals(metric_definition)))
      .WillOnce(Return(ByMove(move(dual_write_metric))))
      .WillOnce(Return(ByMove(move(legacy_metric))));

  EXPECT_THAT(dual_write_client_.CreateTimeAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(legacy_client_.CreateTimeAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  EXPECT_THAT(otel_client_.CreateTimeAggregateMetric(metric_definition),
              EqualsMetric(expected_metric));

  // For dual write we expect a call to RecordDuration on the mock metric, and a
  // call to PutMetricsSync on the mock client.
  expected_metric.set_value("5");
  EXPECT_CALL(*dual_write_metric_ptr, RecordDuration(5, string()))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace, expected_metric.DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(dual_write_client_.PutMetric(expected_metric));

  // For legacy client we expect just a call to RecordDuration
  EXPECT_CALL(*legacy_metric_ptr, RecordDuration(5, string()))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(legacy_client_.PutMetric(expected_metric));

  // For otel client we expect just a call to PutMetricsSync
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace, expected_metric.DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(otel_client_.PutMetric(expected_metric));
}

TEST_F(DualWritingMetricClientTest,
       PutMetricWorksWithTimeAggregateMetricWithLabels) {
  MetricDefinition metric_definition("name", MetricUnit::METRIC_UNIT_SECONDS,
                                     "namespace",
                                     {{"label_key", "label_value"}});
  vector<string> event_codes{"event_1", "event_2"};
  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "name"
    unit: METRIC_UNIT_SECONDS
    type: METRIC_TYPE_HISTOGRAM
    labels { key: "label_key" value: "label_value" }
  )pb");
  auto dual_write_metric = make_unique<NiceMock<MockTimeAggregateMetric>>();
  auto* dual_write_metric_ptr = dual_write_metric.get();
  auto legacy_metric = make_unique<NiceMock<MockTimeAggregateMetric>>();
  auto* legacy_metric_ptr = legacy_metric.get();
  // One call for each client.
  EXPECT_CALL(mock_factory_,
              ConstructTimeAggregateMetricInstance(
                  MetricDefinitionEquals(metric_definition), event_codes))
      .WillOnce(Return(ByMove(move(dual_write_metric))))
      .WillOnce(Return(ByMove(move(legacy_metric))));

  EXPECT_THAT(dual_write_client_.CreateTimeAggregateMetric(metric_definition,
                                                           event_codes),
              EqualsMetric(expected_metric));

  EXPECT_THAT(
      legacy_client_.CreateTimeAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));

  EXPECT_THAT(
      otel_client_.CreateTimeAggregateMetric(metric_definition, event_codes),
      EqualsMetric(expected_metric));

  MetricWrapper metric_wrapper(expected_metric);
  // For dual write we expect a call to RecordDuration on the mock metric, and a
  // call to PutMetricsSync on the mock client.
  EXPECT_CALL(*dual_write_metric_ptr, RecordDuration(5, "event_1"))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace,
                      metric_wrapper.Increment(5, "event_1").DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(
      dual_write_client_.PutMetric(metric_wrapper.Increment(5, "event_1")));
  EXPECT_CALL(*dual_write_metric_ptr, RecordDuration(10, "event_2"))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace,
                      metric_wrapper.Increment(10, "event_2").DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(
      dual_write_client_.PutMetric(metric_wrapper.Increment(10, "event_2")));

  // For legacy client we expect just a call to RecordDuration
  EXPECT_CALL(*legacy_metric_ptr, RecordDuration(5, "event_1"))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(
      legacy_client_.PutMetric(metric_wrapper.Increment(5, "event_1")));
  EXPECT_CALL(*legacy_metric_ptr, RecordDuration(10, "event_2"))
      .WillOnce(Return(core::SuccessExecutionResult()));
  EXPECT_SUCCESS(
      legacy_client_.PutMetric(metric_wrapper.Increment(10, "event_2")));

  // For otel client we expect just a call to PutMetricsSync
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace,
                      metric_wrapper.Increment(5, "event_1").DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(
      otel_client_.PutMetric(metric_wrapper.Increment(5, "event_1")));
  EXPECT_CALL(mock_client_,
              PutMetricsSync(
                  EqualsProto(SubstituteAndParseTextToProto<PutMetricsRequest>(
                      R"pb(
                        metric_namespace: "$0" metrics { $1 }
                      )pb",
                      kOtelNamespace,
                      metric_wrapper.Increment(10, "event_2").DebugString()))))
      .WillOnce(Return(PutMetricsResponse()));
  EXPECT_SUCCESS(
      otel_client_.PutMetric(metric_wrapper.Increment(10, "event_2")));
}

}  // namespace google::scp::cpio
