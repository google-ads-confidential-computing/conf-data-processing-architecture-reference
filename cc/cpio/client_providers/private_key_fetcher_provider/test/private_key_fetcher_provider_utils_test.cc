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

#include "cpio/client_providers/private_key_fetcher_provider/src/private_key_fetcher_provider_utils.h"

#include <gtest/gtest.h>

#include <memory>
#include <string>
#include <utility>

#include "absl/strings/str_format.h"
#include "core/interface/http_types.h"
#include "core/test/utils/scp_test_base.h"
#include "cpio/client_providers/crypto_client_provider/src/crypto_client_provider.h"
#include "public/core/interface/execution_result.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/interface/crypto_client/type_def.h"
#include "public/cpio/proto/crypto_service/v1/crypto_service.pb.h"

using google::cmrt::sdk::crypto_service::v1::HpkeEncryptRequest;
using google::cmrt::sdk::private_key_service::v1::PrivateKeyEndpoint;
using google::scp::core::BytesBuffer;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::HttpMethod;
using google::scp::core::HttpRequest;
using google::scp::core::HttpResponse;
using google::scp::core::errors::GetErrorMessage;
using google::scp::core::errors::
    SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_PUBLIC_KEYSET_HANDLE_JSON;
using google::scp::core::errors::
    SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_RESOURCE_NAME;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_ACTIVATION_TIME_NOT_FOUND;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_CREATION_TIME_NOT_FOUND;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_ENCRYPTION_KEY_TYPE_NOT_FOUND;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_EXPIRATION_TIME_NOT_FOUND;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_INVALID_ENCRYPTION_KEY_TYPE;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_KEY_DATA_NOT_FOUND;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_KEY_MATERIAL_NOT_FOUND;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_PUBLIC_KEYSET_HANDLE_NOT_FOUND;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_RESOURCE_NAME_NOT_FOUND;
using google::scp::core::test::IsSuccessful;
using google::scp::core::test::IsSuccessfulAndHolds;
using google::scp::core::test::ResultIs;
using google::scp::core::test::ScpTestBase;
using google::scp::cpio::client_providers::KeyData;
using google::scp::cpio::client_providers::PrivateKeyFetchingResponse;
using std::make_shared;
using std::shared_ptr;
using std::stoi;
using std::string;
using std::to_string;
using ::testing::Eq;

namespace {
constexpr char kKeyId[] = "123";
constexpr char kPrivateKeyBaseUri[] = "http://localhost.test:8000/v1beta";
constexpr char kPublicKeyJson[] =
    R"({\"key\":[{\"keyData\":{\"keyMaterialType\":\"ASYMMETRIC_PUBLIC\",\"typeUrl\":\"type.googleapis.com/google.crypto.tink.HpkePublicKey\",\"value\":\"EgYIARABGAMaIEMQ7pfYjMHwiKVXbHerDPXDrHl/PZUTnGyEtUKcWWYq\"},\"keyId\":123,\"outputPrefixType\":\"RAW\",\"status\":\"ENABLED\"}],\"primaryKeyId\":123})";  // NOLINT

}  // namespace

