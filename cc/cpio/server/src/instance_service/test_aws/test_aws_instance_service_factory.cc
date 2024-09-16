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

#include "test_aws_instance_service_factory.h"

#include <memory>
#include <string>

#include "cpio/client_providers/instance_client_provider/test/aws/test_aws_instance_client_provider.h"
#include "cpio/server/src/service_utils.h"
#include "public/cpio/proto/instance_service/v1/test_configuration_keys.pb.h"

using google::cmrt::sdk::instance_service::v1::TestClientConfigurationKeys;
using google::cmrt::sdk::instance_service::v1::TestClientConfigurationKeys_Name;
using google::scp::core::ExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::cpio::TryReadConfigString;
using google::scp::cpio::client_providers::InstanceClientProviderInterface;
using google::scp::cpio::client_providers::TestAwsInstanceClientProvider;
using google::scp::cpio::client_providers::TestInstanceClientOptions;
using std::dynamic_pointer_cast;
using std::make_shared;
using std::shared_ptr;
using std::string;

namespace {
constexpr char kDefaultRegion[] = "us-east-1";
}  // namespace

namespace google::scp::cpio {

ExecutionResult TestAwsInstanceServiceFactory::Init() noexcept {
  region_ = kDefaultRegion;
  auto test_options =
      dynamic_pointer_cast<TestAwsInstanceServiceFactoryOptions>(options_);
  if (test_options->region_config_label.empty()) {
    test_options->region_config_label = TestClientConfigurationKeys_Name(
        TestClientConfigurationKeys::CMRT_TEST_INSTANCE_CLIENT_REGION);
  }
  TryReadConfigString(config_provider_, test_options->region_config_label,
                      region_);

  RETURN_IF_FAILURE(AwsInstanceServiceFactory::Init());

  return SuccessExecutionResult();
}

shared_ptr<InstanceClientProviderInterface>
TestAwsInstanceServiceFactory::CreateInstanceClient() noexcept {
  auto options = make_shared<TestInstanceClientOptions>();
  options->region = region_;
  return make_shared<TestAwsInstanceClientProvider>(options);
}

}  // namespace google::scp::cpio
