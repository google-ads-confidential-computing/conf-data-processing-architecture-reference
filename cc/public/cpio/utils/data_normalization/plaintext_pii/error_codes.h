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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include "core/interface/errors.h"
#include "public/core/interface/execution_result.h"

namespace google::scp::core::errors {

/// Registers component code as 0x7033 for SC_CPIO_PLAINTEXT_PII_NORMALIZER.
REGISTER_COMPONENT_CODE(SC_CPIO_PLAINTEXT_PII_NORMALIZER, 0x7033)

DEFINE_ERROR_CODE(SC_CPIO_PLAINTEXT_PII_NORMALIZER_MALFORMED_EMAIL_FAILURE,
                  SC_CPIO_PLAINTEXT_PII_NORMALIZER, 0x0003,
                  "Failed to parse the input email address.",
                  HttpStatusCode::BAD_REQUEST)

DEFINE_ERROR_CODE(
    SC_CPIO_PLAINTEXT_PII_NORMALIZER_INVALID_PHONE_NUMBER_COUNTRY_CODE_FAILURE,
    SC_CPIO_PLAINTEXT_PII_NORMALIZER, 0x0004,
    "A valid country code is required to normalize a phone number that does "
    "not start with plus sign.",
    HttpStatusCode::BAD_REQUEST)

}  // namespace google::scp::core::errors
