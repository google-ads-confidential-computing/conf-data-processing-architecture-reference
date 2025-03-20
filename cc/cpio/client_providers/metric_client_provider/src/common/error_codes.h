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

#include "core/interface/errors.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/error_codes.h"

namespace google::scp::core::errors {
/// Registers component code as 0x0216 for metric client provider.
REGISTER_COMPONENT_CODE(SC_METRIC_CLIENT_PROVIDER, 0x0216)

DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_IS_NOT_RUNNING,
                  SC_METRIC_CLIENT_PROVIDER, 0x0001,
                  "Metric client isn't running", HttpStatusCode::CONFLICT)
DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_IS_ALREADY_RUNNING,
                  SC_METRIC_CLIENT_PROVIDER, 0x0002,
                  "Metric client is already running", HttpStatusCode::CONFLICT)
DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_NAMESPACE_NOT_SET,
                  SC_METRIC_CLIENT_PROVIDER, 0x0003,
                  "Metric namespace cannot be empty",
                  HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_METRIC_NOT_SET,
                  SC_METRIC_CLIENT_PROVIDER, 0x0004, "Metric cannot be empty",
                  HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_METRIC_NAME_NOT_SET,
                  SC_METRIC_CLIENT_PROVIDER, 0x0005,
                  "Metric name cannot be empty", HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_METRIC_VALUE_NOT_SET,
                  SC_METRIC_CLIENT_PROVIDER, 0x0006,
                  "Metric value cannot be empty", HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_EXECUTOR_NOT_AVAILABLE,
                  SC_METRIC_CLIENT_PROVIDER, 0x0007,
                  "No executor for batch recording",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)
DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_INCONSISTENT_NAMESPACE,
                  SC_METRIC_CLIENT_PROVIDER, 0x0008,
                  "Metric namespaces should be the same for all metrics",
                  HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_NAMESPACE_FOR_BATCHING_NOT_SET,
                  SC_METRIC_CLIENT_PROVIDER, 0x0009,
                  "Metric namespace for batch recording cannot be empty",
                  HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(
    SC_METRIC_CLIENT_PROVIDER_OTEL_REMOTE_COLLECTOR_ADDRESS_NOT_SET,
    SC_METRIC_CLIENT_PROVIDER, 0x000A,
    "OpenTelemetry remote collector host address cannot be empty",
    HttpStatusCode::BAD_REQUEST)
DEFINE_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_METRIC_TYPE_NOT_SET,
                  SC_METRIC_CLIENT_PROVIDER, 0x000B,
                  "Metric type cannot be empty for OpenTelemetry API.",
                  HttpStatusCode::BAD_REQUEST)

MAP_TO_PUBLIC_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_IS_NOT_RUNNING,
                         SC_CPIO_COMPONENT_NOT_RUNNING)
MAP_TO_PUBLIC_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_IS_ALREADY_RUNNING,
                         SC_CPIO_COMPONENT_ALREADY_RUNNING)
MAP_TO_PUBLIC_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_NAMESPACE_NOT_SET,
                         SC_CPIO_INVALID_ARGUMENT)
MAP_TO_PUBLIC_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_METRIC_NOT_SET,
                         SC_CPIO_INVALID_ARGUMENT)
MAP_TO_PUBLIC_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_METRIC_NAME_NOT_SET,
                         SC_CPIO_INVALID_ARGUMENT)
MAP_TO_PUBLIC_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_METRIC_VALUE_NOT_SET,
                         SC_CPIO_INVALID_ARGUMENT)
MAP_TO_PUBLIC_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_EXECUTOR_NOT_AVAILABLE,
                         SC_CPIO_INTERNAL_ERROR)
MAP_TO_PUBLIC_ERROR_CODE(SC_METRIC_CLIENT_PROVIDER_INCONSISTENT_NAMESPACE,
                         SC_CPIO_INVALID_ARGUMENT)
MAP_TO_PUBLIC_ERROR_CODE(
    SC_METRIC_CLIENT_PROVIDER_NAMESPACE_FOR_BATCHING_NOT_SET,
    SC_CPIO_INVALID_ARGUMENT)
MAP_TO_PUBLIC_ERROR_CODE(
    SC_METRIC_CLIENT_PROVIDER_OTEL_REMOTE_COLLECTOR_ADDRESS_NOT_SET,
    SC_CPIO_INVALID_ARGUMENT)

}  // namespace google::scp::core::errors
