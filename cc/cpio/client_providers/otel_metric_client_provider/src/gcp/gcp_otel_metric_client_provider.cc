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

#include "gcp_otel_metric_client_provider.h"

#include <string>

#include "core/common/uuid/src/uuid.h"
#include "cpio/client_providers/common/src/gcp/gcp_utils.h"
#include "cpio/client_providers/instance_client_provider/src/gcp/gcp_instance_client_utils.h"
#include "cpio/client_providers/interface/otel_metric_client_provider_interface.h"
#include "cpio/client_providers/otel_metric_client_provider/src/opentelemetry_utils.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"

using google::cmrt::sdk::instance_service::v1::InstanceDetails;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::cpio::client_providers::GcpInstanceClientUtils;
using google::scp::cpio::client_providers::GcpInstanceResourceNameDetails;
using std::make_shared;
using std::shared_ptr;
using std::string;

namespace {
constexpr char kGcpOtelMetricClientProvider[] = "GcpOtelMetricClientProvider";
}  // namespace

namespace google::scp::cpio::client_providers {

ExecutionResult GcpOtelMetricClientProvider::Run() noexcept {
  auto execution_result = OtelMetricClientProvider::Run();
  if (!execution_result.Successful()) {
    SCP_ERROR(kGcpOtelMetricClientProvider, kZeroUuid, execution_result,
              "Failed to initialize OtelMetricClientProvider");
    return execution_result;
  }

  ASSIGN_OR_RETURN(instance_resource_,
                   GcpUtils::GetInstanceResource(kGcpOtelMetricClientProvider,
                                                 instance_client_provider_));

  project_name_ =
      GcpUtils::GetProjectNameFromInstanceResource(instance_resource_);

  fixed_labels_[kResourceInstanceIdLabel] = instance_resource_.instance_id;
  fixed_labels_[kResourceZoneLabel] = instance_resource_.zone_id;

  return SuccessExecutionResult();
}

#ifndef TEST_CPIO
shared_ptr<MetricClientInterface> OtelMetricClientProviderFactory::Create(
    const shared_ptr<MetricClientOptions>& options,
    const shared_ptr<InstanceClientProviderInterface>&
        instance_client_provider) {
  std::string resource_name;
  if (auto result =
          instance_client_provider->GetCurrentInstanceResourceNameSync(
              resource_name);
      !result.Successful()) {
    SCP_ERROR(kGcpOtelMetricClientProvider, kZeroUuid, result,
              "Failed to get current instance resource name.");
    return nullptr;
  }
  auto instance_id_or =
      GcpInstanceClientUtils::ParseInstanceIdFromInstanceResourceName(
          resource_name);
  auto zone_or = GcpInstanceClientUtils::ParseZoneIdFromInstanceResourceName(
      resource_name);
  if (!instance_id_or.Successful()) {
    SCP_ERROR(kGcpOtelMetricClientProvider, kZeroUuid, instance_id_or.result(),
              "Failed to get current instance ID.");
    return nullptr;
  }
  if (!zone_or.Successful()) {
    SCP_ERROR(kGcpOtelMetricClientProvider, kZeroUuid, zone_or.result(),
              "Failed to get current instance zone.");
    return nullptr;
  }

  return make_shared<GcpOtelMetricClientProvider>(
      options, instance_client_provider,
      OpenTelemetryUtils::CreateOpenTelemetryMeter(
          options, std::move(*instance_id_or), std::move(*zone_or)));
}
#endif
}  // namespace google::scp::cpio::client_providers
