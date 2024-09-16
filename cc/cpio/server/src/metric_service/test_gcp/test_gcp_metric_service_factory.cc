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

#include "test_gcp_metric_service_factory.h"

#include <memory>

#include "cpio/server/src/instance_service/test_gcp/test_gcp_instance_service_factory.h"
#include "public/cpio/proto/metric_service/v1/test_configuration_keys.pb.h"

using google::cmrt::sdk::metric_service::v1::TestClientConfigurationKeys;
using google::cmrt::sdk::metric_service::v1::TestClientConfigurationKeys_Name;
using std::make_shared;
using std::shared_ptr;

namespace google::scp::cpio {
shared_ptr<InstanceServiceFactoryInterface>
TestGcpMetricServiceFactory::CreateInstanceServiceFactory() noexcept {
  return make_shared<TestGcpInstanceServiceFactory>(
      config_provider_, instance_service_factory_options_);
}

shared_ptr<InstanceServiceFactoryOptions>
TestGcpMetricServiceFactory::CreateInstanceServiceFactoryOptions() noexcept {
  auto options = make_shared<TestGcpInstanceServiceFactoryOptions>(
      *MetricServiceFactory::CreateInstanceServiceFactoryOptions());
  options->project_id_config_label = TestClientConfigurationKeys_Name(
      TestClientConfigurationKeys::CMRT_TEST_METRIC_CLIENT_OWNER_ID);
  options->zone_config_label = TestClientConfigurationKeys_Name(
      TestClientConfigurationKeys::CMRT_TEST_METRIC_CLIENT_ZONE);
  options->instance_id_config_label = TestClientConfigurationKeys_Name(
      TestClientConfigurationKeys::CMRT_TEST_METRIC_CLIENT_INSTANCE_ID);
  return options;
}
}  // namespace google::scp::cpio
