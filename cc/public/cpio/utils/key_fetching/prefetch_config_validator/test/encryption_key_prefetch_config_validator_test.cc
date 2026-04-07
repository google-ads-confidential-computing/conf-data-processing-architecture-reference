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

#include "public/cpio/utils/key_fetching/prefetch_config_validator/src/encryption_key_prefetch_config_validator.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <string_view>

#include "core/test/utils/proto_test_utils.h"
#include "core/test/utils/scp_test_base.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/utils/dual_writing_metric_client/mock/dual_writing_metric_client_mock.h"
#include "public/cpio/utils/key_fetching/prefetch_config_validator/src/error_codes.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"

using google::cmrt::sdk::metric_service::v1::Metric;
using google::cmrt::sdk::v1::EncryptionKeyPrefetchConfig;
using google::protobuf::TextFormat;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::test::EqualsProto;
using google::scp::core::test::ResultIs;
using google::scp::core::test::ScpTestBase;
using google::scp::core::test::SubstituteAndParseTextToProto;
using std::string_view;
using testing::_;
using testing::NiceMock;
using testing::Pair;
using testing::Return;
using testing::UnorderedElementsAre;

namespace google::scp::cpio::test {
namespace {

class EncryptionKeyPrefetchConfigValidatorTest : public ScpTestBase {
 protected:
  EncryptionKeyPrefetchConfigValidatorTest()
      : validator_(mock_metric_client_, "test_metric_namespace") {
    ON_CALL(mock_metric_client_, PutOtelMetric(_))
        .WillByDefault(Return(SuccessExecutionResult()));
  }

  ~EncryptionKeyPrefetchConfigValidatorTest() override {}

  NiceMock<DualWritingMetricClientMock> mock_metric_client_;
  EncryptionKeyPrefetchConfigValidator validator_;
};

TEST_F(EncryptionKeyPrefetchConfigValidatorTest, ValidWithOnlyKeyIds) {
  string_view text_proto = R"pb(
    keyset_prefetch_configs { key_namespace: "namespace1" key_ids: "id1" }
  )pb";
  EXPECT_CALL(mock_metric_client_, PutOtelMetric(_)).Times(0);

  auto result = validator_.ParseValidateAndBuildMap(text_proto);
  EXPECT_SUCCESS(result.result());

  auto config_map = *result;

  auto expected_config = SubstituteAndParseTextToProto<
      EncryptionKeyPrefetchConfig::KeysetPrefetchConfig>(R"pb(
    key_namespace: "namespace1"
    key_ids: "id1"
  )pb");

  EXPECT_THAT(config_map, UnorderedElementsAre(Pair(
                              "namespace1", EqualsProto(expected_config))));
}

TEST_F(EncryptionKeyPrefetchConfigValidatorTest,
       ValidWithOnlyPrefetchDuration) {
  string_view text_proto = R"pb(
    keyset_prefetch_configs {
      key_namespace: "namespace1"
      prefetch_duration { seconds: 60 }
    }
  )pb";
  EXPECT_CALL(mock_metric_client_, PutOtelMetric(_)).Times(0);

  auto result = validator_.ParseValidateAndBuildMap(text_proto);
  EXPECT_SUCCESS(result.result());

  auto config_map = *result;

  auto expected_config = SubstituteAndParseTextToProto<
      EncryptionKeyPrefetchConfig::KeysetPrefetchConfig>(R"pb(
    key_namespace: "namespace1"
    prefetch_duration { seconds: 60 }
  )pb");

  EXPECT_THAT(config_map, UnorderedElementsAre(Pair(
                              "namespace1", EqualsProto(expected_config))));
}

TEST_F(EncryptionKeyPrefetchConfigValidatorTest,
       ValidWithBothKeyIdsAndPrefetchDuration) {
  string_view text_proto = R"pb(
    keyset_prefetch_configs {
      key_namespace: "namespace1"
      key_ids: "id1"
      prefetch_duration { seconds: 60 }
    }
  )pb";
  EXPECT_CALL(mock_metric_client_, PutOtelMetric(_)).Times(0);

  auto result = validator_.ParseValidateAndBuildMap(text_proto);
  EXPECT_SUCCESS(result.result());

  auto config_map = *result;

  auto expected_config = SubstituteAndParseTextToProto<
      EncryptionKeyPrefetchConfig::KeysetPrefetchConfig>(R"pb(
    key_namespace: "namespace1"
    key_ids: "id1"
    prefetch_duration { seconds: 60 }
  )pb");

  EXPECT_THAT(config_map, UnorderedElementsAre(Pair(
                              "namespace1", EqualsProto(expected_config))));
}

TEST_F(EncryptionKeyPrefetchConfigValidatorTest,
       ValidWithMultipleKeysetConfigs) {
  string_view text_proto = R"pb(
    keyset_prefetch_configs {
      key_namespace: "namespace1"
      key_ids: "id1"
      prefetch_duration { seconds: 60 }
    }
    keyset_prefetch_configs {
      key_namespace: "namespace2"
      key_ids: "id2"
      key_ids: "id3"
    }
  )pb";
  EXPECT_CALL(mock_metric_client_, PutOtelMetric(_)).Times(0);

  auto result = validator_.ParseValidateAndBuildMap(text_proto);
  EXPECT_SUCCESS(result.result());

  auto config_map = *result;

  auto expected_config1 = SubstituteAndParseTextToProto<
      EncryptionKeyPrefetchConfig::KeysetPrefetchConfig>(R"pb(
    key_namespace: "namespace1"
    key_ids: "id1"
    prefetch_duration { seconds: 60 }
  )pb");

  auto expected_config2 = SubstituteAndParseTextToProto<
      EncryptionKeyPrefetchConfig::KeysetPrefetchConfig>(R"pb(
    key_namespace: "namespace2"
    key_ids: "id2"
    key_ids: "id3"
  )pb");

  EXPECT_THAT(
      config_map,
      UnorderedElementsAre(Pair("namespace1", EqualsProto(expected_config1)),
                           Pair("namespace2", EqualsProto(expected_config2))));
}

