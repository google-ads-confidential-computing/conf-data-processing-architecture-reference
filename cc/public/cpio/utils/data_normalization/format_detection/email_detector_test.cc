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

#include "public/cpio/utils/data_normalization/format_detection/email_detector.h"

#include <gtest/gtest.h>

#include <string>

namespace google::scp::cpio::test {
namespace {

struct EmailTestParam {
  std::string input;
  UserDataFormat expected_format;
};

class EmailDetectorTest : public testing::TestWithParam<EmailTestParam> {};

TEST_P(EmailDetectorTest, Detect) {
  EmailDetector detector;
  EXPECT_EQ(detector.Detect(GetParam().input), GetParam().expected_format);
}

INSTANTIATE_TEST_SUITE_P(
    EmailTests, EmailDetectorTest,
    testing::Values(
        // Valid email -> unhashed
        EmailTestParam{"UsEr@googlemail.com", UserDataFormat::kUnhashed},
        EmailTestParam{"user@gmail.com", UserDataFormat::kUnhashed},
        EmailTestParam{"user.name+tag@gmail.com", UserDataFormat::kUnhashed},
        EmailTestParam{"user.name@example.co.uk", UserDataFormat::kUnhashed},
        EmailTestParam{"  user@gmail.com  \n", UserDataFormat::kUnhashed},
        EmailTestParam{"  user%name@gmail.com  \n", UserDataFormat::kUnhashed},

        // Odd email addresses that happened in the field. A single "@" is
        // enough -> unhashed
        EmailTestParam{"*user@eMaiL.com", UserDataFormat::kUnhashed},
        EmailTestParam{"/user/test@Service.com", UserDataFormat::kUnhashed},

        // Valid hash -> hashed
        EmailTestParam{std::string(64, 'a'), UserDataFormat::kHashed},
        EmailTestParam{std::string(43, 'a'), UserDataFormat::kHashed},
        EmailTestParam{std::string(44, 'a'), UserDataFormat::kHashed},

        // Invalid email and invalid hash -> unknown
        EmailTestParam{std::string(45, 'a'), UserDataFormat::kUnknown},
        EmailTestParam{"invalid_email_without_at", UserDataFormat::kUnknown},
        EmailTestParam{"", UserDataFormat::kUnknown},

        // More than one "@" -> unknown
        EmailTestParam{"user@host@gmail.com", UserDataFormat::kUnknown},
        EmailTestParam{"@user@", UserDataFormat::kUnknown}));

class EmailDetectorStrictTest : public testing::TestWithParam<EmailTestParam> {
};

TEST_P(EmailDetectorStrictTest, DetectStrict) {
  EmailDetector detector;
  EXPECT_EQ(detector.DetectStrict(GetParam().input),
            GetParam().expected_format);
}

INSTANTIATE_TEST_SUITE_P(
    EmailStrictTests, EmailDetectorStrictTest,
    testing::Values(
        // Valid email -> unhashed
        EmailTestParam{"UsEr@googlemail.com", UserDataFormat::kUnhashed},
        EmailTestParam{"user@gmail.com", UserDataFormat::kUnhashed},
        EmailTestParam{"user.name+tag@gmail.com", UserDataFormat::kUnhashed},
        EmailTestParam{"user.name@example.co.uk", UserDataFormat::kUnhashed},
        EmailTestParam{"  user@gmail.com  \n", UserDataFormat::kUnhashed},
        EmailTestParam{"  user%name@gmail.com  \n", UserDataFormat::kUnhashed},

        // Valid hash -> hashed
        EmailTestParam{std::string(64, 'a'), UserDataFormat::kHashed},
        EmailTestParam{std::string(43, 'a'), UserDataFormat::kHashed},
        EmailTestParam{std::string(44, 'a'), UserDataFormat::kHashed},

        // Invalid email and invalid hash -> unknown
        EmailTestParam{std::string(45, 'a'), UserDataFormat::kUnknown},
        EmailTestParam{"invalid_email_without_at", UserDataFormat::kUnknown},
        EmailTestParam{"", UserDataFormat::kUnknown},
        EmailTestParam{"user@host@gmail.com", UserDataFormat::kUnknown},
        EmailTestParam{"@user@", UserDataFormat::kUnknown},

        // Stricter than Detect: a single "@" is not sufficient
        EmailTestParam{"*user@eMaiL.com", UserDataFormat::kUnknown},
        EmailTestParam{"/user/test@Service.com", UserDataFormat::kUnknown},
        EmailTestParam{"user@gmail", UserDataFormat::kUnknown},
        EmailTestParam{"user@gmail.c", UserDataFormat::kUnknown},
        EmailTestParam{"@gmail.com", UserDataFormat::kUnknown},
        EmailTestParam{"user@", UserDataFormat::kUnknown}));
}  // namespace
}  // namespace google::scp::cpio::test
