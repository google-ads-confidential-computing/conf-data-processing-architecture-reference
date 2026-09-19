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
#include "core/logger/src/log_utils.h"

#include <gtest/gtest.h>

#include <string_view>

namespace google::scp::core::logger {

namespace {

std::string FormatMessageWithVarargs(std::string_view message, ...) {
  va_list args;
  va_start(args, message);
  std::string result = FormatMessage(message, args);
  va_end(args);
  return result;
}

}  // namespace

TEST(LogUtilsTest, FormatsMessage) {
  EXPECT_EQ(FormatMessageWithVarargs("message: %s %d", "test", 1),
            "message: test 1");
}

};  // namespace google::scp::core::logger

