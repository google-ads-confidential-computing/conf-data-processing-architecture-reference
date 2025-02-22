// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "public/cpio/adapters/private_key_client/src/private_key_client.h"

#include <gmock/gmock.h>

#include <memory>
#include <string>
#include <utility>

#include "core/http2_client/src/error_codes.h"
#include "core/interface/errors.h"
#include "core/test/utils/conditional_wait.h"
#include "core/test/utils/scp_test_base.h"
#include "cpio/client_providers/private_key_client_provider/mock/mock_private_key_client_provider_with_overrides.h"
#include "cpio/client_providers/private_key_client_provider/src/error_codes.h"
#include "public/core/interface/execution_result.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/adapters/private_key_client/mock/mock_private_key_client_with_overrides.h"
#include "public/cpio/interface/error_codes.h"
#include "public/cpio/interface/private_key_client/private_key_client_interface.h"
#include "public/cpio/interface/private_key_client/type_def.h"
#include "public/cpio/proto/private_key_service/v1/private_key_service.pb.h"

using google::cmrt::sdk::private_key_service::v1::
    ListActiveEncryptionKeysRequest;
using google::cmrt::sdk::private_key_service::v1::
    ListActiveEncryptionKeysResponse;
using google::cmrt::sdk::private_key_service::v1::ListPrivateKeysRequest;
using google::cmrt::sdk::private_key_service::v1::ListPrivateKeysResponse;
using google::scp::core::AsyncContext;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::errors::SC_CPIO_ENTITY_NOT_FOUND;
using google::scp::core::errors::SC_CPIO_INVALID_ARGUMENT;
using google::scp::core::errors::SC_CPIO_UNKNOWN_ERROR;
using google::scp::core::errors::SC_HTTP2_CLIENT_HTTP_STATUS_NOT_FOUND;
using google::scp::core::errors::SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_REQUEST;
using google::scp::core::test::IsSuccessful;
using google::scp::core::test::ResultIs;
using google::scp::core::test::ScpTestBase;
using google::scp::core::test::WaitUntil;
using google::scp::cpio::PrivateKeyClient;
using google::scp::cpio::PrivateKeyClientOptions;
using google::scp::cpio::mock::MockPrivateKeyClientWithOverrides;
using std::atomic;
using std::make_shared;
using std::make_unique;
using std::move;
using std::shared_ptr;
using std::string;
using std::unique_ptr;
using testing::Return;

namespace google::scp::cpio::test {
class PrivateKeyClientTest : public ScpTestBase {
 protected:
  PrivateKeyClientTest() {
    auto private_key_client_options = make_shared<PrivateKeyClientOptions>();
    client_ = make_unique<MockPrivateKeyClientWithOverrides>(
        private_key_client_options);

    EXPECT_THAT(client_->Init(), IsSuccessful());
    EXPECT_THAT(client_->Run(), IsSuccessful());
  }

  ~PrivateKeyClientTest() { EXPECT_THAT(client_->Stop(), IsSuccessful()); }

