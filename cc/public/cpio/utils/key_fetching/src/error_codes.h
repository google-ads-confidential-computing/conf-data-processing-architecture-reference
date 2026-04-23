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
#include "public/core/interface/execution_result.h"

namespace google::scp::core::errors {

/// Registers component code as 0x0222 for SC_CPIO_KEY_FETCHER.
REGISTER_COMPONENT_CODE(SC_CPIO_KEY_FETCHER, 0x0222)

DEFINE_ERROR_CODE(SC_CPIO_KEY_NOT_FOUND, SC_CPIO_KEY_FETCHER, 0x0001,
                  "Key is not found in the component",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_CPIO_KEY_COUNT_MISMATCH, SC_CPIO_KEY_FETCHER, 0x0002,
                  "The key count is not as expected",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_CPIO_KEY_FETCHER_FETCHING_TIMEOUT, SC_CPIO_KEY_FETCHER,
                  0x0003, "The key fetching timeout",
                  HttpStatusCode::REQUEST_TIMEOUT)
DEFINE_ERROR_CODE(SC_CPIO_KEY_FETCHER_NO_KEY_FETCHED, SC_CPIO_KEY_FETCHER,
                  0x0004,
                  "The key fetching response is empty from the key service",
                  HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_CPIO_KEY_FETCHER_CACHE_INSERT_FAILURE,
                  SC_CPIO_KEY_FETCHER, 0x0005, "Failed to insert keys to cache",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_CPIO_KEY_FETCHER_INVALID_KEY_SELECTION_TIMESTAMP,
                  SC_CPIO_KEY_FETCHER, 0x0006,
                  "The key selection timestamp is invalid",
                  HttpStatusCode::BAD_REQUEST)
}  // namespace google::scp::core::errors
