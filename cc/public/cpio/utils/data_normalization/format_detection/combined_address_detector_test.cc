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

#include "public/cpio/utils/data_normalization/format_detection/combined_address_detector.h"

#include <gtest/gtest.h>

#include <string>

namespace google::scp::cpio::test {
namespace {

struct AddressTestParam {
  std::string first_name;
  std::string last_name;
  UserDataFormat expected_format;
};

class CombinedAddressDetectorTest
    : public testing::TestWithParam<AddressTestParam> {};

TEST_P(CombinedAddressDetectorTest, Detect) {
  CombinedAddressDetector detector;
  EXPECT_EQ(detector.Detect(GetParam().first_name, GetParam().last_name),
            GetParam().expected_format);
}

INSTANTIATE_TEST_SUITE_P(
    AddressTests, CombinedAddressDetectorTest,
    testing::Values(
        // Both valid hashes with same length -> hashed
        AddressTestParam{std::string(64, 'a'), std::string(64, 'b'),
                         UserDataFormat::kHashed},
        AddressTestParam{"  " + std::string(64, 'a') + "  ",
                         "\n" + std::string(64, 'b') + "\n",
                         UserDataFormat::kHashed},

        // Both valid hashes with different lengths -> unknown
        AddressTestParam{std::string(64, 'a'), std::string(43, '-'),
                         UserDataFormat::kUnknown},

        // One invalid hash -> unhashed
        AddressTestParam{"John", std::string(64, 'a'),
                         UserDataFormat::kUnhashed},
        AddressTestParam{std::string(64, 'a'), "Doe",
                         UserDataFormat::kUnhashed},

        // Both invalid hashes -> unhashed
        AddressTestParam{"John", "Doe", UserDataFormat::kUnhashed}));

}  // namespace
}  // namespace google::scp::cpio::test
