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

#include "cpio/client_providers/kms_client_provider/src/gcp/gcp_kms_client_provider.h"

#include <gmock/gmock.h>

#include <chrono>
#include <memory>
#include <string>
#include <thread>
#include <vector>

#include "core/async_executor/src/async_executor.h"
#include "core/common/operation_dispatcher/src/error_codes.h"
#include "core/interface/async_context.h"
#include "core/test/utils/conditional_wait.h"
#include "core/test/utils/scp_test_base.h"
#include "core/utils/src/base64.h"
#include "cpio/client_providers/kms_client_provider/mock/gcp/mock_gcp_key_management_service_client.h"
#include "cpio/client_providers/kms_client_provider/src/gcp/error_codes.h"
#include "google/cloud/status.h"
#include "public/core/test/interface/execution_result_matchers.h"

using google::cloud::Status;
using google::cloud::StatusCode;
using google::scp::core::AsyncExecutor;
using google::scp::core::test::ScpTestBase;
using std::chrono::seconds;
using testing::Between;
using GcsDecryptRequest = google::cloud::kms::v1::DecryptRequest;
using GcsDecryptResponse = google::cloud::kms::v1::DecryptResponse;
using google::cloud::StatusOr;
using google::cmrt::sdk::kms_service::v1::DecryptRequest;
using google::cmrt::sdk::kms_service::v1::DecryptResponse;
using google::scp::core::AsyncContext;
using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::errors::SC_DISPATCHER_EXHAUSTED_RETRIES;
using google::scp::core::errors::
    SC_GCP_KMS_CLIENT_PROVIDER_BASE64_DECODING_FAILED;
using google::scp::core::errors::
    SC_GCP_KMS_CLIENT_PROVIDER_CIPHERTEXT_NOT_FOUND;
using google::scp::core::errors::SC_GCP_KMS_CLIENT_PROVIDER_DECRYPTION_FAILED;
using google::scp::core::errors::SC_GCP_KMS_CLIENT_PROVIDER_KEY_ARN_NOT_FOUND;
using google::scp::core::test::IsSuccessful;
using google::scp::core::test::ResultIs;
using google::scp::core::test::WaitUntil;
using google::scp::core::utils::Base64Encode;
using google::scp::cpio::client_providers::mock::
    MockGcpKeyManagementServiceClient;
using std::atomic;
using std::dynamic_pointer_cast;
using std::make_shared;
using std::make_unique;
using std::move;
using std::shared_ptr;
using std::string;
using std::thread;
using std::unique_ptr;
using std::vector;
using testing::Eq;
using testing::Exactly;
using testing::ExplainMatchResult;
using testing::Return;
using testing::Sequence;

static constexpr char kServiceAccount[] = "account";
static constexpr char kWipProvider[] = "wip";
static constexpr char kKeyArn[] = "keyArn";
static constexpr char kCiphertext[] = "ciphertext";
static constexpr char kPlaintext[] = "plaintext";