  unique_ptr<MockPrivateKeyClientWithOverrides> client_;
};

TEST_F(PrivateKeyClientTest, ClientProviderCalled) {
  EXPECT_CALL(*client_->GetPrivateKeyClientProvider(), ListPrivateKeys);

  AsyncContext<ListPrivateKeysRequest, ListPrivateKeysResponse> context;
  client_->ListPrivateKeys(context);
}

TEST_F(PrivateKeyClientTest, ListPrivateKeysSyncSuccess) {
  EXPECT_CALL(*client_->GetPrivateKeyClientProvider(), ListPrivateKeys)
      .WillOnce([=](AsyncContext<ListPrivateKeysRequest,
                                 ListPrivateKeysResponse>& context) {
        context.response = make_shared<ListPrivateKeysResponse>();
        context.result = SuccessExecutionResult();
        context.Finish();
        return SuccessExecutionResult();
      });
  EXPECT_SUCCESS(
      client_->ListPrivateKeysSync(ListPrivateKeysRequest()).result());
}

TEST_F(PrivateKeyClientTest, ListActiveEncryptionKeysSyncSuccess) {
  EXPECT_CALL(*client_->GetPrivateKeyClientProvider(), ListActiveEncryptionKeys)
      .WillOnce([=](AsyncContext<ListActiveEncryptionKeysRequest,
                                 ListActiveEncryptionKeysResponse>& context) {
        context.response = make_shared<ListActiveEncryptionKeysResponse>();
        context.result = SuccessExecutionResult();
        context.Finish();
        return SuccessExecutionResult();
      });
  EXPECT_SUCCESS(
      client_->ListActiveEncryptionKeysSync(ListActiveEncryptionKeysRequest())
          .result());
}

TEST_F(PrivateKeyClientTest, ListPrivateKeysSyncFailureConvertToPublicError) {
  EXPECT_CALL(*client_->GetPrivateKeyClientProvider(), ListPrivateKeys)
      .WillOnce([=](AsyncContext<ListPrivateKeysRequest,
                                 ListPrivateKeysResponse>& context) {
        context.response = make_shared<ListPrivateKeysResponse>();
        // The error_code comes from PrivateKeyClientProvider
        context.result = FailureExecutionResult(
            SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_REQUEST);
        context.Finish();
        return SuccessExecutionResult();
      });
  EXPECT_EQ(client_->ListPrivateKeysSync(ListPrivateKeysRequest()).result(),
            FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT));
}

TEST_F(PrivateKeyClientTest,
       ListPrivateKeysSyncHttpClientFailureConvertToPublicError) {
  EXPECT_CALL(*client_->GetPrivateKeyClientProvider(), ListPrivateKeys)
      .WillOnce([=](AsyncContext<ListPrivateKeysRequest,
                                 ListPrivateKeysResponse>& context) {
        context.response = make_shared<ListPrivateKeysResponse>();
        // The error_code comes from HttpClient2
        context.result =
            FailureExecutionResult(SC_HTTP2_CLIENT_HTTP_STATUS_NOT_FOUND);
        context.Finish();
        return SuccessExecutionResult();
      });
  EXPECT_EQ(client_->ListPrivateKeysSync(ListPrivateKeysRequest()).result(),
            FailureExecutionResult(SC_CPIO_ENTITY_NOT_FOUND));
}

TEST_F(PrivateKeyClientTest, ListPrivateKeysFailedWithPublicError) {
  EXPECT_CALL(*client_->GetPrivateKeyClientProvider(), ListPrivateKeys)
      .WillOnce([=](AsyncContext<ListPrivateKeysRequest,
                                 ListPrivateKeysResponse>& context) {
        context.response = make_shared<ListPrivateKeysResponse>();
        // The error_code comes from HttpClient2
        context.result =
            FailureExecutionResult(SC_HTTP2_CLIENT_HTTP_STATUS_NOT_FOUND);
        context.Finish();
        return SuccessExecutionResult();
      });

  atomic<bool> finished = false;
  auto context = AsyncContext<ListPrivateKeysRequest, ListPrivateKeysResponse>(
      make_shared<ListPrivateKeysRequest>(), [&finished](auto& context) {
        EXPECT_THAT(context.result,
                    FailureExecutionResult(SC_CPIO_ENTITY_NOT_FOUND));
        finished = true;
      });
  client_->ListPrivateKeys(context);
  WaitUntil([&]() { return finished.load(); });
}

TEST_F(PrivateKeyClientTest, FailureToCreatePrivateKeyClientProvider) {
  auto failure = FailureExecutionResult(SC_UNKNOWN);
  client_->create_private_key_client_provider_result = failure;
  EXPECT_EQ(client_->Init(), FailureExecutionResult(SC_CPIO_UNKNOWN_ERROR));
}
}  // namespace google::scp::cpio::test
