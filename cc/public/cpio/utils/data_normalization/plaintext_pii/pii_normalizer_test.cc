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
    SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_ZIP_CODE_FAILURE;
using google::scp::core::errors::
    SC_CPIO_PLAINTEXT_PII_NORMALIZER_MALFORMED_EMAIL_FAILURE;
using google::scp::core::test::ResultIs;
using google::scp::core::test::ScpTestBase;

class PiiNormalizerTest : public ScpTestBase {};

TEST_F(PiiNormalizerTest, NormalizeEmailSuccess) {
  auto result = NormalizeEmail("TEST@gmail.com");
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "test@gmail.com");
}

TEST_F(PiiNormalizerTest, NormalizeEmailWithGoogleMailDomain) {
  auto result = NormalizeEmail("TEST@googlemail.com");
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "test@gmail.com");
}

TEST_F(PiiNormalizerTest,
       NormalizeEmailWithGoogleMailDomainReplacementDisabled) {
  auto result = NormalizeEmail("TEST@googlemail.com",
                               {.should_replace_googlemail_with_gmail = false});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "test@googlemail.com");

  result = NormalizeEmail("user.name+user@GOOGLEMAIL.com",
                          {.should_replace_googlemail_with_gmail = false});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "username+user@googlemail.com");

  result = NormalizeEmail("user.name+user@GOOGLEMAIL.com",
                          {.should_strip_parts_after_plus_sign = true,
                           .should_replace_googlemail_with_gmail = false});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "username@googlemail.com");
}

TEST_F(PiiNormalizerTest, NormalizeEmailMalformed) {
  auto result = NormalizeEmail("TESTgmail.com");
  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_CPIO_PLAINTEXT_PII_NORMALIZER_MALFORMED_EMAIL_FAILURE)));
}

TEST_F(PiiNormalizerTest, NormalizeEmailStripPeriods) {
  auto result = NormalizeEmail("test.user@GMAIL.com");
  EXPECT_THAT(
      result,
      ResultIs(SuccessExecutionResult()));  // test.user@GMAIL.com, false, false
  EXPECT_EQ(result.value(), "testuser@gmail.com");
}

TEST_F(PiiNormalizerTest, NormalizeEmailStripPlusSign) {
  auto result = NormalizeEmail("test+user@gmail.com",
                               {.should_strip_parts_after_plus_sign = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "test@gmail.com");

  result = NormalizeEmail("user.name+user@gmail.com",
                          {.should_strip_parts_after_plus_sign = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "username@gmail.com");
}

TEST_F(PiiNormalizerTest, NormalizeEmailStripPeriodsInFacebookmail) {
  auto result = NormalizeEmail("test.user@facebookmail.com",
                               {.should_strip_periods_in_facebookmail = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "testuser@facebookmail.com");
}

TEST_F(PiiNormalizerTest, NormalizeEmailStripSurroundingWhitespaces) {
  auto result = NormalizeEmail(" \tTEST@gmail.com\r\n ");
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "test@gmail.com");

  result = NormalizeEmail("  user.name+user@GOOGLEMAIL.com\n",
                          {.should_strip_parts_after_plus_sign = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "username@gmail.com");

  result = NormalizeEmail("   ");
  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_CPIO_PLAINTEXT_PII_NORMALIZER_MALFORMED_EMAIL_FAILURE)));
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
  EXPECT_EQ(NormalizeCountryCode("United States"), "us");
  EXPECT_EQ(NormalizeCountryCode("UK"), "gb");
  EXPECT_EQ(NormalizeCountryCode("Us"), "us");
}

TEST_F(PiiNormalizerTest, NormalizeCountryCodeTrimsSurroundingWhitespaces) {
  EXPECT_EQ(NormalizeCountryCode("  Us  "), "us");
  EXPECT_EQ(NormalizeCountryCode("\tUnited States\n"), "us");
  // Unknown values are returned trimmed and lowercased.
  EXPECT_EQ(NormalizeCountryCode(" XYZ "), "xyz");
}

TEST_F(PiiNormalizerTest, NormalizeAlphanumericSuccess) {
  EXPECT_EQ(NormalizeAlphaNumeric("hello world 123!"), "helloworld123");
}