namespace google::scp::cpio::client_providers::test {
class MockGcpKmsFactory : public GcpKmsFactory {
 public:
  MOCK_METHOD(shared_ptr<GcpKeyManagementServiceClientInterface>,
              CreateGcpKeyManagementServiceClient,
              (const string&, const string&), (noexcept, override));
};

class GcpKmsClientProviderTest
    : public ScpTestBase,
      public ::testing::WithParamInterface<StatusCode> {
 protected:
  static constexpr auto kGcpKmsDecryptTotalRetries = 4;

  unique_ptr<GcpKmsClientProvider> InitGcpKmsClientProvider(
      const shared_ptr<KmsClientOptions>& options =
          make_shared<KmsClientOptions>()) {
    auto client = make_unique<GcpKmsClientProvider>(
        io_async_executor_, cpu_async_executor_, options,
        mock_gcp_kms_factory_);

    EXPECT_SUCCESS(client->Init());
    EXPECT_SUCCESS(client->Run());

    return move(client);
  }

  void SetUp() override {
    auto options = make_shared<KmsClientOptions>();
    options->enable_gcp_kms_client_cache = false;
    options->enable_gcp_kms_client_retries = true;
    options->gcp_kms_client_retry_total_retries = kGcpKmsDecryptTotalRetries;
    mock_gcp_kms_factory_ = make_shared<MockGcpKmsFactory>();
    EXPECT_SUCCESS(io_async_executor_->Init());
    EXPECT_SUCCESS(io_async_executor_->Run());
    EXPECT_SUCCESS(cpu_async_executor_->Init());
    EXPECT_SUCCESS(cpu_async_executor_->Run());

    client_ = InitGcpKmsClientProvider(options);

    mock_gcp_key_management_service_client_ =
        std::make_shared<MockGcpKeyManagementServiceClient>();
  }

  void TearDown() override {
    EXPECT_SUCCESS(client_->Stop());
    EXPECT_SUCCESS(io_async_executor_->Stop());
    EXPECT_SUCCESS(cpu_async_executor_->Stop());
  }

  unique_ptr<GcpKmsClientProvider> client_;

  shared_ptr<MockGcpKmsFactory> mock_gcp_kms_factory_;
  shared_ptr<MockGcpKeyManagementServiceClient>
      mock_gcp_key_management_service_client_;
  shared_ptr<AsyncExecutor> io_async_executor_ =
      make_shared<AsyncExecutor>(2, 100);
  shared_ptr<AsyncExecutor> cpu_async_executor_ =
      make_shared<AsyncExecutor>(2, 100);
};

TEST_F(GcpKmsClientProviderTest, NullKeyArn) {
  auto kms_decrpyt_request = make_shared<DecryptRequest>();
  kms_decrpyt_request->set_ciphertext(kCiphertext);

  atomic<bool> condition = false;
  AsyncContext<DecryptRequest, DecryptResponse> context(
      kms_decrpyt_request,
      [&](AsyncContext<DecryptRequest, DecryptResponse>& context) {
        EXPECT_THAT(context.result,
                    ResultIs(FailureExecutionResult(
                        SC_GCP_KMS_CLIENT_PROVIDER_KEY_ARN_NOT_FOUND)));
        condition = true;
      });

  client_->Decrypt(context);
  WaitUntil([&]() { return condition.load(); });
}

TEST_F(GcpKmsClientProviderTest, EmptyKeyArn) {
  auto kms_decrpyt_request = make_shared<DecryptRequest>();
  kms_decrpyt_request->set_key_resource_name("");
  kms_decrpyt_request->set_ciphertext(kCiphertext);

  atomic<bool> condition = false;
  AsyncContext<DecryptRequest, DecryptResponse> context(
      kms_decrpyt_request,
      [&](AsyncContext<DecryptRequest, DecryptResponse>& context) {
        EXPECT_THAT(context.result,
                    ResultIs(FailureExecutionResult(
                        SC_GCP_KMS_CLIENT_PROVIDER_KEY_ARN_NOT_FOUND)));
        condition = true;
      });

  client_->Decrypt(context);
  WaitUntil([&]() { return condition.load(); });
}

TEST_F(GcpKmsClientProviderTest, NullCiphertext) {
  auto kms_decrpyt_request = make_shared<DecryptRequest>();
  kms_decrpyt_request->set_key_resource_name(kKeyArn);
  atomic<bool> condition = false;
  AsyncContext<DecryptRequest, DecryptResponse> context(
      kms_decrpyt_request,
      [&](AsyncContext<DecryptRequest, DecryptResponse>& context) {
        EXPECT_THAT(context.result,
                    ResultIs(FailureExecutionResult(
                        SC_GCP_KMS_CLIENT_PROVIDER_CIPHERTEXT_NOT_FOUND)));
        condition = true;
      });

  client_->Decrypt(context);
  WaitUntil([&]() { return condition.load(); });
}

TEST_F(GcpKmsClientProviderTest, EmptyCiphertext) {
  auto kms_decrpyt_request = make_shared<DecryptRequest>();
  kms_decrpyt_request->set_key_resource_name(kKeyArn);
  kms_decrpyt_request->set_ciphertext("");
  atomic<bool> condition = false;
  AsyncContext<DecryptRequest, DecryptResponse> context(
      kms_decrpyt_request,
      [&](AsyncContext<DecryptRequest, DecryptResponse>& context) {
        EXPECT_THAT(context.result,
                    ResultIs(FailureExecutionResult(
                        SC_GCP_KMS_CLIENT_PROVIDER_CIPHERTEXT_NOT_FOUND)));
        condition = true;
      });

  client_->Decrypt(context);
  WaitUntil([&]() { return condition.load(); });
}

MATCHER_P(RequestMatches, req, "") {
  return ExplainMatchResult(Eq(req.name()), arg.name(), result_listener) &&
         ExplainMatchResult(Eq(req.ciphertext()), arg.ciphertext(),
                            result_listener) &&
         ExplainMatchResult(Eq(req.additional_authenticated_data()),
                            arg.additional_authenticated_data(),
                            result_listener);
}

TEST_F(GcpKmsClientProviderTest, FailedToDecode) {
  auto kms_decrpyt_request = make_shared<DecryptRequest>();
  kms_decrpyt_request->set_key_resource_name(kKeyArn);
  kms_decrpyt_request->set_ciphertext("abc");
  kms_decrpyt_request->set_account_identity(kServiceAccount);
  kms_decrpyt_request->set_gcp_wip_provider(kWipProvider);

  atomic<bool> condition = false;
  AsyncContext<DecryptRequest, DecryptResponse> context(
      kms_decrpyt_request,
      [&](AsyncContext<DecryptRequest, DecryptResponse>& context) {
        EXPECT_THAT(context.result,
                    ResultIs(FailureExecutionResult(
                        SC_GCP_KMS_CLIENT_PROVIDER_BASE64_DECODING_FAILED)));
        condition = true;
      });

  client_->Decrypt(context);
  WaitUntil([&]() { return condition.load(); });
}

void ExpectCallDecrypt(shared_ptr<MockGcpKeyManagementServiceClient>
                           mock_gcp_key_management_service_client,
                       int8_t call_decrypt_time) {
  GcsDecryptRequest decrypt_request;
  decrypt_request.set_name(kKeyArn);
  decrypt_request.set_ciphertext(kCiphertext);
  GcsDecryptResponse decrypt_response;
  decrypt_response.set_plaintext(kPlaintext);
  EXPECT_CALL(*mock_gcp_key_management_service_client,
              Decrypt(RequestMatches(decrypt_request)))
      .Times(call_decrypt_time)
      .WillRepeatedly(Return(decrypt_response));
}

void DecryptSuccessfully(GcpKmsClientProvider* client, const string& wip) {
  ASSERT_SUCCESS_AND_ASSIGN(string encoded_ciphertext,
                            Base64Encode(kCiphertext));
  auto kms_decrpyt_request = make_shared<DecryptRequest>();
  kms_decrpyt_request->set_key_resource_name(kKeyArn);
  kms_decrpyt_request->set_ciphertext(encoded_ciphertext);
  kms_decrpyt_request->set_account_identity(kServiceAccount);
  kms_decrpyt_request->set_gcp_wip_provider(wip);

  atomic<bool> condition = false;
  AsyncContext<DecryptRequest, DecryptResponse> context(
      kms_decrpyt_request,
      [&](AsyncContext<DecryptRequest, DecryptResponse>& context) {
        EXPECT_SUCCESS(context.result);
        EXPECT_EQ(context.response->plaintext(), kPlaintext);
        condition = true;
      });

  client->Decrypt(context);

  WaitUntil([&]() { return condition.load(); }, seconds(10));
}

TEST_F(GcpKmsClientProviderTest, SuccessToDecrypt) {
  ExpectCallDecrypt(mock_gcp_key_management_service_client_, 1);

  EXPECT_CALL(*mock_gcp_kms_factory_, CreateGcpKeyManagementServiceClient(
                                          kWipProvider, kServiceAccount))
      .WillOnce(Return(mock_gcp_key_management_service_client_));
  DecryptSuccessfully(client_.get(), kWipProvider);
}

TEST_F(GcpKmsClientProviderTest, MultiThreadSuccessToDecryptWithoutCache) {
  ExpectCallDecrypt(mock_gcp_key_management_service_client_, 10);
  // Call factory 10 times.
  EXPECT_CALL(*mock_gcp_kms_factory_, CreateGcpKeyManagementServiceClient(
                                          kWipProvider, kServiceAccount))
      .Times(10)
      .WillRepeatedly(Return(mock_gcp_key_management_service_client_));
  vector<thread> threads;
  for (auto i = 0; i < 10; ++i) {
    threads.push_back(
        thread([this]() { DecryptSuccessfully(client_.get(), kWipProvider); }));
  }

  for (auto& thread : threads) {
    thread.join();
  }
}

TEST_F(GcpKmsClientProviderTest, MultiThreadSuccessToDecryptWithCache) {
  auto options = make_shared<KmsClientOptions>();
  options->enable_gcp_kms_client_cache = true;
  auto client = make_unique<GcpKmsClientProvider>(
      io_async_executor_, cpu_async_executor_, options, mock_gcp_kms_factory_);
  EXPECT_SUCCESS(client->Init());
  EXPECT_SUCCESS(client->Run());

  ExpectCallDecrypt(mock_gcp_key_management_service_client_, 10);
  // Expect at least one factory call will be saved,
  // Only the first one will be used in cache.
  EXPECT_CALL(*mock_gcp_kms_factory_, CreateGcpKeyManagementServiceClient(
                                          kWipProvider, kServiceAccount))
      .Times(Between(1, 9))
      .WillRepeatedly(Return(mock_gcp_key_management_service_client_));
  vector<thread> threads;
  for (auto i = 0; i < 10; ++i) {
    threads.push_back(thread([this, &client]() {
      DecryptSuccessfully(client.get(), kWipProvider);
    }));
  }

  for (auto& thread : threads) {
    thread.join();
  }

  EXPECT_SUCCESS(client->Stop());
}

TEST_F(GcpKmsClientProviderTest, MultiThreadDifferentWipWithCache) {
  auto options = make_shared<KmsClientOptions>();
  options->enable_gcp_kms_client_cache = true;
  auto client = make_unique<GcpKmsClientProvider>(
      io_async_executor_, cpu_async_executor_, options, mock_gcp_kms_factory_);
  EXPECT_SUCCESS(client->Init());
  EXPECT_SUCCESS(client->Run());

  ExpectCallDecrypt(mock_gcp_key_management_service_client_, 10);
  EXPECT_CALL(*mock_gcp_kms_factory_, CreateGcpKeyManagementServiceClient(
                                          kWipProvider, kServiceAccount))
      .Times(Between(1, 4))
      .WillRepeatedly(Return(mock_gcp_key_management_service_client_));
  string wip_2 = "WIP2";
  EXPECT_CALL(*mock_gcp_kms_factory_,
              CreateGcpKeyManagementServiceClient(wip_2, kServiceAccount))
      .Times(Between(1, 4))
      .WillRepeatedly(Return(mock_gcp_key_management_service_client_));
  vector<thread> threads;
  for (auto i = 0; i < 10; ++i) {
    threads.push_back(thread([this, i, wip_2, &client]() {
      if (i > 4) {
        DecryptSuccessfully(client.get(), kWipProvider);
      } else {
        DecryptSuccessfully(client.get(), wip_2);
      }
    }));
  }

  for (auto& thread : threads) {
    thread.join();
  }

  EXPECT_SUCCESS(client->Stop());
}

/**
 * @brief Helper method to assert that GCP KMS client Decrypt is called
 * failure_return_times while failure_status_code is being returned.
 *
 */
void ExpectCallDecryptWithFailures(shared_ptr<MockGcpKeyManagementServiceClient>
                                       mock_gcp_key_management_service_client,
                                   int8_t failure_return_times,
                                   StatusCode failure_status_code,
                                   const Sequence& sequence = Sequence()) {
  GcsDecryptRequest decrypt_request;
  decrypt_request.set_name(kKeyArn);
  decrypt_request.set_ciphertext(kCiphertext);

  auto failure_status =
      StatusOr<GcsDecryptResponse>(Status(failure_status_code, "Failure"));

  EXPECT_CALL(*mock_gcp_key_management_service_client,
              Decrypt(RequestMatches(decrypt_request)))
      .Times(Exactly(failure_return_times))
      .InSequence(sequence)
      .WillRepeatedly(Return(failure_status));
}

/**
 * @brief Helper method to assert that GCP KMS client Decrypt is called
 * failure_return_times while failure_status_code is being returned, and then to
 * return a successful response after.
 *
 */
void ExpectCallDecryptWithFailuresAndEventualSuccess(
    shared_ptr<MockGcpKeyManagementServiceClient>
        mock_gcp_key_management_service_client,
    int8_t failure_return_times, StatusCode failure_status_code) {
  Sequence sequence;

  ExpectCallDecryptWithFailures(mock_gcp_key_management_service_client,
                                failure_return_times, failure_status_code,
                                sequence);

  GcsDecryptRequest decrypt_request;
  decrypt_request.set_name(kKeyArn);
  decrypt_request.set_ciphertext(kCiphertext);

  GcsDecryptResponse success_decrypt_response;
  success_decrypt_response.set_plaintext(kPlaintext);

  EXPECT_CALL(*mock_gcp_key_management_service_client,
              Decrypt(RequestMatches(decrypt_request)))
      .Times(Exactly(1))
      .InSequence(sequence)
      .WillOnce(Return(success_decrypt_response));
}

INSTANTIATE_TEST_SUITE_P(RetryErrorCodes, GcpKmsClientProviderTest,
                         testing::Values(StatusCode::kUnavailable,
                                         StatusCode::kUnknown,
                                         StatusCode::kInternal));

TEST_P(GcpKmsClientProviderTest, ShouldRetryKmsDecryptForRetriableStatusCode) {
  auto failure_times = 2;
  EXPECT_CALL(*mock_gcp_kms_factory_, CreateGcpKeyManagementServiceClient(
                                          kWipProvider, kServiceAccount))
      .Times(Exactly(failure_times +
                     1))  // Two failures plus the one that succeeds
      .WillRepeatedly(Return(mock_gcp_key_management_service_client_));
  ExpectCallDecryptWithFailuresAndEventualSuccess(
      mock_gcp_key_management_service_client_, failure_times, GetParam());

  DecryptSuccessfully(client_.get(), kWipProvider);
}

void DecryptFailure(GcpKmsClientProvider* client, const string& wip,
                    google::scp::core::StatusCode failure_code) {
  ASSERT_SUCCESS_AND_ASSIGN(string encoded_ciphertext,
                            Base64Encode(kCiphertext));
  auto kms_decrpyt_request = make_shared<DecryptRequest>();
  kms_decrpyt_request->set_key_resource_name(kKeyArn);
  kms_decrpyt_request->set_ciphertext(encoded_ciphertext);
  kms_decrpyt_request->set_account_identity(kServiceAccount);
  kms_decrpyt_request->set_gcp_wip_provider(wip);

  atomic<bool> condition = false;
  AsyncContext<DecryptRequest, DecryptResponse> context(
      kms_decrpyt_request,
      [&](AsyncContext<DecryptRequest, DecryptResponse>& context) {
        EXPECT_THAT(context.result,
                    ResultIs(FailureExecutionResult(failure_code)));
        condition = true;
      });

  client->Decrypt(context);

  WaitUntil([&]() { return condition.load(); }, seconds(10));
}

TEST_F(GcpKmsClientProviderTest,
       ShouldExhaustKmsDecryptRetriesAndEventuallyFailIfUnavailable) {
  auto failure_times = kGcpKmsDecryptTotalRetries;
  EXPECT_CALL(*mock_gcp_kms_factory_, CreateGcpKeyManagementServiceClient(
                                          kWipProvider, kServiceAccount))
      .Times(failure_times)
      .WillRepeatedly(Return(mock_gcp_key_management_service_client_));
  ExpectCallDecryptWithFailures(mock_gcp_key_management_service_client_,
                                failure_times, StatusCode::kUnavailable);

  DecryptFailure(client_.get(), kWipProvider, SC_DISPATCHER_EXHAUSTED_RETRIES);
}

TEST_F(GcpKmsClientProviderTest, ShouldNotRetryKmsDecryptUponFailure) {
  EXPECT_CALL(*mock_gcp_kms_factory_, CreateGcpKeyManagementServiceClient(
                                          kWipProvider, kServiceAccount))
      .Times(Exactly(1))
      .WillOnce(Return(mock_gcp_key_management_service_client_));
  ExpectCallDecryptWithFailures(
      mock_gcp_key_management_service_client_,
      /* failure_return_times */ 1,
      StatusCode::kPermissionDenied);  // Non-retryable code

  DecryptFailure(client_.get(), kWipProvider,
                 SC_GCP_KMS_CLIENT_PROVIDER_DECRYPTION_FAILED);
}

TEST_F(GcpKmsClientProviderTest, ShouldNotRetryKmsDecryptIfRetriesDisabled) {
  auto options = make_shared<KmsClientOptions>();
  options->enable_gcp_kms_client_retries = false;
  auto client = InitGcpKmsClientProvider(options);

  EXPECT_CALL(*mock_gcp_kms_factory_, CreateGcpKeyManagementServiceClient(
                                          kWipProvider, kServiceAccount))
      .Times(Exactly(1))
      .WillOnce(Return(mock_gcp_key_management_service_client_));
  ExpectCallDecryptWithFailures(
      mock_gcp_key_management_service_client_, /* failure_return_times */ 1,
      StatusCode::kUnavailable);  // Generally retryable code, but will fail
                                  // right away since retries are disabled

  DecryptFailure(client.get(), kWipProvider,
                 SC_GCP_KMS_CLIENT_PROVIDER_DECRYPTION_FAILED);

  EXPECT_SUCCESS(client->Stop());
}
}  // namespace google::scp::cpio::client_providers::test
