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

#pragma once

#include "cc/core/interface/errors.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/error_codes.h"

namespace google::scp::core::errors {

/// Registers component code as 0x0009 for dispatcher.
REGISTER_COMPONENT_CODE(SC_DISPATCHER, 0x0009)

DEFINE_ERROR_CODE(SC_DISPATCHER_EXHAUSTED_RETRIES, SC_DISPATCHER, 0x0001,
                  "Retry operation is exhausted.",
                  HttpStatusCode::SERVICE_UNAVAILABLE)

DEFINE_ERROR_CODE(SC_DISPATCHER_OPERATION_EXPIRED, SC_DISPATCHER, 0x0002,
                  "The operation expiration time has reached.",
                  HttpStatusCode::REQUEST_TIMEOUT)

DEFINE_ERROR_CODE(SC_DISPATCHER_NOT_ENOUGH_TIME_REMAINED_FOR_OPERATION,
                  SC_DISPATCHER, 0x0003,
                  "Not enough time remaining to continue the operation.",
                  HttpStatusCode::REQUEST_TIMEOUT)

MAP_TO_PUBLIC_ERROR_CODE(SC_DISPATCHER_EXHAUSTED_RETRIES,
                         SC_CPIO_EXHAUSTED_RETRIES)
MAP_TO_PUBLIC_ERROR_CODE(SC_DISPATCHER_OPERATION_EXPIRED,
                         SC_CPIO_REQUEST_TIMEOUT)
MAP_TO_PUBLIC_ERROR_CODE(SC_DISPATCHER_NOT_ENOUGH_TIME_REMAINED_FOR_OPERATION,
                         SC_CPIO_REQUEST_TIMEOUT)
}  // namespace google::scp::core::errors