TEST_F(EncryptionKeyPrefetchConfigValidatorTest,
       InvalidWithNamespaceWithoutValues) {
  string_view text_proto = R"pb(
    keyset_prefetch_configs { key_namespace: "namespace1" }
  )pb";

  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "PrefetchConfigValidationErrorRate"
    type: METRIC_TYPE_COUNTER
    labels { key: "Keyset" value: "namespace1" }
    labels { key: "PrefetchConfigValidationErrorType" value: "MissingValues" }
    value: "1"
  )pb");

  EXPECT_CALL(mock_metric_client_, PutOtelMetric(EqualsProto(expected_metric)))
      .Times(1);
  EXPECT_THAT(
      validator_.ParseValidateAndBuildMap(text_proto),
      ResultIs(FailureExecutionResult(SC_CPIO_PREFETCH_CONFIG_MISSING_VALUES)));
}

TEST_F(EncryptionKeyPrefetchConfigValidatorTest, InvalidWithNoNamespace) {
  string_view text_proto = R"pb(
    keyset_prefetch_configs { key_ids: "id1" }
  )pb";

  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "PrefetchConfigValidationErrorRate"
    type: METRIC_TYPE_COUNTER
    labels { key: "Keyset" value: "null" }
    labels {
      key: "PrefetchConfigValidationErrorType"
      value: "MissingKeysetNamespace"
    }
    value: "1"
  )pb");

  EXPECT_CALL(mock_metric_client_, PutOtelMetric(EqualsProto(expected_metric)))
      .Times(1);
  EXPECT_THAT(
      validator_.ParseValidateAndBuildMap(text_proto),
      ResultIs(FailureExecutionResult(SC_CPIO_INVALID_PREFETCH_CONFIG)));
}

TEST_F(EncryptionKeyPrefetchConfigValidatorTest, InvalidWithEmptyKeyId) {
  string_view text_proto = R"pb(
    keyset_prefetch_configs { key_namespace: "namespace1" key_ids: "" }
  )pb";

  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "PrefetchConfigValidationErrorRate"
    type: METRIC_TYPE_COUNTER
    labels { key: "Keyset" value: "namespace1" }
    labels { key: "PrefetchConfigValidationErrorType" value: "EmptyKeyId" }
    value: "1"
  )pb");

  EXPECT_CALL(mock_metric_client_, PutOtelMetric(EqualsProto(expected_metric)))
      .Times(1);
  EXPECT_THAT(validator_.ParseValidateAndBuildMap(text_proto),
              ResultIs(FailureExecutionResult(
                  SC_CPIO_PREFETCH_CONFIG_INVALID_KEY_IDS)));
}

TEST_F(EncryptionKeyPrefetchConfigValidatorTest, InvalidDuration) {
  string_view text_proto = R"pb(
    keyset_prefetch_configs {
      key_namespace: "namespace1"
      prefetch_duration { seconds: -60 }
    }
  )pb";

  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "PrefetchConfigValidationErrorRate"
    type: METRIC_TYPE_COUNTER
    labels { key: "Keyset" value: "namespace1" }
    labels { key: "PrefetchConfigValidationErrorType" value: "InvalidDuration" }
    value: "1"
  )pb");

  EXPECT_CALL(mock_metric_client_, PutOtelMetric(EqualsProto(expected_metric)))
      .Times(1);
  EXPECT_THAT(validator_.ParseValidateAndBuildMap(text_proto),
              ResultIs(FailureExecutionResult(
                  SC_CPIO_PREFETCH_CONFIG_INVALID_DURATION)));
}

TEST_F(EncryptionKeyPrefetchConfigValidatorTest, InvalidDuplicateNamespace) {
  string_view text_proto = R"pb(
    keyset_prefetch_configs { key_namespace: "namespace1" key_ids: "id1" }
    keyset_prefetch_configs { key_namespace: "namespace1" key_ids: "id2" }
  )pb";

  auto expected_metric = SubstituteAndParseTextToProto<Metric>(R"pb(
    name: "PrefetchConfigValidationErrorRate"
    type: METRIC_TYPE_COUNTER
    labels { key: "Keyset" value: "namespace1" }
    labels {
      key: "PrefetchConfigValidationErrorType"
      value: "DuplicateKeysetNamespace"
    }
    value: "1"
  )pb");

  EXPECT_CALL(mock_metric_client_, PutOtelMetric(EqualsProto(expected_metric)))
      .Times(1);
  EXPECT_THAT(validator_.ParseValidateAndBuildMap(text_proto),
              ResultIs(FailureExecutionResult(
                  SC_CPIO_PREFETCH_CONFIG_DUPLICATE_NAMESPACE)));
}

}  // namespace
}  // namespace google::scp::cpio::test
