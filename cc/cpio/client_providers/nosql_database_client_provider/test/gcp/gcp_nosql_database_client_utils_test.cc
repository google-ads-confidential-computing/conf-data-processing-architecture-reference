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

#include "cpio/client_providers/nosql_database_client_provider/src/gcp/gcp_nosql_database_client_utils.h"

#include <gtest/gtest.h>

#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "cpio/client_providers/nosql_database_client_provider/src/common/error_codes.h"
#include "google/cloud/spanner/value.h"
#include "public/core/test/interface/execution_result_matchers.h"

using google::cloud::spanner::Json;
using google::cloud::spanner::Value;
using google::scp::core::SuccessExecutionResult;
using json = nlohmann::json;
using google::cmrt::sdk::nosql_database_service::v1::ItemAttribute;
using google::scp::core::FailureExecutionResult;
using google::scp::core::errors::
    SC_NO_SQL_DATABASE_PROVIDER_INVALID_JSON_STRING;
using google::scp::core::errors::
    SC_NO_SQL_DATABASE_PROVIDER_INVALID_PARAMETER_TYPE;
using google::scp::core::test::IsSuccessfulAndHolds;
using google::scp::core::test::ResultIs;
using std::get;
using std::shared_ptr;
using std::string;
using std::vector;
using testing::Eq;
using testing::ExplainMatchResult;

namespace google::scp::cpio::client_providers::test {

MATCHER_P(HasValueInt, value, "") {
  if (arg.value_case() != ItemAttribute::kValueInt) {
    *result_listener << "Expected arg to have value_int: " << value
                     << " but has:\n"
                     << arg.DebugString();
    return false;
  }
  return ExplainMatchResult(Eq(value), arg.value_int(), result_listener);
}

MATCHER_P(HasValueFloat, value, "") {
  if (arg.value_case() != ItemAttribute::kValueFloat) {
    *result_listener << "Expected arg to have value_float: " << value
                     << " but has:\n"
                     << arg.DebugString();
    return false;
  }
  return ExplainMatchResult(Eq(value), arg.value_float(), result_listener);
}

MATCHER_P(HasValueDouble, value, "") {
  if (arg.value_case() != ItemAttribute::kValueDouble) {
    *result_listener << "Expected arg to have value_double: " << value
                     << " but has:\n"
                     << arg.DebugString();
    return false;
  }
  return ExplainMatchResult(Eq(value), arg.value_double(), result_listener);
}

TEST(GcpNoSQLDatabaseClientUtilsTest, ConvertJsonTypeToItemAttributeNumber) {
  json json_val(123);

  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertJsonTypeToItemAttribute(json_val),
      IsSuccessfulAndHolds(HasValueInt(123)));

  // JSON floats are actually all doubles.
  json_val = 123.1f;
  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertJsonTypeToItemAttribute(json_val),
      IsSuccessfulAndHolds(HasValueDouble(123.1f)));

  // No suffix resolves to double.
  json_val = 123.1;
  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertJsonTypeToItemAttribute(json_val),
      IsSuccessfulAndHolds(HasValueDouble(123.1)));
}

MATCHER_P(HasValueString, value, "") {
  if (arg.value_case() != ItemAttribute::kValueString) {
    *result_listener << "Expected arg to have value_string: " << value
                     << " but has:\n"
                     << arg.DebugString();
    return false;
  }
  return ExplainMatchResult(Eq(value), arg.value_string(), result_listener);
}

TEST(GcpNoSQLDatabaseClientUtilsTest,
     ConvertJsonTypeToNoSQLDatabaseValidAttributeValueTypeString) {
  json json_val("hello world!");

  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertJsonTypeToItemAttribute(json_val),
      IsSuccessfulAndHolds(HasValueString("hello world!")));
}

MATCHER_P(HasValueJsonString, value, "") {
  if (arg.value_case() != ItemAttribute::kValueJsonString) {
    *result_listener << "Expected arg to have value_json_string: " << value
                     << " but has:\n"
                     << arg.DebugString();
    return false;
  }
  return ExplainMatchResult(Eq(value), arg.value_json_string(),
                            result_listener);
}

TEST(GcpNoSQLDatabaseClientUtilsTest,
     ConvertJsonTypeToNoSQLDatabaseValidAttributeValueTypeJsonStringType) {
  json json_val(json::object_t{{"struct_field", 1}});

  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertJsonTypeToItemAttribute(json_val),
      IsSuccessfulAndHolds(HasValueJsonString("{\"struct_field\":1}")));
}

