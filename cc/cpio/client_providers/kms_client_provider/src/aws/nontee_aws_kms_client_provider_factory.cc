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

#include "core/interface/async_executor_interface.h"
#include "cpio/client_providers/interface/role_credentials_provider_interface.h"

#include "nontee_aws_kms_client_provider.h"

using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResult;
using std::make_shared;
using std::shared_ptr;

namespace google::scp::cpio::client_providers {
#ifndef TEST_CPIO
std::shared_ptr<KmsClientProviderInterface> KmsClientProviderFactory::Create(
    const shared_ptr<KmsClientOptions>& options,
    const shared_ptr<RoleCredentialsProviderInterface>&
        role_credentials_provider,
    const shared_ptr<core::AsyncExecutorInterface>& io_async_executor,
    const std::shared_ptr<core::AsyncExecutorInterface>&
        cpu_async_executor) noexcept {
  return make_shared<NonteeAwsKmsClientProvider>(
      options, role_credentials_provider, io_async_executor,
      cpu_async_executor);
}
#endif
}  // namespace google::scp::cpio::client_providers
