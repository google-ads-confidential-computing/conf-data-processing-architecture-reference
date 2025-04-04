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

#pragma once

#include <gmock/gmock.h>

#include <memory>

#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/private_key_client/private_key_client_interface.h"

namespace google::scp::cpio {
class MockPrivateKeyClient : public PrivateKeyClientInterface {
 public:
  MockPrivateKeyClient() {
    ON_CALL(*this, Init)
        .WillByDefault(testing::Return(core::SuccessExecutionResult()));
    ON_CALL(*this, Run)
        .WillByDefault(testing::Return(core::SuccessExecutionResult()));
    ON_CALL(*this, Stop)
        .WillByDefault(testing::Return(core::SuccessExecutionResult()));
  }

  MOCK_METHOD(core::ExecutionResult, Init, (), (noexcept, override));
  MOCK_METHOD(core::ExecutionResult, Run, (), (noexcept, override));
  MOCK_METHOD(core::ExecutionResult, Stop, (), (noexcept, override));

  MOCK_METHOD(
      void, ListPrivateKeys,
      ((core::AsyncContext<
            cmrt::sdk::private_key_service::v1::ListPrivateKeysRequest,
            cmrt::sdk::private_key_service::v1::ListPrivateKeysResponse> &
        context)),
      (noexcept, override));

  MOCK_METHOD(core::ExecutionResultOr<
                  cmrt::sdk::private_key_service::v1::ListPrivateKeysResponse>,
              ListPrivateKeysSync,
              ((cmrt::sdk::private_key_service::v1::ListPrivateKeysRequest)),
              (noexcept, override));

  MOCK_METHOD(
      void, ListActiveEncryptionKeys,
      ((core::AsyncContext<
            cmrt::sdk::private_key_service::v1::ListActiveEncryptionKeysRequest,
            cmrt::sdk::private_key_service::v1::
                ListActiveEncryptionKeysResponse> &
        context)),
      (noexcept, override));

  MOCK_METHOD(
      core::ExecutionResultOr<
          cmrt::sdk::private_key_service::v1::ListActiveEncryptionKeysResponse>,
      ListActiveEncryptionKeysSync,
      ((cmrt::sdk::private_key_service::v1::ListActiveEncryptionKeysRequest)),
      (noexcept, override));
};

}  // namespace google::scp::cpio
