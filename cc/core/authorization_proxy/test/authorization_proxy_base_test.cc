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

#include "core/authorization_proxy/src/authorization_proxy_base.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>

#include "core/async_executor/src/async_executor.h"
#include "core/authorization_proxy/src/error_codes.h"
#include "core/interface/async_context.h"
#include "core/test/utils/conditional_wait.h"
#include "core/test/utils/scp_test_base.h"
#include "public/core/test/interface/execution_result_matchers.h"

using google::scp::core::AsyncExecutor;
using google::scp::core::ExecutionResult;
using google::scp::core::test::ScpTestBase;
using std::make_shared;
using std::shared_ptr;
using ::testing::_;
using ::testing::DoAll;
using ::testing::Return;
using ::testing::SetArgPointee;

namespace google::scp::core::test {

class AuthorizeInternalMock {
 public:
  MOCK_METHOD(
      ExecutionResult, AuthorizeInternal,
      ((AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&)),
      (noexcept));
};

class AuthorizationProxyForTest : public AuthorizationProxyBase {
 public:
  AuthorizationProxyForTest(
      const std::shared_ptr<AsyncExecutorInterface>& async_executor,
      AuthorizeInternalMock& authorize_internal_mock)
      : AuthorizationProxyBase(async_executor),
        authorize_internal_mock_(authorize_internal_mock) {}

  ExecutionResult AuthorizeInternal(
      AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>&
          authorization_context) noexcept override {
    return authorize_internal_mock_.AuthorizeInternal(authorization_context);
  }

 private:
  AuthorizeInternalMock& authorize_internal_mock_;
};

class AuthorizationProxyBaseTest : public ScpTestBase {
 protected:
  AuthorizationProxyBaseTest()
      : async_executor_(make_shared<AsyncExecutor>(
            4, 1000, true /* drop tasks on stop */)) {
    EXPECT_SUCCESS(async_executor_->Init());
    EXPECT_SUCCESS(async_executor_->Run());

    authorization_metadata_.claimed_identity = "google.com";
    authorization_metadata_.authorization_token = "kjgasuif8i2qr1kj215125";

    authorized_metadata_.authorized_domain =
        std::make_shared<std::string>("google.com");
  }

  void TearDown() override { EXPECT_SUCCESS(async_executor_->Stop()); }