TEST(GcpNoSQLDatabaseClientUtilsTest,
     ConvertJsonTypeToNoSQLDatabaseValidAttributeValueTypeInvalidType) {
  vector<json> invalid_value_types(
      {/*array*/ vector<string>{"item_1", "item_2"}, /*bool*/ true});

  for (const auto& value_type : invalid_value_types) {
    EXPECT_THAT(
        GcpNoSQLDatabaseClientUtils::ConvertJsonTypeToItemAttribute(value_type),
        ResultIs(FailureExecutionResult(
            SC_NO_SQL_DATABASE_PROVIDER_INVALID_PARAMETER_TYPE)));
  }
}

TEST(GcpNoSQLDatabaseClientUtilsTest, ConvertItemAttributeToJsonTypeNumber) {
  ItemAttribute attribute;
  attribute.set_value_int(1);
  json expected_value(1);
  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToJsonType(attribute),
      IsSuccessfulAndHolds(expected_value));

  attribute.set_value_float(1.5f);
  expected_value = 1.5f;
  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToJsonType(attribute),
      IsSuccessfulAndHolds(expected_value));

  attribute.set_value_double(1.5);
  expected_value = 1.5;
  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToJsonType(attribute),
      IsSuccessfulAndHolds(expected_value));
}

TEST(GcpNoSQLDatabaseClientUtilsTest, ConvertItemAttributeJsonTypeString) {
  ItemAttribute attribute;
  attribute.set_value_string("hello world!");
  json expected_value("hello world!");
  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToJsonType(attribute),
      IsSuccessfulAndHolds(expected_value));
}

TEST(GcpNoSQLDatabaseClientUtilsTest, ConvertItemAttributeJsonTypeJsonString) {
  ItemAttribute attribute;
  string json_string = R"(
    {
        "numbers": [1, 2, 3],
        "creationTimestamp": "2024-07-31T10:34:02.695-08:00"
    }
    )";
  attribute.set_value_json_string(json_string);
  json expected_value = nlohmann::ordered_json::parse(json_string);
  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToJsonType(attribute),
      IsSuccessfulAndHolds(expected_value));
}

TEST(GcpNoSQLDatabaseClientUtilsTest,
     ConvertItemAttributeJsonTypeInvalidJsonString) {
  ItemAttribute attribute;
  attribute.set_value_json_string(R"(
    {
        "strings": ["extra", "comma", ]
    }
    )");
  EXPECT_THAT(
      GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToJsonType(attribute),
      ResultIs(FailureExecutionResult(
          SC_NO_SQL_DATABASE_PROVIDER_INVALID_JSON_STRING)));
}

TEST(GcpNoSQLDatabaseClientUtilsTest,
     ConvertItemAttributeToSpannerValueNumber) {
  ItemAttribute attribute;
  attribute.set_value_int(1);
  EXPECT_THAT(GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToSpannerValue(
                  attribute),
              IsSuccessfulAndHolds(Value(1)));

  attribute.set_value_float(1.5);
  EXPECT_THAT(GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToSpannerValue(
                  attribute),
              IsSuccessfulAndHolds(Value(1.5f)));

  attribute.set_value_double(1.5L);
  EXPECT_THAT(GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToSpannerValue(
                  attribute),
              IsSuccessfulAndHolds(Value(1.5)));
}

TEST(GcpNoSQLDatabaseClientUtilsTest,
     ConvertItemAttributeToSpannerValueString) {
  ItemAttribute attribute;
  attribute.set_value_string("hello world!");
  EXPECT_THAT(GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToSpannerValue(
                  attribute),
              IsSuccessfulAndHolds(Value("hello world!")));
}

TEST(GcpNoSQLDatabaseClientUtilsTest,
     ConvertItemAttributeToSpannerValueJsonString) {
  ItemAttribute attribute;
  string json_string = R"(
    {
        "numbers": [1, 2, 3]
    }
    )";
  attribute.set_value_json_string(json_string);
  Json expected_json_string = Json(json_string);
  EXPECT_THAT(GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToSpannerValue(
                  attribute),
              IsSuccessfulAndHolds(Value(expected_json_string)));
}

TEST(GcpNoSQLDatabaseClientUtilsTest,
     ConvertItemAttributeToSpannerValueInvalidJsonString) {
  ItemAttribute attribute;
  attribute.set_value_json_string(R"(
    {
        "strings": ["extra", "comma", ]
    }
    )");
  EXPECT_THAT(GcpNoSQLDatabaseClientUtils::ConvertItemAttributeToSpannerValue(
                  attribute),
              ResultIs(FailureExecutionResult(
                  SC_NO_SQL_DATABASE_PROVIDER_INVALID_JSON_STRING)));
}
}  // namespace google::scp::cpio::client_providers::test
