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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "public/cpio/utils/data_normalization/format_detection/detector_util.h"

#include <gtest/gtest.h>

#include <string>

namespace google::scp::cpio::test {
namespace {

struct HashTestParam {
  std::string input;
  bool expected_valid;
};

class DetectorUtilTest : public testing::TestWithParam<HashTestParam> {};

TEST_P(DetectorUtilTest, IsValidHash) {
  EXPECT_EQ(IsValidHash(GetParam().input), GetParam().expected_valid);
}

TEST(DetectorUtilDirectTest, IsValidBase64) {
  EXPECT_TRUE(IsValidBase64("QUJD"));
  EXPECT_FALSE(IsValidBase64("QUJD-"));
}

TEST(DetectorUtilDirectTest, IsValidWebsafeBase64) {
  EXPECT_TRUE(IsValidWebsafeBase64("-_--"));
  EXPECT_FALSE(IsValidWebsafeBase64("+++="));
}

TEST(DetectorUtilDirectTest, IsValidHex) {
  EXPECT_TRUE(IsValidHex("0123456789abcdefABCDEF"));
  EXPECT_FALSE(IsValidHex("0123456789abcdefABCDEG"));
  EXPECT_FALSE(IsValidHex("123"));
}

INSTANTIATE_TEST_SUITE_P(ValidHashes, DetectorUtilTest,
                         testing::Values(
                             // Hex (64 chars)
                             HashTestParam{std::string(64, 'a'), true},
                             HashTestParam{std::string(64, '0'), true},
                             // Regular Base64 (44 chars ending with =)
                             HashTestParam{std::string(43, 'A') + "=", true},

                             // Regular Base64 (43 chars, stripped padding =)
                             // Notice the '+' is not a valid char for a
                             // Web-safe Base64 string (length 43).
                             HashTestParam{"+" + std::string(42, 'A'), true},

                             // Web-safe Base64 (43 chars)
                             HashTestParam{std::string(43, '-'), true},
                             HashTestParam{std::string(43, '_'), true}));

INSTANTIATE_TEST_SUITE_P(InvalidHashes, DetectorUtilTest,
                         testing::Values(
                             // Wrong lengths
                             HashTestParam{"", false},
                             HashTestParam{std::string(42, 'A'), false},
                             HashTestParam{std::string(45, 'A'), false},
                             // Invalid charset for Hex
                             HashTestParam{std::string(63, 'a') + "G", false},
                             // Invalid charset for Regular Base64
                             HashTestParam{std::string(43, 'A') + "-", false}));

}  // namespace
}  // namespace google::scp::cpio::test