  std::shared_ptr<AsyncExecutorInterface> async_executor_;
  AuthorizationMetadata authorization_metadata_;
  AuthorizedMetadata authorized_metadata_;
  AuthorizeInternalMock authorize_internal_mock_;
};

TEST_F(AuthorizationProxyBaseTest, AuthorizeWithInvalidAuthorizationMetadata) {
  AuthorizationProxyForTest proxy(async_executor_, authorize_internal_mock_);
  EXPECT_SUCCESS(proxy.Init());
  EXPECT_SUCCESS(proxy.Run());

  AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
      empty_auth_request;
  empty_auth_request.request = make_shared<AuthorizationProxyRequest>();
  EXPECT_THAT(proxy.Authorize(empty_auth_request),
              ResultIs(FailureExecutionResult(
                  errors::SC_AUTHORIZATION_PROXY_BAD_REQUEST)));

  AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
      request_missing_token;
  request_missing_token.request = make_shared<AuthorizationProxyRequest>();
  request_missing_token.request->authorization_metadata.claimed_identity =
      "claimed_id";
  EXPECT_THAT(proxy.Authorize(request_missing_token),
              ResultIs(FailureExecutionResult(
                  errors::SC_AUTHORIZATION_PROXY_BAD_REQUEST)));

  AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
      request_missing_identity;
  request_missing_identity.request = make_shared<AuthorizationProxyRequest>();
  request_missing_identity.request->authorization_metadata.authorization_token =
      "auth_token";
  EXPECT_THAT(proxy.Authorize(request_missing_identity),
              ResultIs(FailureExecutionResult(
                  errors::SC_AUTHORIZATION_PROXY_BAD_REQUEST)));
}

TEST_F(AuthorizationProxyBaseTest,
       AuthorizeReturnsExecutionResultFromAuthInternalCallIfError) {
  AuthorizationProxyForTest proxy(async_executor_, authorize_internal_mock_);
  EXPECT_SUCCESS(proxy.Init());
  EXPECT_SUCCESS(proxy.Run());

  EXPECT_CALL(authorize_internal_mock_, AuthorizeInternal(_))
      .WillOnce(Return(FailureExecutionResult(123321)));

  AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
      authorization_request;
  authorization_request.request = make_shared<AuthorizationProxyRequest>();
  authorization_request.request->authorization_metadata =
      authorization_metadata_;

  EXPECT_THAT(proxy.Authorize(authorization_request),
              ResultIs(FailureExecutionResult(123321)));
}

TEST_F(AuthorizationProxyBaseTest,
       AuthorizeReturnsExecutionResultFromAuthorizeInternalAsyncCall) {
  AuthorizationProxyForTest proxy(async_executor_, authorize_internal_mock_);
  EXPECT_SUCCESS(proxy.Init());
  EXPECT_SUCCESS(proxy.Run());

  EXPECT_CALL(authorize_internal_mock_, AuthorizeInternal)
      .WillOnce([](AsyncContext<AuthorizationProxyRequest,
                                AuthorizationProxyResponse>& context) {
        if (context.request == nullptr) {
          ADD_FAILURE();
        }
        context.result = FailureExecutionResult(456654);
        context.Finish();
        return SuccessExecutionResult();
      });

  std::atomic<bool> request_finished(false);
  AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
      authorization_request;
  authorization_request.request = make_shared<AuthorizationProxyRequest>();
  authorization_request.request->authorization_metadata =
      authorization_metadata_;
  authorization_request.callback = [&](auto context) {
    request_finished = true;
    EXPECT_THAT(context.result, ResultIs(FailureExecutionResult(456654)));
    return SuccessExecutionResult();
  };
  EXPECT_SUCCESS(proxy.Authorize(authorization_request));
  WaitUntil([&]() { return request_finished.load(); });
}

TEST_F(AuthorizationProxyBaseTest, AuthorizeReturnsRetryIfRequestInProgress) {
  AuthorizationProxyForTest proxy(async_executor_, authorize_internal_mock_);
  EXPECT_SUCCESS(proxy.Init());
  EXPECT_SUCCESS(proxy.Run());

  // This call returns a success, but the async call has not come back yet
  EXPECT_CALL(authorize_internal_mock_, AuthorizeInternal)
      .WillOnce(Return(SuccessExecutionResult()));

  AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
      authorization_request1;
  authorization_request1.request = make_shared<AuthorizationProxyRequest>();
  authorization_request1.request->authorization_metadata =
      authorization_metadata_;

  // This authorize call succeeds
  EXPECT_SUCCESS(proxy.Authorize(authorization_request1));

  // Request attempt 2.
  AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
      authorization_request2;
  authorization_request2.request = make_shared<AuthorizationProxyRequest>();
  authorization_request2.request->authorization_metadata =
      authorization_metadata_;

  // Since the async call to AuthorizeInternal hasn't come back, we expect a
  // retry response
  EXPECT_THAT(proxy.Authorize(authorization_request2),
              ResultIs(RetryExecutionResult(
                  errors::SC_AUTHORIZATION_PROXY_AUTH_REQUEST_INPROGRESS)));

  // Request attempt 3.
  AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
      authorization_request3;
  authorization_request3.request = make_shared<AuthorizationProxyRequest>();
  authorization_request3.request->authorization_metadata =
      authorization_metadata_;

  // Since the async call to AuthorizeInternal hasn't come back, we expect a
  // retry response
  EXPECT_THAT(proxy.Authorize(authorization_request3),
              ResultIs(RetryExecutionResult(
                  errors::SC_AUTHORIZATION_PROXY_AUTH_REQUEST_INPROGRESS)));
}

TEST_F(AuthorizationProxyBaseTest,
       AuthorizeReturnsSuccessAfterAuthInternalCompletes) {
  AuthorizationProxyForTest proxy(async_executor_, authorize_internal_mock_);
  EXPECT_SUCCESS(proxy.Init());
  EXPECT_SUCCESS(proxy.Run());

  EXPECT_CALL(authorize_internal_mock_, AuthorizeInternal)
      .WillOnce([this](AsyncContext<AuthorizationProxyRequest,
                                    AuthorizationProxyResponse>& context) {
        if (context.request == nullptr) {
          ADD_FAILURE();
        }
        // Verify the request's authorization context is supplied to
        // AuthorizeInternal correctly.
        EXPECT_EQ(authorization_metadata_.authorization_token,
                  context.request->authorization_metadata.authorization_token);
        EXPECT_EQ(authorization_metadata_.claimed_identity,
                  context.request->authorization_metadata.claimed_identity);

        // Set the response
        context.response = make_shared<AuthorizationProxyResponse>();
        context.response->authorized_metadata = authorized_metadata_;

        context.result = SuccessExecutionResult();
        context.Finish();
        return SuccessExecutionResult();
      });

  // First request, calls AuthorizeInternal and caches response.
  {
    std::atomic<bool> request_finished(false);
    AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
        authorization_request;
    authorization_request.request = make_shared<AuthorizationProxyRequest>();
    authorization_request.request->authorization_metadata =
        authorization_metadata_;
    authorization_request.callback = [&](auto context) {
      EXPECT_SUCCESS(context.result);
      EXPECT_EQ(*context.response->authorized_metadata.authorized_domain,
                *authorized_metadata_.authorized_domain);
      request_finished = true;
      return SuccessExecutionResult();
    };
    EXPECT_SUCCESS(proxy.Authorize(authorization_request));
    WaitUntil([&]() { return request_finished.load(); });
  }

  // We don't expect AuthorizeInternal to be called again.
  EXPECT_CALL(authorize_internal_mock_, AuthorizeInternal(_)).Times(0);

  // Try again, doesn't call AuthorizeInternal, but simply returns the cached
  // response.
  {
    std::atomic<bool> request_finished(false);
    AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
        authorization_request;
    authorization_request.request = make_shared<AuthorizationProxyRequest>();
    authorization_request.request->authorization_metadata =
        authorization_metadata_;
    authorization_request.callback = [&](auto context) {
      EXPECT_SUCCESS(context.result);
      EXPECT_EQ(*context.response->authorized_metadata.authorized_domain,
                *authorized_metadata_.authorized_domain);
      request_finished = true;
      return SuccessExecutionResult();
    };
    EXPECT_SUCCESS(proxy.Authorize(authorization_request));
    WaitUntil([&]() { return request_finished.load(); });
  }
}

TEST_F(AuthorizationProxyBaseTest,
       AuthorizeInternalReturnsFailureReponseDoesntCacheReponse) {
  AuthorizationProxyForTest proxy(async_executor_, authorize_internal_mock_);
  EXPECT_SUCCESS(proxy.Init());
  EXPECT_SUCCESS(proxy.Run());

  EXPECT_CALL(authorize_internal_mock_, AuthorizeInternal)
      .Times(2)
      // Fail the first time
      .WillOnce([](AsyncContext<AuthorizationProxyRequest,
                                AuthorizationProxyResponse>& context) {
        if (context.request == nullptr) {
          ADD_FAILURE();
        }
        context.result = FailureExecutionResult(9999);
        context.Finish();
        return SuccessExecutionResult();
      })
      // Succeed the second time
      .WillOnce([this](AsyncContext<AuthorizationProxyRequest,
                                    AuthorizationProxyResponse>& context) {
        if (context.request == nullptr) {
          ADD_FAILURE();
        }
        context.response = make_shared<AuthorizationProxyResponse>();
        context.response->authorized_metadata = authorized_metadata_;
        context.result = SuccessExecutionResult();
        context.Finish();
        return SuccessExecutionResult();
      });

  // Request 1
  {
    std::atomic<bool> request_finished(false);
    AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
        authorization_request;
    authorization_request.request = make_shared<AuthorizationProxyRequest>();
    authorization_request.request->authorization_metadata =
        authorization_metadata_;
    authorization_request.callback = [&](auto context) {
      EXPECT_THAT(context.result, ResultIs(FailureExecutionResult(9999)));
      request_finished = true;
      return SuccessExecutionResult();
    };
    EXPECT_SUCCESS(proxy.Authorize(authorization_request));
    WaitUntil([&]() { return request_finished.load(); });
  }

  // Request 2
  {
    std::atomic<bool> request_finished(false);
    AsyncContext<AuthorizationProxyRequest, AuthorizationProxyResponse>
        authorization_request;
    authorization_request.request = make_shared<AuthorizationProxyRequest>();
    authorization_request.request->authorization_metadata =
        authorization_metadata_;
    authorization_request.callback = [&](auto context) {
      EXPECT_SUCCESS(context.result);
      EXPECT_EQ(*context.response->authorized_metadata.authorized_domain,
                *authorized_metadata_.authorized_domain);
      request_finished = true;
      return SuccessExecutionResult();
    };
    EXPECT_SUCCESS(proxy.Authorize(authorization_request));
    WaitUntil([&]() { return request_finished.load(); });
  }
}
}  // namespace google::scp::core::test
