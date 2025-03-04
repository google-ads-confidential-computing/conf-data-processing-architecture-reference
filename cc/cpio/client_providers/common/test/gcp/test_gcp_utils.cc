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

#include <gtest/gtest.h>

#include <memory>

#include "cpio/client_providers/common/src/gcp/gcp_utils.h"
#include "cpio/client_providers/instance_client_provider/mock/mock_instance_client_provider.h"
#include "cpio/client_providers/instance_client_provider/src/gcp/gcp_instance_client_utils.h"

using google::scp::cpio::client_providers::mock::MockInstanceClientProvider;

namespace {
constexpr char kInstanceResourceName[] =
    R"(//compute.googleapis.com/projects/123456789/zones/us-central1-c/instances/987654321)";
constexpr char kTestModule[] =
    R"(//compute.googleapis.com/projects/123456789/zones/us-central1-c/instances/987654321)";
}  // namespace

namespace google::scp::cpio::client_providers {

TEST(GcpUtilsTest, ReturnsCorrectProjectName) {
  auto instance_client_provider_mock =
      std::make_shared<MockInstanceClientProvider>();
  instance_client_provider_mock->instance_resource_name = kInstanceResourceName;
  auto instance_resource =
      GcpUtils::GetInstanceResource(kTestModule, instance_client_provider_mock)
          .value();
  EXPECT_EQ(GcpUtils::GetProjectNameFromInstanceResource(instance_resource),
            "projects/123456789");
}

}  // namespace google::scp::cpio::client_providers
