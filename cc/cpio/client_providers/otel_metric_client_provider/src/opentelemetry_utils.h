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

#include "opentelemetry/sdk/metrics/meter_provider.h"
#include "public/cpio/interface/metric_client/type_def.h"

namespace google::scp::cpio::client_providers {

static constexpr char kResourceInstanceIdLabel[] = "exported_instance_id";
static constexpr char kResourceZoneLabel[] = "exported_zone";

class OpenTelemetryUtils {
 public:
  static std::shared_ptr<opentelemetry::metrics::Meter>
  CreateOpenTelemetryMeter(
      const std::shared_ptr<google::scp::cpio::MetricClientOptions>& options,
      const std::string& instance_id, const std::string& zone) noexcept;
};

}  // namespace google::scp::cpio::client_providers