namespace google::scp::cpio::client_providers::test {

class PrivateKeyFetchingClientUtilsTest : public ScpTestBase {};

TEST_F(PrivateKeyFetchingClientUtilsTest, ParsePrivateKeySuccess) {
  string bytes_str = absl::StrFormat(R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "%s",
        "publicKeyMaterial": "testtest",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": "test=test"
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })",
                                     kPublicKeyJson);
  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_SUCCESS(result);
  EXPECT_EQ(response.encryption_keys.size(), 1);
  const auto& encryption_key = *response.encryption_keys.begin();
  EXPECT_EQ(*encryption_key->key_id, "123456");
  EXPECT_EQ(*encryption_key->resource_name, "encryptionKeys/123456");
  EXPECT_EQ(encryption_key->encryption_key_type,
            EncryptionKeyType::kMultiPartyHybridEvenKeysplit);
  auto options = make_shared<CryptoClientOptions>();
  auto client = make_unique<CryptoClientProvider>(options);
  EXPECT_SUCCESS(client->Init());
  EXPECT_SUCCESS(client->Run());
  HpkeEncryptRequest request;
  request.set_tink_key_binary(*encryption_key->public_keyset_handle);
  request.set_payload("payload");
  EXPECT_SUCCESS(client->HpkeEncryptSync(request).result());
  EXPECT_SUCCESS(client->Stop());
  EXPECT_EQ(*encryption_key->public_key_material, "testtest");
  EXPECT_EQ(encryption_key->expiration_time_in_ms, 1669943990485);
  EXPECT_EQ(encryption_key->creation_time_in_ms, 1669252790485);
  EXPECT_EQ(encryption_key->activation_time_in_ms, 1669252790486);
  EXPECT_EQ(*encryption_key->key_data[0]->key_encryption_key_uri,
            "aws-kms://arn:aws:kms:us-east-1:1234567:key");
  EXPECT_EQ(*encryption_key->key_data[0]->public_key_signature, "");
  EXPECT_EQ(*encryption_key->key_data[0]->key_material, "test=test");
  EXPECT_EQ(*encryption_key->key_data[1]->key_encryption_key_uri,
            "aws-kms://arn:aws:kms:us-east-1:12345:key");
  EXPECT_EQ(*encryption_key->key_data[1]->public_key_signature, "");
  EXPECT_EQ(*encryption_key->key_data[1]->key_material, "");
}

TEST_F(PrivateKeyFetchingClientUtilsTest, CreateKeysetReaderFailure) {
  string bytes_str = R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "{\"key\":[{\"keyData\":",
        "publicKeyMaterial": "testtest",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": "test=test"
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })";
  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_THAT(
      result,
      ResultIs(FailureExecutionResult(
          SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_PUBLIC_KEYSET_HANDLE_JSON)));
}

TEST_F(PrivateKeyFetchingClientUtilsTest, CreateKeysetHandleFailure) {
  string bytes_str = R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "{\"key\":\"abc\"}",
        "publicKeyMaterial": "testtest",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": "test=test"
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })";
  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_THAT(
      result,
      ResultIs(FailureExecutionResult(
          SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_PUBLIC_KEYSET_HANDLE_JSON)));
}

TEST_F(PrivateKeyFetchingClientUtilsTest, FailedWithInvalidKeyData) {
  string bytes_str = absl::StrFormat(R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "%s",
        "publicKeyMaterial": "testtest",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "",
                "keyMaterial": "test=test"
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })",
                                     kPublicKeyJson);

  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_PRIVATE_KEY_FETCHER_PROVIDER_KEY_MATERIAL_NOT_FOUND)));
}

TEST_F(PrivateKeyFetchingClientUtilsTest, FailedWithInvalidKeyDataNoKeyUri) {
  string bytes_str = absl::StrFormat(R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "%s",
        "publicKeyMaterial": "testtest",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": ""
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })",
                                     kPublicKeyJson);

  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_PRIVATE_KEY_FETCHER_PROVIDER_KEY_MATERIAL_NOT_FOUND)));
}

TEST_F(PrivateKeyFetchingClientUtilsTest, FailedWithInvalidKeyType) {
  string bytes_str = absl::StrFormat(R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT_WRONG",
        "publicKeysetHandle": "%s",
        "publicKeyMaterial": "testtest",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": ""
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })",
                                     kPublicKeyJson);

  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  auto failure_result = FailureExecutionResult(
      SC_PRIVATE_KEY_FETCHER_PROVIDER_INVALID_ENCRYPTION_KEY_TYPE);
  EXPECT_THAT(result, ResultIs(failure_result));
}

