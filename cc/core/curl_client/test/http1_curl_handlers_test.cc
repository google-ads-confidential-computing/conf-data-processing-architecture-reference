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
#include "cc/core/curl_client/src/http1_curl_handlers.h"

#include <string>

#include <gtest/gtest.h>

#include "core/interface/http_types.h"
#include "core/test/utils/scp_test_base.h"

namespace google::scp::core::test {

using google::scp::core::test::ScpTestBase;

class Http1CurlHandlersTest : public ScpTestBase {};

TEST_F(Http1CurlHandlersTest, ResponsePayloadHandlerWithSinglePayloadWorks) {
  BytesBuffer buffer;
  std::string input_data = "example-curl-payload";

  size_t bytes_processed =
      ResponsePayloadHandler(&input_data[0], 1, input_data.length(), &buffer);

  EXPECT_EQ(bytes_processed, input_data.length());
  EXPECT_NE(buffer.bytes, nullptr);
  EXPECT_EQ(buffer.length, input_data.length());
  EXPECT_EQ(buffer.capacity, input_data.length());
  EXPECT_EQ(buffer.ToString(), input_data);
}

TEST_F(Http1CurlHandlersTest, ResponsePayloadHandlerWithMultiplePayloadsWorks) {
  BytesBuffer buffer;
  std::string part1 = "example-multipart-";
  std::string part2 = "curl-payload";
  std::string combined = part1 + part2;

  size_t bytes_processed1 =
      ResponsePayloadHandler(&part1[0], 1, part1.length(), &buffer);
  size_t bytes_processed2 =
      ResponsePayloadHandler(&part2[0], 1, part2.length(), &buffer);

  EXPECT_EQ(bytes_processed1, part1.length());
  EXPECT_EQ(bytes_processed2, part2.length());
  EXPECT_NE(buffer.bytes, nullptr);
  EXPECT_EQ(buffer.length, combined.length());
  EXPECT_EQ(buffer.capacity, combined.length());
  EXPECT_EQ(buffer.ToString(), combined);
}
}  // namespace google::scp::core::test
