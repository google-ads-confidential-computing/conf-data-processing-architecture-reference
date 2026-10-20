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

#include <string>

#include "absl/strings/ascii.h"
#include "public/cpio/utils/data_normalization/format_detection/detector.h"
#include "public/cpio/utils/data_normalization/format_detection/detector_util.h"

namespace google::scp::cpio {

class CombinedAddressDetector {
 public:
  UserDataFormat Detect(const std::string& first_name,
                        const std::string& last_name) const noexcept {
    std::string clean_first(absl::StripAsciiWhitespace(first_name));
    std::string clean_last(absl::StripAsciiWhitespace(last_name));

    bool first_valid = IsValidHash(clean_first);
    bool last_valid = IsValidHash(clean_last);

    if (first_valid && last_valid) {
      if (clean_first.length() == clean_last.length()) {
        return UserDataFormat::kHashed;
      }
      return UserDataFormat::kUnknown;
    }
    return UserDataFormat::kUnhashed;
  }
};

}  // namespace google::scp::cpio
