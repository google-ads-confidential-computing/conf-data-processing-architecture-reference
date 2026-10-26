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
#include <vector>

#include "absl/base/no_destructor.h"
#include "absl/strings/ascii.h"
#include "absl/strings/str_replace.h"
#include "absl/strings/str_split.h"
#include "absl/strings/strip.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/utils/data_normalization/plaintext_pii/canonical_countries.h"
#include "public/cpio/utils/data_normalization/plaintext_pii/error_codes.h"
#include "public/cpio/utils/data_normalization/plaintext_pii/phone_country_codes.h"

namespace google::scp::cpio {
namespace {

using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::errors::
    SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_PHONE_NUMBER_COUNTRY_CODE_FAILURE;
using google::scp::core::errors::
    SC_CPIO_PLAINTEXT_PII_NORMALIZER_MALFORMED_EMAIL_FAILURE;

constexpr char kGmailDomain[] = "gmail.com";
constexpr char kGoogleMailDomain[] = "googlemail.com";
constexpr char kFacebookmailDomain[] = "facebookmail.com";
constexpr const char* kNameTitlesToStrip[] = {"mr.", "mrs.", "ms.", "dr."};

const auto& phone_country_code_map = GetPhoneCountryCodesMap();
const auto& canonical_country_code_map = GetCanonicalCountriesMap();

}  // namespace

ExecutionResultOr<std::string> NormalizeEmail(
    const std::string& email, bool should_strip_parts_after_plus_sign,
    bool should_strip_periods_in_facebookmail) {
  std::string lowercase_email = absl::AsciiStrToLower(email);

  // Replace googlemail.com with canoncialized gmail.com
  lowercase_email =
      absl::StrReplaceAll(lowercase_email, {{kGoogleMailDomain, kGmailDomain}});

  // Split username and domain parts of email
  std::vector<std::string> parts = absl::StrSplit(lowercase_email, '@');
  if (parts.size() != 2) {
    return FailureExecutionResult(
        SC_CPIO_PLAINTEXT_PII_NORMALIZER_MALFORMED_EMAIL_FAILURE);
  }
  std::string local_part = parts.front();
  std::string domain_part = parts.back();

  // For gmail domains, we always strip periods. Parts after plus sign
  // can also be stripped if configured.
  if (domain_part == kGmailDomain) {
    local_part = absl::StrReplaceAll(local_part, {{".", ""}});

    if (should_strip_parts_after_plus_sign) {
      auto plus_pos = local_part.find('+');
      if (plus_pos != std::string::npos) {
        local_part = local_part.substr(0, plus_pos);
      }
    }
  } else if (domain_part == kFacebookmailDomain &&
             should_strip_periods_in_facebookmail) {
    // For facebookmail.com emails, we strip periods if configured.
    local_part = absl::StrReplaceAll(local_part, {{".", ""}});
  }

  return local_part + "@" + domain_part;
}

ExecutionResultOr<std::string> NormalizePhoneNumber(
    const std::string& phone_number,
    const std::optional<std::string>& normalized_country_code) {
  std::string normalized_phone_number;

  // Remove non-digit and non-starting-plus sign from the string
  for (char c : phone_number) {
    if (absl::ascii_isdigit(c)) {
      normalized_phone_number += c;
    } else if (c == '+' && normalized_phone_number.empty()) {
      normalized_phone_number += c;
    }
  }

  // If already has the leading plus sign, consider it as E.164 formatted and
  // return the value.
  if (absl::StartsWith(normalized_phone_number, "+")) {
    return normalized_phone_number;
  }

  // Prepend the leading plus sign and return the value if country code is not
  // provided.
  if (!normalized_country_code.has_value()) {
    return absl::StrCat("+", normalized_phone_number);
  }

  auto it = phone_country_code_map.find(*normalized_country_code);
  if (it == phone_country_code_map.end()) {
    return FailureExecutionResult(
        SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_PHONE_NUMBER_COUNTRY_CODE_FAILURE);  // NOLINT(whitespace/line_length)
  }
  return absl::StrCat(*it->second.begin(), normalized_phone_number);
}

ExecutionResultOr<std::string> NormalizeName(
    const std::string& name, bool should_strip_common_prefixes,
    bool remove_numbers_and_special_chars) {
  std::string normalized_name = absl::AsciiStrToLower(name);
  if (should_strip_common_prefixes) {
    for (const auto& title : kNameTitlesToStrip) {
      std::string title_space = absl::StrCat(title, " ");
      if (absl::StartsWith(normalized_name, title_space)) {
        normalized_name = absl::StripPrefix(normalized_name, title_space);
        break;
      }
    }
  }
  if (remove_numbers_and_special_chars) {
    // Replicate Tagging's REGEX_NUMBER_OR_SYMBOL replacement (also whitespace)
    // /[0-9`~!@#$%^&*()_\-+=:;<>,.?|/\\[\]]/g
    static const absl::NoDestructor<absl::flat_hash_set<char>>
        kCharsToRemoveSet({' ', '0', '1', '2', '3', '4', '5',  '6', '7', '8',
                           '9', '`', '~', '!', '@', '#', '$',  '%', '^', '&',
                           '*', '(', ')', '_', '-', '+', '=',  ':', ';', '<',
                           '>', ',', '.', '?', '|', '/', '\\', '[', ']'});
    std::string filtered_name;
    for (char c : normalized_name) {
      if (!kCharsToRemoveSet->contains(c)) {
        filtered_name += c;
      }
    }
    normalized_name = filtered_name;
  }
  normalized_name = absl::StripAsciiWhitespace(normalized_name);
  return normalized_name;
}

ExecutionResultOr<std::string> NormalizeCountryCode(
    const std::string& country) {
  std::string normalized_country = absl::AsciiStrToLower(country);
  auto it = canonical_country_code_map.find(normalized_country);
  if (it != canonical_country_code_map.end()) {
    return it->second;
  }
  return normalized_country;
}

ExecutionResultOr<std::string> NormalizeAlphaNumeric(const std::string& input) {
  std::string result;
  for (char c : input) {
    if (absl::ascii_isalnum(c)) {
      result += c;
    }
  }
  return result;
}

ExecutionResultOr<std::string> NormalizeZipCode(
    const std::string& zip_code,
    bool should_only_trim_surrounding_whitespaces) {
  if (should_only_trim_surrounding_whitespaces) {
    return std::string(absl::StripAsciiWhitespace(zip_code));
  }
  return NormalizeAlphaNumeric(zip_code);
}

}  // namespace google::scp::cpio
