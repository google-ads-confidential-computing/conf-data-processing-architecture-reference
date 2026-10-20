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

// We consider a phone number to be legitimate if it has at least 4 digits and
// at most 18 digits. Both the bounds are from querying our internal production
// phone number data set with extra padding for safety (actual bound is [5, 16])
inline constexpr size_t kMinLegitPhoneNumberLength = 4;
inline constexpr size_t kMaxLegitPhoneNumberLength = 18;

class PhoneNumberDetector {
 public:
  UserDataFormat Detect(const std::string& phone) const noexcept {
    std::string clean_phone(absl::StripAsciiWhitespace(phone));
    if (IsValidHash(clean_phone)) {
      return UserDataFormat::kHashed;
    }
    size_t digit_count = 0;
    for (char c : clean_phone) {
      digit_count += (c >= '0' && c <= '9');
    }
    if (digit_count >= kMinLegitPhoneNumberLength &&
        digit_count <= kMaxLegitPhoneNumberLength) {
      return UserDataFormat::kUnhashed;
    }
    return UserDataFormat::kUnknown;
  }
};

}  // namespace google::scp::cpio
