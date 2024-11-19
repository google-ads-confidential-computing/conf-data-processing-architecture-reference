// Copyright 2022 Google LLC
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

#include "public/cpio/utils/metric_instance/src/time_aggregate_metric.h"

#include <gtest/gtest.h>

#include <atomic>
#include <chrono>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "core/async_executor/mock/mock_async_executor.h"
#include "core/async_executor/src/async_executor.h"
#include "core/interface/async_context.h"
#include "core/test/utils/conditional_wait.h"
#include "public/core/interface/execution_result.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/mock/metric_client/mock_metric_client.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"
#include "public/cpio/utils/metric_instance/interface/type_def.h"
#include "public/cpio/utils/metric_instance/mock/mock_time_aggregate_metric_with_overrides.h"

using google::cmrt::sdk::metric_service::v1::Metric;
using google::cmrt::sdk::metric_service::v1::MetricUnit;
using google::cmrt::sdk::metric_service::v1::PutMetricsRequest;
using google::cmrt::sdk::metric_service::v1::PutMetricsResponse;
using google::scp::core::AsyncContext;
using google::scp::core::AsyncExecutor;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::AsyncOperation;
using google::scp::core::AverageTimeDuration;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::RetryExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::TimeDuration;
using google::scp::core::Timestamp;
using google::scp::core::async_executor::mock::MockAsyncExecutor;
using google::scp::core::errors::SC_CUSTOMIZED_METRIC_NOT_RUNNING;
using google::scp::core::errors::SC_CUSTOMIZED_METRIC_PUSH_CANNOT_SCHEDULE;
using google::scp::core::test::ResultIs;
using google::scp::core::test::WaitUntil;
using google::scp::cpio::MetricDefinition;
using std::atomic;
using std::make_pair;
using std::make_shared;
using std::make_unique;
using std::map;
using std::mutex;
using std::shared_ptr;
using std::static_pointer_cast;
using std::string;
using std::thread;
using std::to_string;
using std::vector;
using testing::AllOf;
using testing::Eq;
using testing::FieldsAre;

namespace {
constexpr char kMetricName[] = "JobRequestCount";
constexpr char kNamespace[] = "JobClient";
constexpr TimeDuration kDuration1 = 5000;
constexpr TimeDuration kDuration2 = 3000;
constexpr AverageTimeDuration kAverageDuration = 4000;
const vector<string> kEventList = {"QPS", "Errors"};

MetricDefinition CreateMetricDefinition() {
  return MetricDefinition(kMetricName, MetricUnit::METRIC_UNIT_COUNT,
                          kNamespace, {});
}

}  // namespace

namespace google::scp::cpio {

class TimeAggregateMetricTest : public testing::Test {
 protected:
  TimeAggregateMetricTest() {
    mock_metric_client_ = make_shared<MockMetricClient>();
    mock_async_executor_ = make_shared<MockAsyncExecutor>();
    async_executor_ = mock_async_executor_;
  }

