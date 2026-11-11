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

#pragma once

#include <optional>
#include <string>
#include <utility>

#include "absl/container/flat_hash_map.h"
#include "absl/container/flat_hash_set.h"
#include "public/core/interface/execution_result.h"

namespace google::scp::cpio {

struct NormalizeEmailOptions {
  bool should_strip_parts_after_plus_sign = false;
  bool should_strip_periods_in_facebookmail = false;
  bool should_replace_googlemail_with_gmail = true;
};

// Normalize an email address by stripping surrounding ASCII whitespaces,
// lowercasing all ASCII characters, and, if configured, canonicalize
// Google-owned email addresses so that they can be used for
// equivalent-comparison.
core::ExecutionResultOr<std::string> NormalizeEmail(
    const std::string& email, const NormalizeEmailOptions& options = {});

// Normalize a phone number to ITU-T E.164 standard (i.e. "+16502530000".)
// With an optional country code as a hint, numbers without +<countrycode>
// will be prefixed with that country's phone prefix. Without a country code
// (std::nullopt) and a valid E.164, phone number will be normalized into digits
// without +<countrycode> prefix. FailureExecutionResult will be returned if
// invalid country code is supplied.
core::ExecutionResultOr<std::string> NormalizePhoneNumber(
    const std::string& phone_number,
    const std::optional<std::string>& country_code);

// Normalize a person's name by lowercasing every characters.
// NOTE: This only reliably supports ASCII strings. UTF-8 support is WIP.
core::ExecutionResultOr<std::string> NormalizeName(
    const std::string& name, bool should_strip_common_prefixes,
    bool remove_numbers_and_special_chars = false);

// Normalize a country name/code into a 2-digit lowercase ISO-3166 country
// code through best-effort. Leading and trailing ASCII whitespaces are
// trimmed before lookup; unknown values are returned trimmed and lowercased.
std::string NormalizeCountryCode(const std::string& country);

// Normalize a string to only contain alphanumeric characters. This can
// be used for postal codes.
std::string NormalizeAlphaNumeric(const std::string& input);

// Options for NormalizeZipCode.
struct NormalizeZipCodeOptions {
  // If true, only leading and trailing ASCII whitespaces are trimmed.
  bool should_only_trim_surrounding_whitespaces = false;
  // If true, converts alphanumeric characters to lowercase.
  bool to_lowercase = false;
  // If true, returns FailureExecutionResult when no alphanumeric characters
  // remain.
  bool fail_on_empty = false;
  // Optional country code. When provided and normalized to "us", alphanumeric
  // zip codes receive US-specific formatting controlled by
  // `zero_pad_us_zip_code` and `truncate_us_zip_code`.
  std::optional<std::string> country_code = std::nullopt;
  // If true, non-empty US zip codes are left-padded with '0': codes shorter
  // than 5 characters are padded to 5, and codes longer than 5 but shorter
  // than 9 characters are padded to 9 (and then truncated if
  // `truncate_us_zip_code` is true). Other lengths are unchanged.
  bool zero_pad_us_zip_code = false;
  // If true, 9-character US zip codes are truncated to the first 5 characters.
  // Otherwise, they are returned as all 9 characters without a separator.
  bool truncate_us_zip_code = false;
};

// Normalize a postal/zip code according to `options`:
// - If `should_only_trim_surrounding_whitespaces` is true, only trims
//   leading and trailing ASCII whitespaces; all other options are ignored.
// - Otherwise, removes non-alphanumeric characters, then lowercases the
//   result if `to_lowercase` is true. Returns
//   SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_ZIP_CODE_FAILURE if nothing
//   remains and `fail_on_empty` is true.
// - If `country_code` normalizes to "us" and `zero_pad_us_zip_code` is true,
//   left-pads non-empty results with '0' to 5 characters (if shorter than 5)
//   or to 9 characters (if longer than 5 and shorter than 9).
// - If `country_code` normalizes to "us", 9 characters remain, and
//   `truncate_us_zip_code` is true, truncates to the first 5 characters.
core::ExecutionResultOr<std::string> NormalizeZipCode(
    const std::string& zip_code, const NormalizeZipCodeOptions& options = {});

}  // namespace google::scp::cpio
