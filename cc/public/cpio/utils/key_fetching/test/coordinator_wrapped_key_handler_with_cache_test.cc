// Copyright 2026 Google LLC
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

#include "public/cpio/utils/key_fetching/src/coordinator_wrapped_key_handler_with_cache.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <atomic>
#include <chrono>
#include <memory>
#include <string>
#include <thread>
#include <vector>

#include "cc/core/async_executor/src/async_executor.h"
#include "cc/core/test/utils/proto_test_utils.h"
#include "cc/core/test/utils/scp_test_base.h"
#include "cpio/common/src/common_error_codes.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/interface/error_codes.h"
#include "public/cpio/mock/crypto_client/mock_crypto_client.h"
#include "public/cpio/utils/dual_writing_metric_client/mock/dual_writing_metric_client_mock.h"
#include "public/cpio/utils/key_fetching/interface/key_fetcher_with_cache_interface.h"
#include "public/cpio/utils/key_fetching/src/error_codes.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"
#include "public/cpio/utils/key_fetching/test/key_fetching_metric_matcher.h"

using google::cmrt::sdk::crypto_service::v1::HpkeDecryptRequest;
using google::cmrt::sdk::crypto_service::v1::HpkeDecryptResponse;
using google::cmrt::sdk::v1::CloudWrappedKey;
using google::cmrt::sdk::v1::CoordinatorWrappedKey;
using google::scp::core::AsyncExecutor;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResult;
using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::errors::SC_CPIO_ENTITY_NOT_FOUND;
using google::scp::core::errors::SC_CPIO_INVALID_ARGUMENT;
using google::scp::core::errors::SC_CPIO_KEY_FETCHER_FETCHING_TIMEOUT;
using google::scp::core::errors::SC_CPIO_KEY_NOT_FOUND;
using google::scp::core::errors::SC_CPIO_SERVICE_UNAVAILABLE;
using google::scp::core::test::EqualsProto;
using google::scp::core::test::IsSuccessfulAndHolds;
using google::scp::core::test::ResultIs;
using google::scp::core::test::ScpTestBase;
using google::scp::cpio::DualWritingMetricClientMock;
using google::scp::cpio::ExpectOtelKeyCacheStatusMetricPush;
using google::scp::cpio::ExpectOtelKeyFetchingErrorMetricPush;
using google::scp::cpio::ExpectOtelKeyFetchingLatencyMetricPush;
using google::scp::cpio::ExpectOtelKeyFetchingRequestMetricPush;
using google::scp::cpio::Key;
using google::scp::cpio::KeyCacheStatus;
using google::scp::cpio::KeyFetcherWithCacheInterface;
using google::scp::cpio::KeyFetchingType;
using google::scp::cpio::KeyType;
using google::scp::cpio::MockCryptoClient;
using google::scp::cpio::WrappedKeyHandlerOptions;
using std::make_shared;
using std::shared_ptr;
using std::string;
using std::chrono::seconds;
using std::this_thread::sleep_for;
using ::testing::Eq;
using testing::NiceMock;
using testing::Return;

namespace google::scp::cpio {

namespace {

constexpr absl::string_view kKeyId = "coordinator_key_id";
constexpr absl::string_view kEncryptedDek = "encrypted_dek";
constexpr absl::string_view kPrivateKey = "tink_private_key_binary";
constexpr absl::string_view kDecryptedDek = "decrypted_dek";

class MockKeyFetcherWithCache : public KeyFetcherWithCacheInterface {
 public:
  MockKeyFetcherWithCache() {
    ON_CALL(*this, Init).WillByDefault(Return(SuccessExecutionResult()));
    ON_CALL(*this, Run).WillByDefault(Return(SuccessExecutionResult()));
    ON_CALL(*this, Stop).WillByDefault(Return(SuccessExecutionResult()));
  }

  MOCK_METHOD(ExecutionResult, Init, (), (noexcept, override));
  MOCK_METHOD(ExecutionResult, Run, (), (noexcept, override));
  MOCK_METHOD(ExecutionResult, Stop, (), (noexcept, override));

