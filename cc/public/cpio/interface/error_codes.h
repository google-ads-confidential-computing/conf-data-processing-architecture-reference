// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef SCP_CPIO_INTERFACE_ERROR_CODES_H_
#define SCP_CPIO_INTERFACE_ERROR_CODES_H_

#include "core/interface/errors.h"
#include "public/core/interface/execution_result.h"

namespace google::scp::core::errors {
/// Registers component code as 0x0214 for CPIO common errors.
REGISTER_COMPONENT_CODE(SC_CPIO, 0x0214)

DEFINE_ERROR_CODE(SC_CPIO_UNKNOWN_ERROR, SC_CPIO, 0x0001,
                  "Unknown Error in Cloud Service", HttpStatusCode::UNKNOWN)
DEFINE_ERROR_CODE(SC_CPIO_INTERNAL_ERROR, SC_CPIO, 0x0002,
                  "Internal Error in Cloud Service",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_CPIO_INVALID_CREDENTIALS, SC_CPIO, 0x0003,
                  "Invalid Cloud credentials", HttpStatusCode::UNAUTHORIZED)
DEFINE_ERROR_CODE(SC_CPIO_REQUEST_LIMIT_REACHED, SC_CPIO, 0x0004,
                  "Reach request limit in Cloud Service",
                  HttpStatusCode::TOO_MANY_REQUESTS)
DEFINE_ERROR_CODE(SC_CPIO_SERVICE_UNAVAILABLE, SC_CPIO, 0x0005,
                  "Cloud service unavailable",
                  HttpStatusCode::SERVICE_UNAVAILABLE)
DEFINE_ERROR_CODE(SC_CPIO_NOT_IMPLEMENTED, SC_CPIO, 0x0006, "Not implemented",
                  HttpStatusCode::NOT_IMPLEMENTED)
DEFINE_ERROR_CODE(SC_CPIO_REQUEST_TIMEOUT, SC_CPIO, 0x0007, "Request timeout",
                  HttpStatusCode::REQUEST_TIMEOUT)
DEFINE_ERROR_CODE(SC_CPIO_ENTITY_NOT_FOUND, SC_CPIO, 0x0008, "Not found",
                  HttpStatusCode::NOT_FOUND)
DEFINE_ERROR_CODE(SC_CPIO_INVALID_ARGUMENT, SC_CPIO, 0x0009, "Invalid argument",
                  HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_CPIO_REQUEST_ABORTED, SC_CPIO, 0x000A, "Request aborted",
                  HttpStatusCode::CANCELLED)
DEFINE_ERROR_CODE(SC_CPIO_ALREADY_EXISTS, SC_CPIO, 0x000B, "Already exists",
                  HttpStatusCode::CONFLICT)
DEFINE_ERROR_CODE(SC_CPIO_REQUEST_TOO_LARGE, SC_CPIO, 0x000C,
                  "Parameters in request exceeded limit",
                  HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_CPIO_INVALID_RESOURCE, SC_CPIO, 0x000D,
                  "Resources validation failed", HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_CPIO_RESOURCE_NOT_FOUND, SC_CPIO, 0x000E,
                  "Resources not found", HttpStatusCode::NOT_FOUND)
DEFINE_ERROR_CODE(SC_CPIO_MULTIPLE_ENTITIES_FOUND, SC_CPIO, 0x000F,
                  "Multiple Entities found",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)

DEFINE_ERROR_CODE(SC_CPIO_COMPONENT_FAILED_INITIALIZED, SC_CPIO, 0x0010,
                  "The component is failed to initialized",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_CPIO_COMPONENT_NOT_RUNNING, SC_CPIO, 0x0011,
                  "The component is not running in CPIO",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_CPIO_COMPONENT_ALREADY_RUNNING, SC_CPIO, 0x00012,
                  "The component is already running in CPIO",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)

DEFINE_ERROR_CODE(SC_CPIO_EXHAUSTED_RETRIES, SC_CPIO, 0x0013,
                  "Retry count exhausted",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)

// Granular errors for customer-managed wrapped keys, returned when unwrapping
// a data encryption key (DEK) with a key encryption key (KEK) in a cloud KMS.
DEFINE_ERROR_CODE(SC_CPIO_DEK_DECRYPTION_ERROR, SC_CPIO, 0x0014,
                  "The encrypted DEK is missing, malformed, or cannot be "
                  "decrypted with the KEK",
                  HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_CPIO_INVALID_KEK, SC_CPIO, 0x0015,
                  "The KEK is missing, malformed, does not exist, or is "
                  "disabled",
                  HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_CPIO_FEDERATED_AUTH_FAILED, SC_CPIO, 0x0016,
                  "Failed to authenticate with the cloud through identity "
                  "federation",
                  HttpStatusCode::UNAUTHORIZED)
DEFINE_ERROR_CODE(SC_CPIO_KEK_PERMISSION_DENIED, SC_CPIO, 0x0017,
                  "Permission denied to decrypt with the KEK",
                  HttpStatusCode::FORBIDDEN)

// Map CPIO unknown error
MAP_TO_PUBLIC_ERROR_CODE(SC_UNKNOWN, SC_CPIO_UNKNOWN_ERROR)
}  // namespace google::scp::core::errors

#endif  // SCP_CPIO_INTERFACE_ERROR_CODES_H_
