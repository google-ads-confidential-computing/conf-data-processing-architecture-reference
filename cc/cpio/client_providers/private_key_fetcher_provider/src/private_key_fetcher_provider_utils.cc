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

#include "private_key_fetcher_provider_utils.h"

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <nlohmann/json.hpp>
#include <tink/binary_keyset_writer.h>
#include <tink/cleartext_keyset_handle.h>
#include <tink/json_keyset_reader.h>

#include "absl/strings/str_cat.h"
#include "cc/core/utils/src/base64.h"
#include "core/interface/http_types.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/core/interface/execution_result_or_macros.h"

#include "error_codes.h"

using crypto::tink::BinaryKeysetWriter;
using crypto::tink::CleartextKeysetHandle;
using crypto::tink::JsonKeysetReader;
using google::scp::core::ExecutionResult;
using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::HttpMethod;
using google::scp::core::HttpRequest;
using google::scp::core::HttpResponse;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::Uri;
using google::scp::core::common::kZeroUuid;
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
    SC_PRIVATE_KEY_FETCHER_PROVIDER_PUBLIC_KEY_MATERIAL_NOT_FOUND;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_PUBLIC_KEYSET_HANDLE_NOT_FOUND;
using google::scp::core::errors::
    SC_PRIVATE_KEY_FETCHER_PROVIDER_RESOURCE_NAME_NOT_FOUND;
using google::scp::core::utils::Base64Encode;
using google::scp::cpio::client_providers::KeyData;
using google::scp::cpio::client_providers::PrivateKeyFetchingResponse;
using std::make_shared;
using std::shared_ptr;
using std::string;
using std::to_string;
using std::vector;

namespace {
constexpr char kEncryptionKeyPrefix[] = "encryptionKeys/";
constexpr char kEncryptionKeysLabel[] = "keys";
constexpr char kResourceNameLabel[] = "name";
constexpr char kEncryptionKeyType[] = "encryptionKeyType";
constexpr char kMultiPartyEnum[] = "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT";
constexpr char kSinglePartyEnum[] = "SINGLE_PARTY_HYBRID_KEY";
constexpr char kPublicKeysetHandle[] = "publicKeysetHandle";
constexpr char kPublicKeyMaterial[] = "publicKeyMaterial";
constexpr char kKeysetName[] = "setName";
constexpr char kExpirationTime[] = "expirationTime";
constexpr char kActivationTime[] = "activationTime";
constexpr char kCreationTime[] = "creationTime";
constexpr char kKeyData[] = "keyData";
constexpr char kPublicKeySignature[] = "publicKeySignature";
constexpr char kKeyEncryptionKeyUri[] = "keyEncryptionKeyUri";
constexpr char kKeyMaterial[] = "keyMaterial";
constexpr char kMaxAgeSecondsQueryParameter[] = "maxAgeSeconds=";
constexpr char kEncryptionKeyUrlSuffix[] = "/encryptionKeys";
}  // namespace

