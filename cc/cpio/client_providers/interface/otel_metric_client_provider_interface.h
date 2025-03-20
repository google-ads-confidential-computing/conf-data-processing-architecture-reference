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

#pragma once

#include <memory>
#include <string>

#include "core/interface/service_interface.h"
#include "cpio/client_providers/interface/instance_client_provider_interface.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/metric_client/metric_client_interface.h"
#include "public/cpio/interface/metric_client/type_def.h"
#include "public/cpio/proto/metric_service/v1/metric_service.pb.h"

namespace google::scp::cpio::client_providers {

class OtelMetricClientProviderFactory {
 public:
  /**
   * @brief Factory to create OtelMetricClientProvider.
   *
   * @return std::shared_ptr<MetricClientInterface> created
   * MetricClientProvider.
   */
  static std::shared_ptr<MetricClientInterface> Create(
      const std::shared_ptr<MetricClientOptions>& options,
      const std::shared_ptr<InstanceClientProviderInterface>&
          instance_client_provider);
};
}  // namespace google::scp::cpio::client_providers
