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

#include "public/cpio/utils/data_normalization/format_detection/detector_util.h"

#include <string>

#include "absl/strings/escaping.h"

namespace google::scp::cpio {

bool HasBase64Sha256Length(const std::string& str) noexcept {
  return str.length() == 44 /* with padding */ ||
         str.length() == 43 /* without padding */;
}

bool HasHexSha256Length(const std::string& str) noexcept {
  return str.length() == 64;
}

bool IsValidBase64(const std::string& str) noexcept {
  std::string decoded;
  return absl::Base64Unescape(str, &decoded);
}

bool IsValidWebsafeBase64(const std::string& str) noexcept {
  std::string decoded;
  return absl::WebSafeBase64Unescape(str, &decoded);
}

bool IsValidHex(const std::string& str) noexcept {
  std::string decoded;
  return absl::HexStringToBytes(str, &decoded);
}

bool IsValidHash(const std::string& str) noexcept {
  if (HasBase64Sha256Length(str)) {
    if (str.length() == 44) {
      return IsValidBase64(str);
    }
    std::string padded_str = str + "=";
    return IsValidWebsafeBase64(str) || IsValidBase64(padded_str);
  }
  if (HasHexSha256Length(str) && IsValidHex(str)) {
    return true;
  }
  return false;
}

}  // namespace google::scp::cpio
