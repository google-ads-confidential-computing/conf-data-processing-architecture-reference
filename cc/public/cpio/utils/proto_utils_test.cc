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

#include "public/cpio/utils/proto_utils.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <google/protobuf/util/time_util.h>

using google::protobuf::util::TimeUtil;
using testing::Eq;

namespace google::scp::cpio::test {
namespace {

TEST(ProtoUtilsTest, TextProtoStringSuccessfullyFormatsStringValue) {
  google::protobuf::Timestamp message = TimeUtil::NanosecondsToTimestamp(123);

  std::string result = ProtoUtils::TextProtoString(message);

  EXPECT_THAT(result, Eq("nanos: 123\n"));
}

TEST(ProtoUtilsTest, TextProtoStringHandlesEmptyMessage) {
  google::protobuf::Timestamp message;

  std::string result = ProtoUtils::TextProtoString(message);

  EXPECT_THAT(result, Eq(""));
}

TEST(ProtoUtilsTest, TextProtoStringFormatsComplexMessage) {
  google::protobuf::Timestamp message =
      TimeUtil::NanosecondsToTimestamp(1234567890000000500LL);

  std::string result = ProtoUtils::TextProtoString(message);

  // Verifies nested fields and correct formatting without anti-parsing comments
  EXPECT_THAT(result, Eq("seconds: 1234567890\nnanos: 500\n"));
}

}  // namespace
}  // namespace google::scp::cpio::test
