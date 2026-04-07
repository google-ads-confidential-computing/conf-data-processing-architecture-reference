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

#include "cc/core/interface/errors.h"

namespace google::scp::cpio {

/// Registers component code as 0x0221 for
/// SC_CPIO_PREFETCH_CONFIG_VALIDATOR.

REGISTER_COMPONENT_CODE(SC_CPIO_PREFETCH_CONFIG_VALIDATOR, 0x0221)

DEFINE_ERROR_CODE(
    SC_CPIO_INVALID_PREFETCH_CONFIG, SC_CPIO_PREFETCH_CONFIG_VALIDATOR, 0x0001,
    "Invalid prefetch configuration structure.",
    google::scp::core::errors::HttpStatusCode::INTERNAL_SERVER_ERROR)

DEFINE_ERROR_CODE(
    SC_CPIO_PREFETCH_CONFIG_PARSE_ERROR, SC_CPIO_PREFETCH_CONFIG_VALIDATOR,
    0x0002, "The prefetch config textproto was unable to be parsed.",
    google::scp::core::errors::HttpStatusCode::INTERNAL_SERVER_ERROR)

DEFINE_ERROR_CODE(
    SC_CPIO_PREFETCH_CONFIG_INVALID_DURATION, SC_CPIO_PREFETCH_CONFIG_VALIDATOR,
    0x0003, "Prefetch config contains an invalid duration (e.g. negative).",
    google::scp::core::errors::HttpStatusCode::INTERNAL_SERVER_ERROR)

DEFINE_ERROR_CODE(
    SC_CPIO_PREFETCH_CONFIG_INVALID_KEY_IDS, SC_CPIO_PREFETCH_CONFIG_VALIDATOR,
    0x0004, "Prefetch config contains invalid key_ids (e.g., empty string).",
    google::scp::core::errors::HttpStatusCode::INTERNAL_SERVER_ERROR)

DEFINE_ERROR_CODE(
    SC_CPIO_PREFETCH_CONFIG_MISSING_VALUES, SC_CPIO_PREFETCH_CONFIG_VALIDATOR,
    0x0005,
    "Prefetch config must contain either key_ids or prefetch_duration if "
    "namespace is set.",
    google::scp::core::errors::HttpStatusCode::INTERNAL_SERVER_ERROR)

DEFINE_ERROR_CODE(
    SC_CPIO_PREFETCH_CONFIG_DUPLICATE_NAMESPACE,
    SC_CPIO_PREFETCH_CONFIG_VALIDATOR, 0x0006,
    "Prefetch config contains a duplicate namespace.",
    google::scp::core::errors::HttpStatusCode::INTERNAL_SERVER_ERROR)

}  // namespace google::scp::cpio
