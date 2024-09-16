/*
 * Copyright 2022 Google LLC
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

REGISTER_COMPONENT_CODE(SC_NETWORK_SERVICE, 0x0010)

DEFINE_ERROR_CODE(SC_NETWORK_SERVICE_INIT_ERROR, SC_NETWORK_SERVICE, 0x0001,
                  "Network service init error.",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_NETWORK_SERVICE_DOUBLE_INIT_ERROR, SC_NETWORK_SERVICE,
                  0x0002, "Network service RPC double initialization.",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_NETWORK_SERVICE_OOM, SC_NETWORK_SERVICE, 0x0003,
                  "Network service out of memory.",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_NETWORK_SERVICE_START_ERROR, SC_NETWORK_SERVICE, 0x0004,
                  "Network service cannot start.",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_NETWORK_SERVICE_CANNOT_PARSE, SC_NETWORK_SERVICE, 0x0005,
                  "Network service cannot parse.",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_NETWORK_SERVICE_CANNOT_SERIALIZE, SC_NETWORK_SERVICE,
                  0x0006, "Network service cannot start.",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
}  // namespace google::scp::core::errors