TEST_F(PiiNormalizerTest, NormalizeZipCodeAlphanumericSuccess) {
  auto result = NormalizeZipCode(
      " 12345-6789 ", {.should_only_trim_surrounding_whitespaces = false});
  EXPECT_EQ(result.value(), "123456789");

  result = NormalizeZipCode(
      "SW1A 1AA", {.should_only_trim_surrounding_whitespaces = false});
  EXPECT_EQ(result.value(), "SW1A1AA");
}

TEST_F(PiiNormalizerTest,
       NormalizeZipCodeOnlyTrimSurroundingWhitespacesSuccess) {
  auto result = NormalizeZipCode(
      " \t12345-6789\r\n", {.should_only_trim_surrounding_whitespaces = true});
  EXPECT_EQ(result.value(), "12345-6789");

  // Whitespaces in the middle are preserved.
  result = NormalizeZipCode(" SW1A 1AA ",
                            {.should_only_trim_surrounding_whitespaces = true});
  EXPECT_EQ(result.value(), "SW1A 1AA");

  result = NormalizeZipCode("12345+6789",
                            {.should_only_trim_surrounding_whitespaces = true});
  EXPECT_EQ(result.value(), "12345+6789");

  result = NormalizeZipCode("   ",
                            {.should_only_trim_surrounding_whitespaces = true});
  EXPECT_EQ(result.value(), "");

  // Trim-only takes precedence over other options.
  result = NormalizeZipCode(" 12345-6789 ",
                            {.should_only_trim_surrounding_whitespaces = true,
                             .to_lowercase = true,
                             .country_code = "US",
                             .truncate_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "12345-6789");

  result =
      NormalizeZipCode("   ", {.should_only_trim_surrounding_whitespaces = true,
                               .fail_on_empty = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "");
}

TEST_F(PiiNormalizerTest, NormalizeZipCodeDefaultBehaviorSuccess) {
  auto result = NormalizeZipCode("hello world 123!");
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "helloworld123");

  // When default options are used, uppercase is preserved and US zip+4
  // is not split or truncated.
  result = NormalizeZipCode("SW1A 1AA");
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "SW1A1AA");

  result = NormalizeZipCode("12345-6789");
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "123456789");

  // US options do nothing without country_code.
  result = NormalizeZipCode("12345-6789", {.zero_pad_us_zip_code = true,
                                           .truncate_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "123456789");

  result = NormalizeZipCode("_&|$%^");
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "");
}

TEST_F(PiiNormalizerTest, NormalizeZipCodeToLowercaseOnly) {
  // to_lowercase = true, fail_on_empty = false, country_code = std::nullopt:
  // lowercases alphanumeric characters, does not split or truncate US zip+4,
  // and returns empty string when no alphanumeric characters remain.
  auto result = NormalizeZipCode("SW1A 1AA", {.to_lowercase = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "sw1a1aa");

  result = NormalizeZipCode("12345-6789", {.to_lowercase = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "123456789");

  result = NormalizeZipCode("_&|$%^", {.to_lowercase = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "");
}

TEST_F(PiiNormalizerTest, NormalizeZipCodeFailOnEmpty) {
  // When fail_on_empty = true and no alphanumeric characters remain, return
  // FailureExecutionResult.
  auto result = NormalizeZipCode("_&|$%^", {.fail_on_empty = true});
  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_ZIP_CODE_FAILURE)));

  result = NormalizeZipCode(
      "_&|$%^",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "uk"});
  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_ZIP_CODE_FAILURE)));
}

