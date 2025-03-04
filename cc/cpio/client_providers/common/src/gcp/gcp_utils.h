/*
 * Copyright 2025 Google LLC
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

#include <memory>
#include <string>

#include <grpcpp/support/status.h>

#include "cpio/client_providers/instance_client_provider/src/gcp/gcp_instance_client_utils.h"
#include "google/cloud/status.h"

namespace google::scp::cpio::client_providers {

class GcpUtils {
 public:
  static core::ExecutionResultOr<GcpInstanceResourceNameDetails>
  GetInstanceResource(const std::string& logger_origin_module,
                      const std::shared_ptr<InstanceClientProviderInterface>&
                          instance_client_provider) noexcept;

  static std::string GetProjectNameFromInstanceResource(
      const GcpInstanceResourceNameDetails& instance_resource) noexcept;
};
}  // namespace google::scp::cpio::client_providers
