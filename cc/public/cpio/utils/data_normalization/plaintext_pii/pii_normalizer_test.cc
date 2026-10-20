/*
 * Copyright 2025 Google LLC
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

#include "public/cpio/utils/data_normalization/plaintext_pii/pii_normalizer.h"

#include <optional>
#include <string>

#include "core/test/utils/scp_test_base.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/utils/data_normalization/plaintext_pii/error_codes.h"

namespace google::scp::cpio::test {
namespace {

using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::errors::
    SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_PHONE_NUMBER_COUNTRY_CODE_FAILURE;
using google::scp::core::errors::
    SC_CPIO_PLAINTEXT_PII_NORMALIZER_MALFORMED_EMAIL_FAILURE;
using google::scp::core::test::ResultIs;
using google::scp::core::test::ScpTestBase;

class PiiNormalizerTest : public ScpTestBase {};

TEST_F(PiiNormalizerTest, NormalizeEmailSuccess) {
  auto result = NormalizeEmail("TEST@gmail.com",
                               /*should_strip_parts_after_plus_sign=*/false,
                               /*should_strip_periods_in_facebookmail=*/false);
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "test@gmail.com");
}

TEST_F(PiiNormalizerTest, NormalizeEmailWithGoogleMailDomain) {
  auto result = NormalizeEmail("TEST@googlemail.com",
                               /*should_strip_parts_after_plus_sign=*/false,
                               /*should_strip_periods_in_facebookmail=*/false);
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "test@gmail.com");
}

TEST_F(PiiNormalizerTest, NormalizeEmailMalformed) {
  auto result = NormalizeEmail("TESTgmail.com",
                               /*should_strip_parts_after_plus_sign=*/false,
                               /*should_strip_periods_in_facebookmail=*/false);
  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_CPIO_PLAINTEXT_PII_NORMALIZER_MALFORMED_EMAIL_FAILURE)));
}

TEST_F(PiiNormalizerTest, NormalizeEmailStripPeriods) {
  auto result = NormalizeEmail("test.user@GMAIL.com",
                               /*should_strip_parts_after_plus_sign=*/false,
                               /*should_strip_periods_in_facebookmail=*/false);
  EXPECT_THAT(
      result,
      ResultIs(SuccessExecutionResult()));  // test.user@GMAIL.com, false, false
  EXPECT_EQ(result.value(), "testuser@gmail.com");
}

TEST_F(PiiNormalizerTest, NormalizeEmailStripPlusSign) {
  auto result = NormalizeEmail("test+user@gmail.com",
                               /*should_strip_parts_after_plus_sign=*/true,
                               /*should_strip_periods_in_facebookmail=*/false);
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "test@gmail.com");

  result = NormalizeEmail("user.name+user@gmail.com",
                          /*should_strip_parts_after_plus_sign=*/true,
                          /*should_strip_periods_in_facebookmail=*/false);
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "username@gmail.com");
}

TEST_F(PiiNormalizerTest, NormalizeEmailStripPeriodsInFacebookmail) {
  auto result = NormalizeEmail("test.user@facebookmail.com",
                               /*should_strip_parts_after_plus_sign=*/false,
                               /*should_strip_periods_in_facebookmail=*/true);
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "testuser@facebookmail.com");
}

TEST_F(PiiNormalizerTest, NormalizePhoneNumberSuccessWithPlusSign) {
  auto result = NormalizePhoneNumber(" +16502530000 ", std::nullopt);
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "+16502530000");
}

TEST_F(PiiNormalizerTest, NormalizePhoneNumberSuccessWithCountryCode) {
  auto result = NormalizePhoneNumber("6502+530000", "us");
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "+16502530000");
}

TEST_F(PiiNormalizerTest,
       NormalizePhoneNumberSuccessWithoutCountryCodeAndPlusSign) {
  auto result = NormalizePhoneNumber("6502+530000", std::nullopt);
  EXPECT_EQ(result.value(), "+6502530000");
}

TEST_F(PiiNormalizerTest, NormalizePhoneNumberFailureInvalidCountryCode) {
  auto result = NormalizePhoneNumber("6502530000", "invalid");
  EXPECT_THAT(
      result.result(),
      ResultIs(FailureExecutionResult(
          SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_PHONE_NUMBER_COUNTRY_CODE_FAILURE)));  // NOLINT(whitespace/line_length)
}

TEST_F(PiiNormalizerTest, NormalizeNameSuccess) {
  auto result =
      NormalizeName("Mr. Test User", /*should_strip_common_prefixes=*/true);
  EXPECT_EQ(result.value(), "test user");

  result = NormalizeName("Dr. doctoR",
                         /*should_strip_common_prefixes=*/true);
  EXPECT_EQ(result.value(), "doctor");

  result = NormalizeName("Mrs. tEst",
                         /*should_strip_common_prefixes=*/true);
  EXPECT_EQ(result.value(), "test");
}

TEST_F(PiiNormalizerTest, NormalizeNameLettersOnlySuccess) {
  auto result =
      NormalizeName("John 123 Doe!", /*should_strip_common_prefixes=*/false,
                    /*remove_numbers_and_special_chars=*/true);
  EXPECT_EQ(result.value(), "johndoe");

  result = NormalizeName("Mr. John 123 Doe!",
                         /*should_strip_common_prefixes=*/true,
                         /*remove_numbers_and_special_chars=*/true);
  EXPECT_EQ(result.value(), "johndoe");
}

TEST_F(PiiNormalizerTest, NormalizeNameStripPrefixDisabled) {
  auto result =
      NormalizeName("Mr. Test User", /*should_strip_common_prefixes=*/false);
  EXPECT_EQ(result.value(), "mr. test user");
}

TEST_F(PiiNormalizerTest, NormalizeCountryCodeSuccess) {
  auto result = NormalizeCountryCode("United States");
  EXPECT_EQ(result.value(), "us");

  result = NormalizeCountryCode("UK");
  EXPECT_EQ(result.value(), "gb");

  result = NormalizeCountryCode("Us");
  EXPECT_EQ(result.value(), "us");
}

TEST_F(PiiNormalizerTest, NormalizeAlphanumericSuccess) {
  auto result = NormalizeAlphaNumeric("hello world 123!");
  EXPECT_EQ(result.value(), "helloworld123");
}

}  // namespace
}  // namespace google::scp::cpio::test
