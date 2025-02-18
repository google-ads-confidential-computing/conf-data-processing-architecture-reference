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

#include "aws_role_credentials_provider.h"

using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResult;
using std::make_shared;
using std::shared_ptr;

namespace google::scp::cpio::client_providers {
#ifndef TEST_CPIO
std::shared_ptr<RoleCredentialsProviderInterface>
RoleCredentialsProviderFactory::Create(
    const shared_ptr<RoleCredentialsProviderOptions>& options,
    const shared_ptr<InstanceClientProviderInterface>& instance_client_provider,
    const shared_ptr<core::AsyncExecutorInterface>& cpu_async_executor,
    const shared_ptr<core::AsyncExecutorInterface>& io_async_executor,
    const shared_ptr<AuthTokenProviderInterface>&
        auth_token_provider) noexcept {
  return make_shared<AwsRoleCredentialsProvider>(
      options, instance_client_provider, cpu_async_executor, io_async_executor,
      auth_token_provider);
}
#endif
}  // namespace google::scp::cpio::client_providers