TEST_F(PrivateKeyFetchingClientUtilsTest, FailedWithNameNotFound) {
  string bytes_str = absl::StrFormat(R"({
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "%s",
        "publicKeyMaterial": "testtest",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": ""
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })",
                                     kPublicKeyJson);

  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_PRIVATE_KEY_FETCHER_PROVIDER_RESOURCE_NAME_NOT_FOUND)));
}

TEST_F(PrivateKeyFetchingClientUtilsTest, FailedWithExpirationTimeNotFound) {
  string bytes_str = absl::StrFormat(R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "%s",
        "publicKeyMaterial": "testtest",
        "creationTime": "1669252790485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": ""
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })",
                                     kPublicKeyJson);

  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_PRIVATE_KEY_FETCHER_PROVIDER_EXPIRATION_TIME_NOT_FOUND)));
}

TEST_F(PrivateKeyFetchingClientUtilsTest, FailedWithActivationTimeNotFound) {
  string bytes_str = absl::StrFormat(R"({
          "name": "encryptionKeys/123456",
          "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
          "publicKeysetHandle": "%s",
          "publicKeyMaterial": "testtest",
          "creationTime": "1669252790485",
          "expirationTime": "1669943990485",
          "ttlTime": 0,
          "keyData": [
              {
                  "publicKeySignature": "",
                  "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                  "keyMaterial": ""
              },
              {
                  "publicKeySignature": "",
                  "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                  "keyMaterial": ""
              }
          ]
      })",
                                     kPublicKeyJson);

  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_PRIVATE_KEY_FETCHER_PROVIDER_ACTIVATION_TIME_NOT_FOUND)));
}

TEST_F(PrivateKeyFetchingClientUtilsTest, FailedWithCreationTimeNotFound) {
  string bytes_str = absl::StrFormat(R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "%s",
        "publicKeyMaterial": "testtest",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": ""
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })",
                                     kPublicKeyJson);

  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_THAT(result,
              ResultIs(FailureExecutionResult(
                  SC_PRIVATE_KEY_FETCHER_PROVIDER_CREATION_TIME_NOT_FOUND)));
}

TEST_F(PrivateKeyFetchingClientUtilsTest, ParseMultiplePrivateKeysSuccess) {
  string one_key_without_name = absl::StrFormat(R"(
           "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "%s",
        "publicKeyMaterial": "testtest",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": "test=test"
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })",
                                                kPublicKeyJson);
  string key_1 = R"({"name": "encryptionKeys/111111",)";
  string key_2 = R"({"name": "encryptionKeys/222222",)";
  string bytes_str = R"({"keys": [)" + key_1 + one_key_without_name + "," +
                     key_2 + one_key_without_name + "]}";

  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_SUCCESS(result);
  EXPECT_EQ(response.encryption_keys.size(), 2);
  EXPECT_EQ(*response.encryption_keys[0]->key_id, "111111");
  EXPECT_EQ(*response.encryption_keys[1]->key_id, "222222");
}

TEST_F(PrivateKeyFetchingClientUtilsTest,
       ParsePrivateKeyWithoutPublicKeySuccessWithoutSetName) {
  string bytes_str = R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "",
        "publicKeyMaterial": "",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": "test=test"
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })";
  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_SUCCESS(result);
  EXPECT_EQ(response.encryption_keys.size(), 1);
  const auto& encryption_key = *response.encryption_keys.begin();
  EXPECT_EQ(*encryption_key->key_id, "123456");
  EXPECT_EQ(*encryption_key->resource_name, "encryptionKeys/123456");
  EXPECT_EQ(encryption_key->encryption_key_type,
            EncryptionKeyType::kMultiPartyHybridEvenKeysplit);
  EXPECT_EQ(*encryption_key->public_keyset_handle, "");
  EXPECT_EQ(*encryption_key->public_key_material, "");
  EXPECT_EQ(encryption_key->expiration_time_in_ms, 1669943990485);
  EXPECT_EQ(encryption_key->creation_time_in_ms, 1669252790485);
  EXPECT_EQ(encryption_key->activation_time_in_ms, 1669252790486);
  EXPECT_EQ(*encryption_key->key_data[0]->key_encryption_key_uri,
            "aws-kms://arn:aws:kms:us-east-1:1234567:key");
  EXPECT_EQ(*encryption_key->key_data[0]->public_key_signature, "");
  EXPECT_EQ(*encryption_key->key_data[0]->key_material, "test=test");
  EXPECT_EQ(*encryption_key->key_data[1]->key_encryption_key_uri,
            "aws-kms://arn:aws:kms:us-east-1:12345:key");
  EXPECT_EQ(*encryption_key->key_data[1]->public_key_signature, "");
  EXPECT_EQ(*encryption_key->key_data[1]->key_material, "");
}