  MOCK_METHOD(ExecutionResultOr<Key>, GetKey, (const std::string&),
              (noexcept, override));

  MOCK_METHOD(ExecutionResultOr<std::vector<Key>>, GetValidKeys,
              (core::Timestamp), (noexcept, override));

  MOCK_METHOD(ExecutionResultOr<bool>, ValidateKey, (const std::string&),
              (noexcept, override));
};

CloudWrappedKey BuildCloudWrappedKey(
    const CoordinatorWrappedKey& coordinator_wrapped_key) {
  CloudWrappedKey cloud_wrapped_key;
  *cloud_wrapped_key.mutable_coordinator_wrapped_key() =
      coordinator_wrapped_key;
  return cloud_wrapped_key;
}

WrappedKeyHandlerOptions CreateWrappedKeyHandlerOptions(
    bool enable_decryption_lock = true, bool enable_cache = true) {
  WrappedKeyHandlerOptions options;
  options.enable_decryption_lock = enable_decryption_lock;
  options.enable_cache = enable_cache;
  options.key_cache_lifetime = std::chrono::seconds(1800);
  options.key_failure_cache_lifetime = std::chrono::seconds(300);
  options.key_decryption_waiting_timeout = std::chrono::milliseconds(200);
  return options;
}

}  // namespace

class CoordinatorWrappedKeyHandlerWithCacheTest : public ScpTestBase {
 public:
  CoordinatorWrappedKeyHandlerWithCacheTest()
      : async_executor_(make_shared<AsyncExecutor>(2, 10)),
        wrapped_key_handler_(
            async_executor_, mock_crypto_client_, mock_key_fetcher_with_cache_,
            CreateWrappedKeyHandlerOptions(), mock_metric_client_) {
    EXPECT_SUCCESS(async_executor_->Init());
    EXPECT_SUCCESS(async_executor_->Run());
    EXPECT_SUCCESS(wrapped_key_handler_.Init());
    EXPECT_SUCCESS(wrapped_key_handler_.Run());
  }

 protected:
  void SetUp() override {
    coordinator_wrapped_key_.set_key_id(string(kKeyId));
    coordinator_wrapped_key_.set_encrypted_dek(string(kEncryptedDek));

    coordinator_key_.key_id = string(kKeyId);
    coordinator_key_.private_key = string(kPrivateKey);

    expected_decrypt_request_.set_tink_key_binary(string(kPrivateKey));
    expected_decrypt_request_.mutable_encrypted_data()->set_ciphertext(
        string(kEncryptedDek));
  }

  void TearDown() override {
    EXPECT_SUCCESS(wrapped_key_handler_.Stop());
    EXPECT_SUCCESS(async_executor_->Stop());
  }

  void ExpectOtelEncryptionKeyFetchingRequestMetricPush(int call_count) {
    ExpectOtelKeyFetchingRequestMetricPush(
        mock_metric_client_, call_count, KeyType::kCoordinatorWrappedKey,
        KeyFetchingType::kOnDemand, kDummyLabelValue);
  }

  void ExpectOtelEncryptionKeyFetchingErrorMetricPush(
      int call_count,
      absl::string_view error_code = KeyFetchingErrorType::kGenericError) {
    ExpectOtelKeyFetchingErrorMetricPush(
        mock_metric_client_, call_count, KeyType::kCoordinatorWrappedKey,
        KeyFetchingType::kOnDemand, kDummyLabelValue, error_code);
  }

  void ExpectOtelEncryptionKeyFetchingLatencyMetricPush(int call_count) {
    ExpectOtelKeyFetchingLatencyMetricPush(
        mock_metric_client_, call_count, KeyType::kCoordinatorWrappedKey,
        KeyFetchingType::kOnDemand, kDummyLabelValue);
  }

