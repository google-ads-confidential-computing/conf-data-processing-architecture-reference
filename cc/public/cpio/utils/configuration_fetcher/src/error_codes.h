// Copyright 2023 Google LLC
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

#include "core/interface/errors.h"
#include "public/core/interface/execution_result.h"

namespace google::scp::core::errors {
REGISTER_COMPONENT_CODE(SC_CONFIGURATION_FETCHER, 0x021D)

DEFINE_ERROR_CODE(SC_CONFIGURATION_FETCHER_ENVIRONMENT_NAME_NOT_FOUND,
                  SC_CONFIGURATION_FETCHER, 0x0001,
                  "Environment name is not found", HttpStatusCode::NOT_FOUND)

DEFINE_ERROR_CODE(SC_CONFIGURATION_FETCHER_INSTANCE_RESOURCE_NAME_NOT_FOUND,
                  SC_CONFIGURATION_FETCHER, 0x0005,
                  "Environment name is not found", HttpStatusCode::NOT_FOUND)

DEFINE_ERROR_CODE(SC_CONFIGURATION_FETCHER_CONVERSION_FAILED,
                  SC_CONFIGURATION_FETCHER, 0x0002,
                  "Failed to convert the parameter value to required type",
                  HttpStatusCode::BAD_REQUEST)

DEFINE_ERROR_CODE(SC_CONFIGURATION_FETCHER_INVALID_ENVIRONMENT_NAME_LABEL,
                  SC_CONFIGURATION_FETCHER, 0x0003,
                  "Environment name tag is empty or invalid.",
                  HttpStatusCode::BAD_REQUEST)

DEFINE_ERROR_CODE(SC_CONFIGURATION_FETCHER_INVALID_PARAMETER_NAME,
                  SC_CONFIGURATION_FETCHER, 0x0004,
                  "Parameter name is empty or invalid.",
                  HttpStatusCode::BAD_REQUEST)
}  // namespace google::scp::core::errors