TEST_F(PrivateKeyFetchingClientUtilsTest,
       ParsePrivateKeyWithoutPublicKeySuccessWithSetName) {
  string bytes_str = R"({
        "name": "encryptionKeys/123456",
        "encryptionKeyType": "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT",
        "publicKeysetHandle": "",
        "publicKeyMaterial": "",
        "creationTime": "1669252790485",
        "expirationTime": "1669943990485",
        "activationTime": "1669252790486",
        "ttlTime": 0,
        "setName": "testSetName",
        "keyData": [
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:1234567:key",
                "keyMaterial": "test=test"
            },
            {
                "publicKeySignature": "",
                "keyEncryptionKeyUri": "aws-kms://arn:aws:kms:us-east-1:12345:key",
                "keyMaterial": ""
            }
        ]
    })";
  PrivateKeyFetchingResponse response;
  auto result = PrivateKeyFetchingClientUtils::ParsePrivateKey(
      BytesBuffer(bytes_str), response);

  EXPECT_SUCCESS(result);
  EXPECT_EQ(response.encryption_keys.size(), 1);
  const auto& encryption_key = *response.encryption_keys.begin();
  EXPECT_EQ(*encryption_key->keyset_name, "testSetName");
  EXPECT_EQ(*encryption_key->resource_name, "encryptionKeys/123456");
  EXPECT_EQ(encryption_key->encryption_key_type,
            EncryptionKeyType::kMultiPartyHybridEvenKeysplit);
  EXPECT_EQ(*encryption_key->public_keyset_handle, "");
  EXPECT_EQ(*encryption_key->public_key_material, "");
  EXPECT_EQ(encryption_key->expiration_time_in_ms, 1669943990485);
  EXPECT_EQ(encryption_key->creation_time_in_ms, 1669252790485);
  EXPECT_EQ(encryption_key->activation_time_in_ms, 1669252790486);
  EXPECT_EQ(*encryption_key->key_data[0]->key_encryption_key_uri,
            "aws-kms://arn:aws:kms:us-east-1:1234567:key");
  EXPECT_EQ(*encryption_key->key_data[0]->public_key_signature, "");
  EXPECT_EQ(*encryption_key->key_data[0]->key_material, "test=test");
  EXPECT_EQ(*encryption_key->key_data[1]->key_encryption_key_uri,
            "aws-kms://arn:aws:kms:us-east-1:12345:key");
  EXPECT_EQ(*encryption_key->key_data[1]->public_key_signature, "");
  EXPECT_EQ(*encryption_key->key_data[1]->key_material, "");
}

TEST_F(PrivateKeyFetchingClientUtilsTest, ExtractKeyId) {
  auto key_id_or =
      PrivateKeyFetchingClientUtils::ExtractKeyId("encryptionKeys/123456");
  EXPECT_THAT(key_id_or, IsSuccessfulAndHolds(Eq("123456")));

  key_id_or = PrivateKeyFetchingClientUtils::ExtractKeyId("encryption/123456");
  EXPECT_THAT(key_id_or,
              ResultIs(FailureExecutionResult(
                  SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_RESOURCE_NAME)));
}
}  // namespace google::scp::cpio::client_providers::test