  void ExpectOtelEncryptionKeyCacheStatusMetricPush(
      int call_count,
      absl::string_view key_cache_status = KeyCacheStatus::kValidKeyCacheHit) {
    ExpectOtelKeyCacheStatusMetricPush(mock_metric_client_, call_count,
                                       KeyType::kCoordinatorWrappedKey,
                                       kDummyLabelValue, key_cache_status);
  }

  std::shared_ptr<AsyncExecutorInterface> async_executor_;
  NiceMock<DualWritingMetricClientMock> mock_metric_client_;
  MockCryptoClient mock_crypto_client_;
  MockKeyFetcherWithCache mock_key_fetcher_with_cache_;
  CoordinatorWrappedKeyHandlerWithCache wrapped_key_handler_;
  CoordinatorWrappedKey coordinator_wrapped_key_;
  Key coordinator_key_;
  HpkeDecryptRequest expected_decrypt_request_;
};

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GetKeyFailsIfCoordinatorWrappedKeyIsMissing) {
  CloudWrappedKey cloud_wrapped_key;

  auto result = wrapped_key_handler_.GetKey(cloud_wrapped_key);

  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest, GetKeyFailsIfFieldsAreEmpty) {
  coordinator_wrapped_key_.clear_key_id();
  EXPECT_THAT(wrapped_key_handler_.GetKey(
                  BuildCloudWrappedKey(coordinator_wrapped_key_)),
              ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));

  coordinator_wrapped_key_.set_key_id(string(kKeyId));
  coordinator_wrapped_key_.clear_encrypted_dek();
  EXPECT_THAT(wrapped_key_handler_.GetKey(
                  BuildCloudWrappedKey(coordinator_wrapped_key_)),
              ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       ValidateKeyFailsIfCoordinatorWrappedKeyOrFieldsAreEmpty) {
  CloudWrappedKey empty_cloud_wrapped_key;
  EXPECT_THAT(wrapped_key_handler_.ValidateKey(empty_cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));

  coordinator_wrapped_key_.clear_key_id();
  EXPECT_THAT(wrapped_key_handler_.ValidateKey(
                  BuildCloudWrappedKey(coordinator_wrapped_key_)),
              ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));

  coordinator_wrapped_key_.set_key_id(string(kKeyId));
  coordinator_wrapped_key_.clear_encrypted_dek();
  EXPECT_THAT(wrapped_key_handler_.ValidateKey(
                  BuildCloudWrappedKey(coordinator_wrapped_key_)),
              ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       ValidateKeyDelegatesToKeyFetcherWithCache) {
  EXPECT_CALL(mock_key_fetcher_with_cache_, ValidateKey(string(kKeyId)))
      .WillOnce(Return(true))
      .WillOnce(Return(false))
      .WillOnce(Return(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  EXPECT_THAT(wrapped_key_handler_.ValidateKey(cloud_wrapped_key),
              IsSuccessfulAndHolds(true));
  EXPECT_THAT(wrapped_key_handler_.ValidateKey(cloud_wrapped_key),
              IsSuccessfulAndHolds(false));
  EXPECT_THAT(wrapped_key_handler_.ValidateKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       DecryptDekMapsFieldsCorrectly) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(0);

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  HpkeDecryptResponse response;
  response.set_payload(string(kDecryptedDek));
  EXPECT_CALL(mock_crypto_client_,
              HpkeDecryptSync(EqualsProto(expected_decrypt_request_)))
      .WillOnce(Return(response));

  auto decrypted_dek_or = wrapped_key_handler_.GetKey(
      BuildCloudWrappedKey(coordinator_wrapped_key_));
  EXPECT_THAT(decrypted_dek_or, IsSuccessfulAndHolds(string(kDecryptedDek)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GettingDecryptedDekByWrappedKeySuccessful) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(0);

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  HpkeDecryptResponse response;
  response.set_payload(string(kDecryptedDek));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync).WillOnce(Return(response));

  auto decrypted_dek_or = wrapped_key_handler_.GetKey(
      BuildCloudWrappedKey(coordinator_wrapped_key_));

  EXPECT_THAT(*decrypted_dek_or, Eq(kDecryptedDek));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GettingDecryptedDekByWrappedKeySuccessfulWithMutexLockEnabled) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(0);

  CoordinatorWrappedKeyHandlerWithCache key_handler_with_mutex_lock_enabled(
      async_executor_, mock_crypto_client_, mock_key_fetcher_with_cache_,
      CreateWrappedKeyHandlerOptions(true), mock_metric_client_);
  EXPECT_SUCCESS(key_handler_with_mutex_lock_enabled.Init());
  EXPECT_SUCCESS(key_handler_with_mutex_lock_enabled.Run());

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  HpkeDecryptResponse response;
  response.set_payload(string(kDecryptedDek));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync).WillOnce(Return(response));

  auto decrypted_dek_or = key_handler_with_mutex_lock_enabled.GetKey(
      BuildCloudWrappedKey(coordinator_wrapped_key_));

  EXPECT_THAT(*decrypted_dek_or, Eq(kDecryptedDek));
  EXPECT_SUCCESS(key_handler_with_mutex_lock_enabled.Stop());
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GettingDecryptedDekByWrappedKeySuccessfulWithMutexLockDisabled) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(0);

  CoordinatorWrappedKeyHandlerWithCache key_handler_with_mutex_lock_disabled(
      async_executor_, mock_crypto_client_, mock_key_fetcher_with_cache_,
      CreateWrappedKeyHandlerOptions(false), mock_metric_client_);
  EXPECT_SUCCESS(key_handler_with_mutex_lock_disabled.Init());
  EXPECT_SUCCESS(key_handler_with_mutex_lock_disabled.Run());

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  HpkeDecryptResponse response;
  response.set_payload(string(kDecryptedDek));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync).WillOnce(Return(response));

  auto decrypted_dek_or = key_handler_with_mutex_lock_disabled.GetKey(
      BuildCloudWrappedKey(coordinator_wrapped_key_));

  EXPECT_THAT(*decrypted_dek_or, Eq(kDecryptedDek));
  EXPECT_SUCCESS(key_handler_with_mutex_lock_disabled.Stop());
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GettingDecryptedDekByWrappedKeyWithMultiThreadsSuccessful) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(-1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(-1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      -1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      -1, KeyCacheStatus::kValidKeyCacheHit);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(0);

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  HpkeDecryptResponse response;
  response.set_payload(string(kDecryptedDek));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync).WillOnce(Return(response));

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  auto decrypted_dek_or = wrapped_key_handler_.GetKey(cloud_wrapped_key);

  EXPECT_THAT(*decrypted_dek_or, Eq(kDecryptedDek));

  constexpr auto kNumThreads = 1000;
  std::vector<std::thread> work_threads;
  work_threads.reserve(kNumThreads);
  for (int i = 0; i < kNumThreads; i++) {
    work_threads.emplace_back([&] {
      auto decrypted_dek2_or = wrapped_key_handler_.GetKey(cloud_wrapped_key);

      EXPECT_THAT(*decrypted_dek2_or, Eq(kDecryptedDek));
    });
  }

  for (auto& t : work_threads) {
    if (t.joinable()) {
      t.join();
    }
  }
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GetKeyFailedWhenKeyFetcherFailsWithNonRetryableError) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(0);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kInvalidKeyCacheHit);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(
      1, KeyFetchingErrorType::kInvalidKeyId);

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync).Times(0);

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));

  // Subsequent call hits the failure cache without calling key fetcher again.
  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GetKeyFailedWhenKeyFetcherReturnsEmptyPrivateKey) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(0);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kInvalidKeyCacheHit);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(
      1, KeyFetchingErrorType::kInvalidKeyId);

  Key empty_private_key;
  empty_private_key.key_id = string(kKeyId);
  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(empty_private_key));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync).Times(0);

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));
  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GetKeyRetriesWhenKeyFetcherFailsWithRetryableError) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(2);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      2, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(
      1, KeyFetchingErrorType::kGenericError);

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(FailureExecutionResult(SC_CPIO_SERVICE_UNAVAILABLE)))
      .WillOnce(Return(coordinator_key_));
  HpkeDecryptResponse response;
  response.set_payload(string(kDecryptedDek));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync).WillOnce(Return(response));

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_SERVICE_UNAVAILABLE)));
  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              IsSuccessfulAndHolds(string(kDecryptedDek)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GetKeyFailedWithNonRetryableHpkeDecryptionError) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kInvalidKeyCacheHit);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(
      1, KeyFetchingErrorType::kInvalidKeyId);

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync)
      .WillOnce(Return(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));

  // Subsequent call hits the failure cache without calling HpkeDecryptSync.
  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GetKeyFailedWithNonRetryableErrorWithMutexLockDisabled) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kInvalidKeyCacheHit);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(
      1, KeyFetchingErrorType::kInvalidKeyId);

  CoordinatorWrappedKeyHandlerWithCache key_handler_with_mutex_lock_disabled(
      async_executor_, mock_crypto_client_, mock_key_fetcher_with_cache_,
      CreateWrappedKeyHandlerOptions(false), mock_metric_client_);
  EXPECT_SUCCESS(key_handler_with_mutex_lock_disabled.Init());
  EXPECT_SUCCESS(key_handler_with_mutex_lock_disabled.Run());

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync)
      .WillOnce(Return(FailureExecutionResult(SC_CPIO_ENTITY_NOT_FOUND)));

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  EXPECT_THAT(key_handler_with_mutex_lock_disabled.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_ENTITY_NOT_FOUND)));

  EXPECT_THAT(key_handler_with_mutex_lock_disabled.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_ENTITY_NOT_FOUND)));
  EXPECT_SUCCESS(key_handler_with_mutex_lock_disabled.Stop());
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       InvalidKeyWithEmptyResponseCachedById) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kInvalidKeyCacheHit);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(
      1, KeyFetchingErrorType::kInvalidKeyId);

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  HpkeDecryptResponse empty_response;
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync)
      .WillOnce(Return(empty_response));

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));

  EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key),
              ResultIs(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GettingDecryptedDeksByWrappedKeyHandlesTimeout) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(-1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(-1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      -1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      -1, KeyCacheStatus::kInvalidKeyCacheHit);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(
      -1, KeyFetchingErrorType::kGenericError);

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  HpkeDecryptResponse response;
  response.set_payload(string(kDecryptedDek));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync).WillOnce([&response](auto) {
    sleep_for(std::chrono::milliseconds(1000));
    return response;
  });

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  constexpr auto kNumThreads = 10;
  std::atomic<int8_t> success_count = 0;
  std::vector<std::thread> work_threads;
  work_threads.reserve(kNumThreads);
  for (int i = 0; i < kNumThreads; i++) {
    work_threads.emplace_back([&] {
      auto decrypted_dek_or = wrapped_key_handler_.GetKey(cloud_wrapped_key);
      if (!decrypted_dek_or.result().Successful()) {
        EXPECT_THAT(decrypted_dek_or.result(),
                    ResultIs(FailureExecutionResult(
                        SC_CPIO_KEY_FETCHER_FETCHING_TIMEOUT)));
      }
      if (decrypted_dek_or.has_value() &&
          decrypted_dek_or.value() == kDecryptedDek) {
        ++success_count;
      }
    });
  }

  for (auto& t : work_threads) {
    if (t.joinable()) {
      t.join();
    }
  }
  EXPECT_EQ(success_count.load(), 1);
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GetDecryptionFailuresFromKeyFailureCache) {
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(-1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(-1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      -1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      -1, KeyCacheStatus::kInvalidKeyCacheHit);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(
      -1, KeyFetchingErrorType::kInvalidKeyId);

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync)
      .WillOnce(Return(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  constexpr auto kNumThreads = 100;
  std::vector<std::thread> work_threads;
  work_threads.reserve(kNumThreads);
  for (int i = 0; i < kNumThreads; i++) {
    work_threads.emplace_back([&] {
      EXPECT_THAT(wrapped_key_handler_.GetKey(cloud_wrapped_key).result(),
                  ResultIs(FailureExecutionResult(SC_CPIO_KEY_NOT_FOUND)));
    });
  }

  for (auto& t : work_threads) {
    if (t.joinable()) {
      t.join();
    }
  }
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GettingDecryptedDekByWrappedKeySuccessfulWithCacheDisabled) {
  int num_of_calls = 4;
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(num_of_calls);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(num_of_calls);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      num_of_calls, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(0);

  CoordinatorWrappedKeyHandlerWithCache key_handler_with_cache_disabled(
      async_executor_, mock_crypto_client_, mock_key_fetcher_with_cache_,
      CreateWrappedKeyHandlerOptions(/*enable_decryption_lock=*/false,
                                     /*enable_cache=*/false),
      mock_metric_client_);
  EXPECT_SUCCESS(key_handler_with_cache_disabled.Init());
  EXPECT_SUCCESS(key_handler_with_cache_disabled.Run());

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .Times(num_of_calls)
      .WillRepeatedly(Return(coordinator_key_));
  HpkeDecryptResponse response;
  response.set_payload(string(kDecryptedDek));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync)
      .Times(num_of_calls)
      .WillRepeatedly(Return(response));

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  for (int i = 0; i < num_of_calls; i++) {
    auto decrypted_dek_or =
        key_handler_with_cache_disabled.GetKey(cloud_wrapped_key);
    EXPECT_THAT(*decrypted_dek_or, Eq(kDecryptedDek));
  }
  EXPECT_SUCCESS(key_handler_with_cache_disabled.Stop());
}

TEST_F(CoordinatorWrappedKeyHandlerWithCacheTest,
       GettingDecryptedDekByWrappedKeySuccessfulWithCacheEnabled) {
  int num_of_calls = 4;
  ExpectOtelEncryptionKeyFetchingRequestMetricPush(1);
  ExpectOtelEncryptionKeyFetchingLatencyMetricPush(1);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      1, KeyCacheStatus::kValidKeyCacheMiss);
  ExpectOtelEncryptionKeyCacheStatusMetricPush(
      3, KeyCacheStatus::kValidKeyCacheHit);
  ExpectOtelEncryptionKeyFetchingErrorMetricPush(0);

  CoordinatorWrappedKeyHandlerWithCache key_handler_with_cache_enabled(
      async_executor_, mock_crypto_client_, mock_key_fetcher_with_cache_,
      CreateWrappedKeyHandlerOptions(/*enable_decryption_lock=*/false,
                                     /*enable_cache=*/true),
      mock_metric_client_);
  EXPECT_SUCCESS(key_handler_with_cache_enabled.Init());
  EXPECT_SUCCESS(key_handler_with_cache_enabled.Run());

  EXPECT_CALL(mock_key_fetcher_with_cache_, GetKey(string(kKeyId)))
      .WillOnce(Return(coordinator_key_));
  HpkeDecryptResponse response;
  response.set_payload(string(kDecryptedDek));
  EXPECT_CALL(mock_crypto_client_, HpkeDecryptSync).WillOnce(Return(response));

  auto cloud_wrapped_key = BuildCloudWrappedKey(coordinator_wrapped_key_);
  for (int i = 0; i < num_of_calls; i++) {
    auto decrypted_dek_or =
        key_handler_with_cache_enabled.GetKey(cloud_wrapped_key);
    EXPECT_THAT(*decrypted_dek_or, Eq(kDecryptedDek));
  }
  EXPECT_SUCCESS(key_handler_with_cache_enabled.Stop());
}

}  // namespace google::scp::cpio
