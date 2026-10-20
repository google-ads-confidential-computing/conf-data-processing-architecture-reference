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

#include "public/cpio/utils/data_normalization/format_detection/phone_number_detector.h"

#include <gtest/gtest.h>

#include <string>

namespace google::scp::cpio::test {
namespace {

struct PhoneTestParam {
  std::string input;
  UserDataFormat expected_format;
};

class PhoneNumberDetectorTest : public testing::TestWithParam<PhoneTestParam> {
};

TEST_P(PhoneNumberDetectorTest, Detect) {
  PhoneNumberDetector detector;
  EXPECT_EQ(detector.Detect(GetParam().input), GetParam().expected_format);
}

INSTANTIATE_TEST_SUITE_P(
    PhoneTests, PhoneNumberDetectorTest,
    testing::Values(
        // Valid hash -> hashed
        PhoneTestParam{std::string(64, 'a'), UserDataFormat::kHashed},
        PhoneTestParam{std::string(43, 'a'), UserDataFormat::kHashed},
        PhoneTestParam{std::string(44, 'A'), UserDataFormat::kHashed},

        // Valid plaintext phone number -> unhashed
        PhoneTestParam{" +1234567890", UserDataFormat::kUnhashed},
        PhoneTestParam{"1234567890 ", UserDataFormat::kUnhashed},
        PhoneTestParam{"234567890 ", UserDataFormat::kUnhashed},
        PhoneTestParam{"+1 (123) 456-7890", UserDataFormat::kUnhashed},
        PhoneTestParam{"  +1 (123) 456-7890  ", UserDataFormat::kUnhashed},

        // Phone number too short/long -> unknown
        PhoneTestParam{"123", UserDataFormat::kUnknown},
        PhoneTestParam{std::string(19, '1'), UserDataFormat::kUnknown}));

}  // namespace
}  // namespace google::scp::cpio::test
