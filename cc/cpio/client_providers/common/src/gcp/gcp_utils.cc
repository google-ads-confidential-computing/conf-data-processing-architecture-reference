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

#include "gcp_utils.h"

#include <memory>

#include "absl/strings/str_format.h"
#include "core/common/global_logger/src/global_logger.h"
#include "cpio/client_providers/instance_client_provider/src/gcp/gcp_instance_client_utils.h"

using google::scp::core::ExecutionResult;
using google::scp::core::ExecutionResultOr;
using google::scp::core::common::kZeroUuid;

constexpr char kProjectNamePrefix[] = "projects/";

namespace google::scp::cpio::client_providers {

ExecutionResultOr<GcpInstanceResourceNameDetails> GcpUtils::GetInstanceResource(
    const std::string& logger_origin_module,
    const std::shared_ptr<InstanceClientProviderInterface>&
        instance_client_provider) noexcept {
  std::string instance_resource_name;
  auto execution_result =
      instance_client_provider->GetCurrentInstanceResourceNameSync(
          instance_resource_name);
  if (!execution_result.Successful()) {
    SCP_ERROR(logger_origin_module, kZeroUuid, execution_result,
              "Failed to fetch current instance resource name");
    return execution_result;
  }

  GcpInstanceResourceNameDetails instance_resource;
  execution_result = GcpInstanceClientUtils::GetInstanceResourceNameDetails(
      instance_resource_name, instance_resource);
  if (!execution_result.Successful()) {
    SCP_ERROR(logger_origin_module, kZeroUuid, execution_result,
              "Failed to parse instance resource name %s",
              instance_resource_name.c_str());
  }

  return instance_resource;
}

std::string GcpUtils::GetProjectNameFromInstanceResource(
    const GcpInstanceResourceNameDetails& instance_resource) noexcept {
  return std::string(kProjectNamePrefix) + instance_resource.project_id;
}

}  // namespace google::scp::cpio::client_providers