TEST_F(PiiNormalizerTest, NormalizeZipCodeNonUsCountry) {
  // Non-US or no country code: alphanumeric only (with optional lowercasing).
  auto result = NormalizeZipCode(
      "SW1A 1AA",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "gb"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "sw1a1aa");

  result = NormalizeZipCode(
      "12345UK",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "uk"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "12345uk");

  result = NormalizeZipCode("UK$%^&-123",
                            {.to_lowercase = true, .fail_on_empty = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "uk123");

  // to_lowercase = false preserves case.
  result = NormalizeZipCode(
      "SW1A 1AA",
      {.to_lowercase = false, .fail_on_empty = true, .country_code = "gb"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "SW1A1AA");
}

TEST_F(PiiNormalizerTest, NormalizeZipCodeUS) {
  // US 5-digit unchanged
  auto result = NormalizeZipCode(
      "12345",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "US"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "12345");

  // US 5-digit with hyphen
  result = NormalizeZipCode(
      "123-45",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "US"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "12345");

  // US zip codes with length != 5 and != 9 are returned as stripped
  // alphanumeric without zero-padding.
  result = NormalizeZipCode(
      "2345",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "us"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "2345");

  result = NormalizeZipCode(
      "ABC",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "  Us  "});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "abc");

  result = NormalizeZipCode(
      "ABC",
      {.to_lowercase = false, .fail_on_empty = true, .country_code = "US"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "ABC");

  result = NormalizeZipCode(
      "1234569",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "US"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "1234569");

  result = NormalizeZipCode(
      "1234567890",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "US"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "1234567890");

  // US 9-digit with truncate_us_zip_code = false emits all 9 characters
  // without a separator.
  result = NormalizeZipCode(
      "12345-6789",
      {.to_lowercase = true, .fail_on_empty = true, .country_code = "US"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "123456789");

  result = NormalizeZipCode("123456789", {.to_lowercase = true,
                                          .fail_on_empty = true,
                                          .country_code = "United States"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "123456789");

  // US 9-digit with truncate_us_zip_code = true truncates to first 5
  // characters.
  result = NormalizeZipCode("12345-6789", {.to_lowercase = true,
                                           .fail_on_empty = true,
                                           .country_code = "US",
                                           .truncate_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "12345");

  result = NormalizeZipCode("123456789", {.to_lowercase = true,
                                          .fail_on_empty = true,
                                          .country_code = "United States",
                                          .truncate_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "12345");
}

TEST_F(PiiNormalizerTest, NormalizeZipCodeUSZeroPadding) {
  // Shorter than 5 characters: padded to 5.
  auto result = NormalizeZipCode(
      "2345", {.country_code = "US", .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "02345");

  result = NormalizeZipCode(
      "1", {.country_code = "us", .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "00001");

  result = NormalizeZipCode("ABC", {.to_lowercase = true,
                                    .country_code = "United States",
                                    .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "00abc");

  // Exactly 5 characters: unchanged.
  result = NormalizeZipCode(
      "12345", {.country_code = "US", .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "12345");

  // Between 5 and 9 characters: left-padded to 9 (matches MRP and Match
  // Service).
  result = NormalizeZipCode(
      "1234569", {.country_code = "US", .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "001234569");

  result = NormalizeZipCode(
      "12345-678", {.country_code = "US", .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "012345678");

  result = NormalizeZipCode(
      "123456", {.country_code = "US", .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "000123456");

  // Between 5 and 9 characters: left-padded to 9, then truncated to 5
  // (matches MRP and Match Service).
  result = NormalizeZipCode("1234569", {.country_code = "US",
                                        .zero_pad_us_zip_code = true,
                                        .truncate_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "00123");

  // Exactly 9 characters: not padded, truncated.
  result = NormalizeZipCode("12345-6789", {.country_code = "US",
                                           .zero_pad_us_zip_code = true,
                                           .truncate_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "12345");

  // Longer than 9 characters: unchanged.
  result = NormalizeZipCode("1234567890", {.country_code = "US",
                                           .zero_pad_us_zip_code = true,
                                           .truncate_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "1234567890");

  // Empty result is not padded.
  result = NormalizeZipCode(
      "-_-", {.country_code = "US", .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "");

  result = NormalizeZipCode("-_-", {.fail_on_empty = true,
                                    .country_code = "US",
                                    .zero_pad_us_zip_code = true});
  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_ZIP_CODE_FAILURE)));

  // Non-US country codes are not padded.
  result = NormalizeZipCode(
      "2345", {.country_code = "gb", .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "2345");

  // No country code: not padded.
  result = NormalizeZipCode("2345", {.zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "2345");

  // Trim-only takes precedence over zero-padding.
  result = NormalizeZipCode(" 2345 ",
                            {.should_only_trim_surrounding_whitespaces = true,
                             .country_code = "US",
                             .zero_pad_us_zip_code = true});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "2345");

  // Without the flag, US zip codes are not padded.
  result = NormalizeZipCode("2345", {.country_code = "US"});
  EXPECT_THAT(result, ResultIs(SuccessExecutionResult()));
  EXPECT_EQ(result.value(), "2345");
}

}  // namespace
}  // namespace google::scp::cpio::test
