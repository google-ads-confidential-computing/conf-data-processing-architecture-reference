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

#include "test_gcp_blob_storage_service_factory.h"

#include <memory>

#include "cpio/client_providers/blob_storage_client_provider/test/gcp/test_gcp_blob_storage_client_provider.h"
#include "cpio/server/src/instance_service/test_gcp/test_gcp_instance_service_factory.h"
#include "cpio/server/src/service_utils.h"
#include "public/cpio/proto/blob_storage_service/v1/test_configuration_keys.pb.h"

using google::cmrt::sdk::blob_storage_service::v1::TestClientConfigurationKeys;
using google::cmrt::sdk::blob_storage_service::v1::
    TestClientConfigurationKeys_Name;
using google::scp::cpio::client_providers::BlobStorageClientProviderInterface;
using google::scp::cpio::client_providers::GcpBlobStorageClientProvider;
using google::scp::cpio::client_providers::TestGcpCloudStorageFactory;
using std::make_shared;
using std::shared_ptr;

namespace google::scp::cpio {
shared_ptr<InstanceServiceFactoryInterface>
TestGcpBlobStorageServiceFactory::CreateInstanceServiceFactory() noexcept {
  return make_shared<TestGcpInstanceServiceFactory>(
      config_provider_, instance_service_factory_options_);
}

shared_ptr<InstanceServiceFactoryOptions> TestGcpBlobStorageServiceFactory::
    CreateInstanceServiceFactoryOptions() noexcept {
  auto options =
      GcpBlobStorageServiceFactory::CreateInstanceServiceFactoryOptions();
  auto test_options =
      make_shared<TestGcpInstanceServiceFactoryOptions>(*options);
  test_options->project_id_config_label = TestClientConfigurationKeys_Name(
      TestClientConfigurationKeys::CMRT_TEST_BLOB_STORAGE_CLIENT_OWNER_ID);
  return test_options;
}

std::shared_ptr<BlobStorageClientProviderInterface>
TestGcpBlobStorageServiceFactory::CreateBlobStorageClient() noexcept {
  TryReadConfigString(
      config_provider_,
      TestClientConfigurationKeys_Name(
          TestClientConfigurationKeys::
              CMRT_TEST_GCP_BLOB_STORAGE_CLIENT_IMPERSONATE_SERVICE_ACCOUNT),
      test_options_->impersonate_service_account);

  return make_shared<GcpBlobStorageClientProvider>(
      test_options_, instance_client_,
      instance_service_factory_->GetCpuAsynceExecutor(),
      instance_service_factory_->GetIoAsynceExecutor(),
      std::make_shared<TestGcpCloudStorageFactory>());
}
}  // namespace google::scp::cpio