  shared_ptr<MetricClientInterface> mock_metric_client_;
  size_t aggregation_time_duration_in_ms_ = 5000;
  shared_ptr<AsyncExecutorInterface> async_executor_;
  shared_ptr<MockAsyncExecutor> mock_async_executor_;
};

TEST_F(TimeAggregateMetricTest, Run) {
  vector<ExecutionResult> results = {SuccessExecutionResult(),
                                     FailureExecutionResult(123),
                                     RetryExecutionResult(123)};

  for (auto result : results) {
    auto timeaggregate_metric = MockTimeAggregateMetricOverrides(
        async_executor_.get(), mock_metric_client_.get(),
        CreateMetricDefinition(), aggregation_time_duration_in_ms_);

    timeaggregate_metric.schedule_metric_push_mock = [&]() { return result; };
    EXPECT_THAT(timeaggregate_metric.Run(), ResultIs(result));
  }
}

TEST_F(TimeAggregateMetricTest, ScheduleMetricPush) {
  atomic<int> schedule_for_is_called = 0;
  mock_async_executor_->schedule_for_mock = [&](const AsyncOperation& work,
                                                Timestamp timestamp,
                                                std::function<bool()>&) {
    schedule_for_is_called++;
    return SuccessExecutionResult();
  };

  auto time_aggregate_metric = MockTimeAggregateMetricOverrides(
      async_executor_.get(), mock_metric_client_.get(),
      CreateMetricDefinition(), aggregation_time_duration_in_ms_);
  EXPECT_THAT(
      time_aggregate_metric.ScheduleMetricPush(),
      ResultIs(FailureExecutionResult(SC_CUSTOMIZED_METRIC_NOT_RUNNING)));

  EXPECT_SUCCESS(time_aggregate_metric.Run());
  EXPECT_SUCCESS(time_aggregate_metric.ScheduleMetricPush());
  WaitUntil([&]() { return schedule_for_is_called.load() == 2; });
}

TEST_F(TimeAggregateMetricTest, RunMetricPush) {
  auto time_aggregate_metric = MockTimeAggregateMetricOverrides(
      async_executor_.get(), mock_metric_client_.get(),
      CreateMetricDefinition(), aggregation_time_duration_in_ms_, kEventList);

  int metric_push_handler_is_called = 0;
  AverageTimeDuration average_time_for_all_events = 0;
  time_aggregate_metric.metric_push_handler_mock =
      [&](const AverageTimeDuration& average_time_duration,
          const MetricDefinition& metric_info) {
        metric_push_handler_is_called += 1;
        average_time_for_all_events += average_time_duration;
      };

  for (const auto& code : kEventList) {
    EXPECT_SUCCESS(time_aggregate_metric.RecordDuration(kDuration1, code));
    EXPECT_SUCCESS(time_aggregate_metric.RecordDuration(kDuration2));
    EXPECT_THAT(time_aggregate_metric.GetCounter(code),
                FieldsAre(1, kDuration1));
  }
  EXPECT_THAT(time_aggregate_metric.GetCounter(), FieldsAre(2, kDuration2));

  time_aggregate_metric.RunMetricPush();

  for (const auto& code : kEventList) {
    EXPECT_THAT(time_aggregate_metric.GetCounter(code), FieldsAre(0, 0));
  }
  EXPECT_THAT(time_aggregate_metric.GetCounter(), FieldsAre(0, 0));
  EXPECT_EQ(metric_push_handler_is_called, 3);
  EXPECT_EQ((kDuration1 + kDuration2) / 2, kAverageDuration);
}

TEST_F(TimeAggregateMetricTest, RunMetricPushHandler) {
  auto mock_metric_client = make_shared<MockMetricClient>();
  auto time_duration = 1000;

  auto mock_async_executor = make_shared<MockAsyncExecutor>();

  shared_ptr<AsyncExecutorInterface> async_executor =
      static_pointer_cast<AsyncExecutorInterface>(mock_async_executor);

  Metric metric_received;
  int metric_push_is_called = 0;

  EXPECT_CALL(*mock_metric_client, PutMetrics)
      .Times(3)
      .WillRepeatedly([&](auto context) {
        metric_push_is_called += 1;
        metric_received.CopyFrom(context.request->metrics()[0]);
        context.result = FailureExecutionResult(123);
        context.Finish();
        return context.result;
      });

  auto metric_info = CreateMetricDefinition();
  auto time_aggregate_metric = MockTimeAggregateMetricOverrides(
      async_executor.get(), mock_metric_client.get(), metric_info,
      time_duration, kEventList);

  for (const auto& code : kEventList) {
    auto info = time_aggregate_metric.GetMetricInfo(code);
    EXPECT_SUCCESS(info.result());
    time_aggregate_metric.MetricPushHandler(kDuration1, info.value());
    EXPECT_EQ(metric_received.name(), kMetricName);
    EXPECT_EQ(metric_received.labels().find("EventCode")->second, code);
    EXPECT_EQ(metric_received.value(),
              to_string(AverageTimeDuration(kDuration1)));
  }

  time_aggregate_metric.MetricPushHandler(kDuration1, metric_info);
  EXPECT_EQ(metric_received.name(), kMetricName);
  EXPECT_EQ(metric_received.labels().size(), 0);
  EXPECT_EQ(metric_received.value(),
            to_string(AverageTimeDuration(kDuration1)));
  WaitUntil([&]() { return metric_push_is_called == 3; });
}

TEST_F(TimeAggregateMetricTest, RecordDuration) {
  auto time_aggregate_metric = MockTimeAggregateMetricOverrides(
      async_executor_.get(), mock_metric_client_.get(),
      CreateMetricDefinition(), aggregation_time_duration_in_ms_, kEventList);

  auto value = 1;
  for (const auto& code : kEventList) {
    for (auto i = 0; i < value; i++) {
      EXPECT_SUCCESS(time_aggregate_metric.RecordDuration(kDuration1, code));
    }
    EXPECT_THAT(time_aggregate_metric.GetCounter(code),
                FieldsAre(value, kDuration1));
    value++;
  }
}

TEST_F(TimeAggregateMetricTest, RecordDurationMultipleThreads) {
  auto time_aggregate_metric = MockTimeAggregateMetricOverrides(
      async_executor_.get(), mock_metric_client_.get(),
      CreateMetricDefinition(), aggregation_time_duration_in_ms_, kEventList);
  auto num_threads = 2;
  auto num_calls = 10;
  vector<thread> threads;

  for (auto i = 0; i < num_threads; ++i) {
    threads.push_back(thread([&]() {
      for (auto j = 0; j < num_calls; j++) {
        for (const auto& code : kEventList) {
          EXPECT_SUCCESS(
              time_aggregate_metric.RecordDuration(kDuration1, code));
        }
      }
    }));
  }
  for (auto& thread : threads) {
    thread.join();
  }

  for (const auto& code : kEventList) {
    EXPECT_THAT(time_aggregate_metric.GetCounter(code),
                FieldsAre(num_threads * num_calls, AllOf(Eq(kDuration1))));
  }
}

TEST_F(TimeAggregateMetricTest, StopShouldNotDiscardAnyCounters) {
  auto real_async_executor = make_shared<AsyncExecutor>(
      2 /* thread count */, 1000 /* queue capacity */,
      true /* drop tasks on stop*/);
  EXPECT_SUCCESS(real_async_executor->Init());
  EXPECT_SUCCESS(real_async_executor->Run());

  auto time_aggregate_metric = MockTimeAggregateMetricOverrides(
      real_async_executor.get(), mock_metric_client_.get(),
      CreateMetricDefinition(), aggregation_time_duration_in_ms_, kEventList);

  EXPECT_SUCCESS(time_aggregate_metric.Init());
  EXPECT_SUCCESS(time_aggregate_metric.Run());

  auto value = 1;
  for (const auto& code : kEventList) {
    for (auto i = 0; i < value; i++) {
      EXPECT_SUCCESS(time_aggregate_metric.RecordDuration(kDuration1, code));
    }
    EXPECT_THAT(time_aggregate_metric.GetCounter(code),
                FieldsAre(value, kDuration1));
    value++;
  }

  EXPECT_SUCCESS(time_aggregate_metric.Stop());

  // Counters should be 0
  for (const auto& event_code : kEventList) {
    EXPECT_THAT(time_aggregate_metric.GetCounter(event_code), FieldsAre(0, 0));
  }

  EXPECT_SUCCESS(real_async_executor->Stop());
}

TEST(CalculateAverageDurationTest, CalculateAverageDuration) {
  EXPECT_THAT(CalculateAverageDuration(100, 10, 80), (1000 + 80) / 101.0);
}
}  // namespace google::scp::cpio
