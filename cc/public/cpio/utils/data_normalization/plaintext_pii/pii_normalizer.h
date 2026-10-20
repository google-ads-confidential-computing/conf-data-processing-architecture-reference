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

// Normalize an email address by perform lowercasing all ASCII characters,
// and, if configured, canonicalize Google-owned email addresses so that
// they can be used for equivalent-comparison.
core::ExecutionResultOr<std::string> NormalizeEmail(
    const std::string& email, bool should_strip_parts_after_plus_sign,
    bool should_strip_periods_in_facebookmail);

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
// code through best-effort.
core::ExecutionResultOr<std::string> NormalizeCountryCode(
    const std::string& country);

// Normalize a string to only contain alphanumeric characters. This can
// be used for postal codes.
core::ExecutionResultOr<std::string> NormalizeAlphaNumeric(
    const std::string& input);

}  // namespace google::scp::cpio
