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

namespace google::scp::cpio {

REGISTER_COMPONENT_CODE(SC_CPIO_DUAL_WRITING_METRIC_CLIENT, 0x021F)

DEFINE_ERROR_CODE(SC_CPIO_METRIC_NOT_FOUND, SC_CPIO_DUAL_WRITING_METRIC_CLIENT,
                  0x0001, "Could not find the Metric",
                  scp::core::errors::HttpStatusCode::NOT_FOUND)

}  // namespace google::scp::cpio
