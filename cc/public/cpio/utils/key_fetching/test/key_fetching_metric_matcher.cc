// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "key_fetching_metric_matcher.h"

using testing::Return;

namespace google::scp::cpio {
namespace {
MATCHER_P(MetricNameEqual, metric_name, "") {
  return testing::ExplainMatchResult(metric_name, arg.name(), result_listener);
}
}  // namespace

void ExpectOtelKeyFetchingErrorMetricPush(
    DualWritingMetricClientMock& mock_metric_client, int call_count,
    absl::string_view key_type, absl::string_view key_fetching_type,
    absl::string_view keyset_name, absl::string_view error_code) {
  if (call_count == 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(MetricNameEqual("KeyFetchingErrorRate")))
        .Times(0);
  } else if (call_count < 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyFetchingErrorMetricEqual(
                    key_type, key_fetching_type, keyset_name, error_code)))
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  } else {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyFetchingErrorMetricEqual(
                    key_type, key_fetching_type, keyset_name, error_code)))
        .Times(call_count)
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  }
}

void ExpectOtelKeyFetchingRequestMetricPush(
    DualWritingMetricClientMock& mock_metric_client, int call_count,
    absl::string_view key_type, absl::string_view key_fetching_type,
    absl::string_view keyset_name) {
  if (call_count == 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(MetricNameEqual("KeyFetchingRequestRate")))
        .Times(0);
  } else if (call_count < 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyFetchingRequestMetricEqual(
                    key_type, key_fetching_type, keyset_name)))
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  } else {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyFetchingRequestMetricEqual(
                    key_type, key_fetching_type, keyset_name)))
        .Times(call_count)
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  }
}

void ExpectOtelKeyFetchingLatencyMetricPush(
    DualWritingMetricClientMock& mock_metric_client, int call_count,
    absl::string_view key_type, absl::string_view key_fetching_type,
    absl::string_view keyset_name) {
  if (call_count == 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(MetricNameEqual("KeyFetchingLatencyInMillis")))
        .Times(0);
  } else if (call_count < 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyFetchingLatencyMetricEqual(
                    key_type, key_fetching_type, keyset_name, "")))
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  } else {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyFetchingLatencyMetricEqual(
                    key_type, key_fetching_type, keyset_name, "")))
        .Times(call_count)
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  }
}

void ExpectOtelKeyCacheStatusMetricPush(
    DualWritingMetricClientMock& mock_metric_client, int call_count,
    absl::string_view key_type, absl::string_view keyset_name,
    absl::string_view key_cache_status) {
  if (call_count == 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(MetricNameEqual("KeyCacheStatus")))
        .Times(0);
  } else if (call_count < 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyCacheStatusMetricEqual(key_type, keyset_name,
                                                        key_cache_status)))
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  } else {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyCacheStatusMetricEqual(key_type, keyset_name,
                                                        key_cache_status)))
        .Times(call_count)
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  }
}

void ExpectOtelKeyAgeInDaysMetricPush(
    DualWritingMetricClientMock& mock_metric_client, int call_count,
    absl::string_view key_type, absl::string_view key_fetching_type,
    absl::string_view keyset_name, int16_t key_age_in_days) {
  if (call_count == 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(MetricNameEqual("KeyAgeInDays")))
        .Times(0);
  } else if (call_count < 0) {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyAgeInDaysMetricEqual(
                    key_type, key_fetching_type, keyset_name,
                    absl::StrCat(key_age_in_days))))
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  } else {
    EXPECT_CALL(mock_metric_client,
                PutOtelMetric(KeyAgeInDaysMetricEqual(
                    key_type, key_fetching_type, keyset_name,
                    absl::StrCat(key_age_in_days))))
        .Times(call_count)
        .WillRepeatedly(Return(core::SuccessExecutionResult()));
  }
}

}  // namespace google::scp::cpio