namespace google::scp::cpio::client_providers {

ExecutionResultOr<string> PrivateKeyFetchingClientUtils::ExtractKeyId(
    const string& resource_name) noexcept {
  if (resource_name.find(kEncryptionKeyPrefix) == 0) {
    return resource_name.substr(strlen(kEncryptionKeyPrefix));
  }
  return FailureExecutionResult(
      SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_RESOURCE_NAME);
}

ExecutionResult PrivateKeyFetchingClientUtils::ParsePrivateKey(
    const core::BytesBuffer& body,
    PrivateKeyFetchingResponse& response) noexcept {
  try {
    auto json_response =
        nlohmann::json::parse(body.bytes->begin(), body.bytes->end());
    auto json_keys = json_response.find(kEncryptionKeysLabel);
    if (json_keys == json_response.end()) {
      // For fetching encryption key by ID, will return only one key.
      return ParseEncryptionKey(json_response, response);
    } else {
      auto key_count = json_keys.value().size();
      for (size_t i = 0; i < key_count; ++i) {
        auto one_key_json = json_keys.value()[i];
        RETURN_IF_FAILURE(ParseEncryptionKey(one_key_json, response));
      }
    }
  } catch (std::exception& e) {
    auto execution_result = core::FailureExecutionResult(
        core::errors::SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_JSON);
    SCP_ERROR(kPrivateKeyFetcherProviderUtils, core::common::kZeroUuid,
              execution_result, "Failed to parse EncryptionKey Json: %s.",
              e.what());
    return execution_result;
  }
  return SuccessExecutionResult();
}

ExecutionResult PrivateKeyFetchingClientUtils::ParseEncryptionKey(
    const nlohmann::json& json_key,
    PrivateKeyFetchingResponse& response) noexcept {
  auto encryption_key = make_shared<EncryptionKey>();

  string name;
  auto result = ParseJsonValue(json_key, kResourceNameLabel, name);
  if (!result.Successful()) {
    return FailureExecutionResult(
        SC_PRIVATE_KEY_FETCHER_PROVIDER_RESOURCE_NAME_NOT_FOUND);
  }
  encryption_key->resource_name = make_shared<string>(name);

  string handleJsonStr;
  result = ParseJsonValue(json_key, kPublicKeysetHandle, handleJsonStr);
  if (!result.Successful()) {
    return FailureExecutionResult(
        SC_PRIVATE_KEY_FETCHER_PROVIDER_PUBLIC_KEYSET_HANDLE_NOT_FOUND);
  }
  if (handleJsonStr.empty()) {
    encryption_key->public_keyset_handle = make_shared<string>();
  } else {
    // Convert json string to tink binary form.
    auto keyset_reader_or = JsonKeysetReader::New(handleJsonStr);
    if (!keyset_reader_or.ok()) {
      auto execution_result = FailureExecutionResult(
          core::errors::
              SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_PUBLIC_KEYSET_HANDLE_JSON);
      SCP_ERROR(kPrivateKeyFetcherProviderUtils, kZeroUuid, execution_result,
                "Failed to create JsonKeysetReader: %s.",
                keyset_reader_or.status().ToString().c_str());
      return execution_result;
    }

    auto keyset_handle_or =
        CleartextKeysetHandle::Read(std::move(*keyset_reader_or));
    if (!keyset_handle_or.ok()) {
      auto execution_result = FailureExecutionResult(
          core::errors::
              SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_PUBLIC_KEYSET_HANDLE_JSON);
      SCP_ERROR(kPrivateKeyFetcherProviderUtils, kZeroUuid, execution_result,
                "Creating Keyset handle failed with error %s.",
                keyset_handle_or.status().ToString().c_str());
      return execution_result;
    }

    std::stringbuf public_key_buf(std::ios_base::out);
    auto keyset_writer = BinaryKeysetWriter::New(
        std::make_unique<std::ostream>(&public_key_buf));
    auto write_result = CleartextKeysetHandle::Write(
        keyset_writer->get(), *keyset_handle_or->release());
    if (!write_result.ok()) {
      auto execution_result = FailureExecutionResult(
          core::errors::
              SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_PUBLIC_KEYSET_HANDLE_JSON);
      SCP_ERROR(kPrivateKeyFetcherProviderUtils, kZeroUuid, execution_result,
                "Write binary keyset failed with error %s.",
                write_result.ToString().c_str());
      return execution_result;
    }

    auto encoded_key_or = Base64Encode(public_key_buf.str());
    RETURN_AND_LOG_IF_FAILURE(encoded_key_or.result(),
                              kPrivateKeyFetcherProviderUtils, kZeroUuid,
                              "Encode public keyset handle failed.");

    encryption_key->public_keyset_handle = make_shared<string>(*encoded_key_or);
  }
  string public_key_material;
  result = ParseJsonValue(json_key, kPublicKeyMaterial, public_key_material);
  if (!result.Successful()) {
    return FailureExecutionResult(
        SC_PRIVATE_KEY_FETCHER_PROVIDER_PUBLIC_KEY_MATERIAL_NOT_FOUND);
  }
  encryption_key->public_key_material =
      make_shared<string>(public_key_material);

  string keyset_name;
  result = ParseJsonValue(json_key, kKeysetName, keyset_name);
  if (result.Successful()) {
    encryption_key->keyset_name = make_shared<string>(keyset_name);
  } else {
    SCP_ERROR(kPrivateKeyFetcherProviderUtils, core::common::kZeroUuid, result,
              "Failed to parse keyset name for key %s.", name.c_str());
  }

  EncryptionKeyType type;
  result = ParseEncryptionKeyType(json_key, kEncryptionKeyType, type);
  if (!result.Successful()) {
    return result;
  }
  encryption_key->encryption_key_type = type;

  string expiration_time;
  result = ParseJsonValue(json_key, kExpirationTime, expiration_time);
  if (!result.Successful()) {
    return FailureExecutionResult(
        SC_PRIVATE_KEY_FETCHER_PROVIDER_EXPIRATION_TIME_NOT_FOUND);
  }
  encryption_key->expiration_time_in_ms = std::stol(expiration_time);

  string activation_time;
  result = ParseJsonValue(json_key, kActivationTime, activation_time);
  if (!result.Successful()) {
    return FailureExecutionResult(
        SC_PRIVATE_KEY_FETCHER_PROVIDER_ACTIVATION_TIME_NOT_FOUND);
  }
  encryption_key->activation_time_in_ms = std::stol(activation_time);

  string creation_time;
  result = ParseJsonValue(json_key, kCreationTime, creation_time);
  if (!result.Successful()) {
    return FailureExecutionResult(
        SC_PRIVATE_KEY_FETCHER_PROVIDER_CREATION_TIME_NOT_FOUND);
  }
  encryption_key->creation_time_in_ms = std::stol(creation_time);

  vector<shared_ptr<KeyData>> key_data;
  result = ParseKeyData(json_key, kKeyData, key_data);
  if (!result.Successful()) {
    return result;
  }
  encryption_key->key_data =
      vector<shared_ptr<KeyData>>(key_data.begin(), key_data.end());

  auto key_id_or = ExtractKeyId(*encryption_key->resource_name);
  RETURN_IF_FAILURE(key_id_or.result());

  encryption_key->key_id = make_shared<string>(*key_id_or);
  response.encryption_keys.emplace_back(encryption_key);

  return SuccessExecutionResult();
}

ExecutionResult PrivateKeyFetchingClientUtils::ParseEncryptionKeyType(
    const nlohmann::json& json_response, const string& type_tag,
    EncryptionKeyType& key_type) noexcept {
  try {
    auto it = json_response.find(type_tag);
    if (it == json_response.end()) {
      return FailureExecutionResult(
          SC_PRIVATE_KEY_FETCHER_PROVIDER_ENCRYPTION_KEY_TYPE_NOT_FOUND);
    }

    if (it.value() == kMultiPartyEnum) {
      key_type = EncryptionKeyType::kMultiPartyHybridEvenKeysplit;
    } else if (it.value() == kSinglePartyEnum) {
      key_type = EncryptionKeyType::kSinglePartyHybridKey;
    } else {
      return FailureExecutionResult(
          SC_PRIVATE_KEY_FETCHER_PROVIDER_INVALID_ENCRYPTION_KEY_TYPE);
    }
  } catch (std::exception& e) {
    auto execution_result = core::FailureExecutionResult(
        core::errors::SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_JSON);
    SCP_ERROR(kPrivateKeyFetcherProviderUtils, core::common::kZeroUuid,
              execution_result, "Failed to parse EncryptionKeyType Json: %s.",
              e.what());
    return execution_result;
  }

  return SuccessExecutionResult();
}

ExecutionResult PrivateKeyFetchingClientUtils::ParseKeyData(
    const nlohmann::json& json_response, const string& key_data_tag,
    vector<shared_ptr<KeyData>>& key_data_list) noexcept {
  try {
    auto key_data_json = json_response.find(key_data_tag);
    if (key_data_json == json_response.end()) {
      return FailureExecutionResult(
          SC_PRIVATE_KEY_FETCHER_PROVIDER_KEY_DATA_NOT_FOUND);
    }

    auto key_data_size = key_data_json.value().size();
    auto found_key_material = false;

    for (size_t i = 0; i < key_data_size; ++i) {
      auto json_chunk = key_data_json.value()[i];
      KeyData key_data;

      string kek_uri;
      auto result = ParseJsonValue(json_chunk, kKeyEncryptionKeyUri, kek_uri);
      if (!result.Successful()) {
        return result;
      }
      key_data.key_encryption_key_uri = make_shared<string>(kek_uri);

      string key_material;
      result = ParseJsonValue(json_chunk, kKeyMaterial, key_material);
      if (!result.Successful()) {
        return result;
      }
      key_data.key_material = make_shared<string>(key_material);

      if (!key_material.empty() && !kek_uri.empty()) {
        found_key_material = true;
      }

      string public_key_signature;
      result =
          ParseJsonValue(json_chunk, kPublicKeySignature, public_key_signature);
      if (!result.Successful()) {
        return result;
      }
      key_data.public_key_signature = make_shared<string>(public_key_signature);

      key_data_list.emplace_back(make_shared<KeyData>(key_data));
    }

    // Must have one pair of key_encryption_key_uri and key_material.
    if (!found_key_material) {
      return FailureExecutionResult(
          SC_PRIVATE_KEY_FETCHER_PROVIDER_KEY_MATERIAL_NOT_FOUND);
    }
  } catch (std::exception& e) {
    auto execution_result = core::FailureExecutionResult(
        core::errors::SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_JSON);
    SCP_ERROR(kPrivateKeyFetcherProviderUtils, core::common::kZeroUuid,
              execution_result, "Failed to KeyData Json: %s.", e.what());
    return execution_result;
  }

  return SuccessExecutionResult();
}
}  // namespace google::scp::cpio::client_providers
