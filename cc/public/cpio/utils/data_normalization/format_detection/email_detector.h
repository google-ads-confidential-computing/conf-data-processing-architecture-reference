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

#pragma once

#include <regex>
#include <string>
#include <vector>

#include "absl/strings/ascii.h"
#include "absl/strings/str_split.h"
#include "absl/strings/string_view.h"
#include "public/cpio/utils/data_normalization/format_detection/detector.h"
#include "public/cpio/utils/data_normalization/format_detection/detector_util.h"

namespace google::scp::cpio {

class EmailDetector {
 public:
  UserDataFormat Detect(const std::string& email) const noexcept {
    std::string clean_email(absl::StripAsciiWhitespace(email));
    if (IsValidHash(clean_email)) {
      return UserDataFormat::kHashed;
    }
    const std::vector<absl::string_view> parts =
        absl::StrSplit(clean_email, '@');
    if (parts.size() == 2) {
      return UserDataFormat::kUnhashed;
    }
    return UserDataFormat::kUnknown;
  }

  UserDataFormat DetectStrict(const std::string& email) const noexcept {
    std::string clean_email(absl::StripAsciiWhitespace(email));
    if (IsValidHash(clean_email)) {
      return UserDataFormat::kHashed;
    }
    static const std::regex email_re(
        R"(^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$)");
    if (std::regex_match(clean_email, email_re)) {
      return UserDataFormat::kUnhashed;
    }
    return UserDataFormat::kUnknown;
  }
};

}  // namespace google::scp::cpio
