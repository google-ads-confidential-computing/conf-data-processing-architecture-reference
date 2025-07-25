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

#include "private_key_client_provider.h"

#include <atomic>
#include <functional>
#include <memory>
#include <utility>
#include <vector>

#include "core/interface/async_context.h"
#include "core/interface/http_client_interface.h"
#include "core/interface/http_types.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/core/interface/execution_result_or_macros.h"
#include "public/cpio/interface/private_key_client/type_def.h"
#include "public/cpio/proto/private_key_service/v1/private_key_service.pb.h"

#include "error_codes.h"
#include "private_key_client_utils.h"

using google::cmrt::sdk::kms_service::v1::DecryptRequest;
using google::cmrt::sdk::kms_service::v1::DecryptResponse;
using google::cmrt::sdk::private_key_service::v1::
    ListActiveEncryptionKeysRequest;
using google::cmrt::sdk::private_key_service::v1::
    ListActiveEncryptionKeysResponse;
using google::cmrt::sdk::private_key_service::v1::ListPrivateKeysRequest;
using google::cmrt::sdk::private_key_service::v1::ListPrivateKeysResponse;
using google::cmrt::sdk::private_key_service::v1::PrivateKey;
using google::cmrt::sdk::private_key_service::v1::PrivateKeyEndpoint;
using google::scp::core::AsyncContext;
using google::scp::core::ExecutionResult;
using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::HttpClientInterface;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::Uri;
using google::scp::core::common::ConcurrentMap;
using google::scp::core::common::kZeroUuid;
using google::scp::core::errors::SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_REQUEST;
using google::scp::core::errors::
    SC_PRIVATE_KEY_CLIENT_PROVIDER_UNMATCHED_ENDPOINTS_SPLITS;
using std::atomic;
using std::bind;
using std::make_pair;
using std::make_shared;
using std::move;
using std::shared_ptr;
using std::string;
using std::vector;
using std::placeholders::_1;

static constexpr char kPrivateKeyClientProvider[] = "PrivateKeyClientProvider";

namespace google::scp::cpio::client_providers {
ExecutionResult PrivateKeyClientProvider::Init() noexcept {
  return SuccessExecutionResult();
}

ExecutionResult PrivateKeyClientProvider::Run() noexcept {
  return SuccessExecutionResult();
}

ExecutionResult PrivateKeyClientProvider::Stop() noexcept {
  return SuccessExecutionResult();
}

void PrivateKeyClientProvider::ListPrivateKeys(
    AsyncContext<ListPrivateKeysRequest, ListPrivateKeysResponse>&
        list_private_keys_context) noexcept {
  if (list_private_keys_context.request->key_ids().empty() &&
      list_private_keys_context.request->max_age_seconds() <= 0) {
    list_private_keys_context.result =
        FailureExecutionResult(SC_PRIVATE_KEY_CLIENT_PROVIDER_INVALID_REQUEST);
    SCP_ERROR_CONTEXT(kPrivateKeyClientProvider, list_private_keys_context,
                      list_private_keys_context.result,
                      "The list of key_ids is empty and the max_age_seconds is "
                      "invalid in the request.");
    list_private_keys_context.Finish();
  }
  ListPrivateKeysBase(list_private_keys_context);
}

void PrivateKeyClientProvider::ListActiveEncryptionKeys(
    AsyncContext<ListActiveEncryptionKeysRequest,
                 ListActiveEncryptionKeysResponse>&
        list_active_encryption_keys_context) noexcept {
  auto list_private_keys_request = make_shared<ListPrivateKeysRequest>();
  list_private_keys_request->set_key_set_name(
      list_active_encryption_keys_context.request->key_set_name());
  *list_private_keys_request->mutable_key_endpoints() =
      list_active_encryption_keys_context.request->key_endpoints();
  list_private_keys_request->set_max_age_seconds(0);

  AsyncContext<ListPrivateKeysRequest, ListPrivateKeysResponse>
      list_private_key_context(
          move(list_private_keys_request),
          bind(&PrivateKeyClientProvider::OnFetchActiveEncryptionKeysCallback,
               this, list_active_encryption_keys_context, _1),
          list_active_encryption_keys_context);

  ListPrivateKeysBase(list_private_key_context);
}

void PrivateKeyClientProvider::ListPrivateKeysBase(
    core::AsyncContext<
        cmrt::sdk::private_key_service::v1::ListPrivateKeysRequest,
        cmrt::sdk::private_key_service::v1::ListPrivateKeysResponse>&
        list_private_keys_context) noexcept {
  auto endpoint_count = list_private_keys_context.request->key_endpoints_size();
  auto key_ids = list_private_keys_context.request->key_ids();
  auto max_age_seconds = list_private_keys_context.request->max_age_seconds();

  auto list_keys_status = make_shared<ListPrivateKeysStatus>();
  list_keys_status->listing_method =
      key_ids.empty() ? ListingMethod::kByMaxAge : ListingMethod::kByKeyId;
  if (key_ids.empty() && max_age_seconds == 0) {
    list_keys_status->listing_method = ListingMethod::kByActiveKeys;
  }
  list_keys_status->result_list = vector<KeysResultPerEndpoint>(endpoint_count);

  list_keys_status->call_count_per_endpoint =
      list_keys_status->listing_method == ListingMethod::kByKeyId
          ? key_ids.size()
          : 1;

  for (size_t call_index = 0;
       call_index < list_keys_status->call_count_per_endpoint; ++call_index) {
    for (size_t uri_index = 0; uri_index < endpoint_count; ++uri_index) {
      auto request = make_shared<PrivateKeyFetchingRequest>();

      if (list_keys_status->listing_method == ListingMethod::kByKeyId) {
        request->key_id = make_shared<string>(key_ids[call_index]);
      } else {
        request->max_age_seconds = max_age_seconds;
      }

      const auto& endpoint =
          list_private_keys_context.request->key_endpoints().Get(uri_index);
      request->key_endpoint = make_shared<PrivateKeyEndpoint>(endpoint);

      request->key_set_name = make_shared<string>(
          list_private_keys_context.request->key_set_name());

      AsyncContext<PrivateKeyFetchingRequest, PrivateKeyFetchingResponse>
          fetch_private_key_context(
              move(request),
              bind(&PrivateKeyClientProvider::OnFetchPrivateKeyCallback, this,
                   list_private_keys_context, _1, list_keys_status, uri_index),
              list_private_keys_context);

      private_key_fetcher_->FetchPrivateKey(fetch_private_key_context);
    }
  }
}

void PrivateKeyClientProvider::OnFetchActiveEncryptionKeysCallback(
    AsyncContext<ListActiveEncryptionKeysRequest,
                 ListActiveEncryptionKeysResponse>&
        list_active_encryption_keys_context,
    AsyncContext<ListPrivateKeysRequest, ListPrivateKeysResponse>&
        list_private_key_context) noexcept {
  auto execution_result = list_private_key_context.result;
  list_active_encryption_keys_context.result = execution_result;
  list_active_encryption_keys_context.response =
      make_shared<ListActiveEncryptionKeysResponse>();
  if (execution_result.Successful()) {
    *list_active_encryption_keys_context.response->mutable_result() =
        move(*list_private_key_context.response->mutable_result());
    list_active_encryption_keys_context.response->set_key_set_name(
        move(list_active_encryption_keys_context.request->key_set_name()));
    *list_active_encryption_keys_context.response->mutable_private_keys() =
        move(*list_private_key_context.response->mutable_private_keys());
  }
  list_active_encryption_keys_context.Finish();
}

void PrivateKeyClientProvider::OnFetchPrivateKeyCallback(
    AsyncContext<ListPrivateKeysRequest, ListPrivateKeysResponse>&
        list_private_keys_context,
    AsyncContext<PrivateKeyFetchingRequest, PrivateKeyFetchingResponse>&
        fetch_private_key_context,
    shared_ptr<ListPrivateKeysStatus> list_keys_status,
    size_t uri_index) noexcept {
  if (list_keys_status->got_failure.load()) {
    return;
  }

  auto execution_result = fetch_private_key_context.result;
  if (list_keys_status->listing_method == ListingMethod::kByKeyId) {
    ExecutionResult out;
    if (auto insert_result =
            list_keys_status->result_list[uri_index]
                .fetch_result_key_id_map.Insert(
                    make_pair(*fetch_private_key_context.request->key_id,
                              execution_result),
                    out);
        !insert_result.Successful()) {
      auto got_failure = false;
      if (list_keys_status->got_failure.compare_exchange_strong(got_failure,
                                                                true)) {
        list_private_keys_context.result = insert_result;
        SCP_ERROR_CONTEXT(kPrivateKeyClientProvider, list_private_keys_context,
                          list_private_keys_context.result,
                          "Failed to insert fetch result");
        list_private_keys_context.Finish();
      }
      return;
    }
    // For ListByKeyId, store the key IDs no matter the fetching failed or
    // not.
    list_keys_status->set_mutex.lock();
    list_keys_status->key_id_set.insert(
        *fetch_private_key_context.request->key_id);
    list_keys_status->set_mutex.unlock();
  } else {
    list_keys_status->result_list[uri_index].fetch_result = execution_result;
  }

  // For empty key list, call callback directly.
  if (!execution_result.Successful() ||
      fetch_private_key_context.response->encryption_keys.empty()) {
    if (!execution_result.Successful()) {
      SCP_ERROR_CONTEXT(
          kPrivateKeyClientProvider, list_private_keys_context,
          execution_result, "Failed to fetch keys from endpoint: %s",
          fetch_private_key_context.request->key_endpoint->endpoint().c_str());
    } else {
      SCP_WARNING_CONTEXT(
          kPrivateKeyClientProvider, list_private_keys_context,
          "Fetched response without keys from endpoint: %s",
          fetch_private_key_context.request->key_endpoint->endpoint().c_str());
    }
    list_keys_status->fetching_call_returned_count.fetch_add(1);
    AsyncContext<DecryptRequest, DecryptResponse> decrypt_context(
        make_shared<DecryptRequest>(), [](auto&) {}, list_private_keys_context);
    decrypt_context.result = SuccessExecutionResult();
    OnDecryptCallback(list_private_keys_context, decrypt_context,
                      list_keys_status, nullptr, uri_index);
    return;
  }

  list_keys_status->total_key_split_count.fetch_add(
      fetch_private_key_context.response->encryption_keys.size());
  // Increase returned count after increasing the count of issued calls in case
  // some calls are missed.
  list_keys_status->fetching_call_returned_count.fetch_add(1);

  for (const auto& encryption_key :
       fetch_private_key_context.response->encryption_keys) {
    if (list_keys_status->listing_method != ListingMethod::kByKeyId) {
      list_keys_status->set_mutex.lock();
      list_keys_status->key_id_set.insert(*encryption_key->key_id);
      list_keys_status->set_mutex.unlock();
    }
    DecryptRequest kms_decrypt_request;
    execution_result = PrivateKeyClientUtils::GetKmsDecryptRequest(
        encryption_key, kms_decrypt_request);
    if (!execution_result.Successful()) {
      auto got_failure = false;
      if (list_keys_status->got_failure.compare_exchange_strong(got_failure,
                                                                true)) {
        list_private_keys_context.result = execution_result;
        SCP_ERROR_CONTEXT(kPrivateKeyClientProvider, list_private_keys_context,
                          list_private_keys_context.result,
                          "Failed to get the key data.");
        list_private_keys_context.Finish();
      }
      return;
    }
    // Still need the account_identity for GCP.
    kms_decrypt_request.set_account_identity(
        fetch_private_key_context.request->key_endpoint->account_identity());
    //// Only used for AWS Start ////
    kms_decrypt_request.set_kms_region(
        fetch_private_key_context.request->key_endpoint->key_service_region());
    //// Only used for AWS End ////
    //// Only used for GCP Start ////
    kms_decrypt_request.set_gcp_wip_provider(
        fetch_private_key_context.request->key_endpoint->gcp_wip_provider());
    //// Only used for GCP End ////
    AsyncContext<DecryptRequest, DecryptResponse> decrypt_context(
        make_shared<DecryptRequest>(kms_decrypt_request),
        bind(&PrivateKeyClientProvider::OnDecryptCallback, this,
             list_private_keys_context, _1, list_keys_status, encryption_key,
             uri_index),
        list_private_keys_context);
    kms_client_provider_->Decrypt(decrypt_context);
  }
}

ExecutionResult InsertDecryptResult(
    ConcurrentMap<string, DecryptResult>& decrypt_result_key_id_map,
    EncryptionKey encryption_key, ExecutionResult result, string plaintext) {
  DecryptResult decrypt_result;
  decrypt_result.decrypt_result = move(result);
  decrypt_result.encryption_key = move(encryption_key);
  if (!plaintext.empty()) {
    decrypt_result.plaintext = move(plaintext);
  }

  DecryptResult out;
  RETURN_AND_LOG_IF_FAILURE(
      decrypt_result_key_id_map.Insert(
          make_pair(*decrypt_result.encryption_key.key_id, decrypt_result),
          out),
      kPrivateKeyClientProvider, kZeroUuid, "Failed to insert decrypt result");
  return SuccessExecutionResult();
}

void PrivateKeyClientProvider::OnDecryptCallback(
    AsyncContext<ListPrivateKeysRequest, ListPrivateKeysResponse>&
        list_private_keys_context,
    AsyncContext<DecryptRequest, DecryptResponse>& decrypt_context,
    shared_ptr<ListPrivateKeysStatus> list_keys_status,
    shared_ptr<EncryptionKey> encryption_key, size_t uri_index) noexcept {
  if (list_keys_status->got_failure.load()) {
    return;
  }

  atomic<size_t> finished_key_split_count_prev(
      list_keys_status->finished_key_split_count.load() - 1);
  if (encryption_key) {
    string plaintext;
    if (decrypt_context.result.Successful()) {
      plaintext = move(*decrypt_context.response->mutable_plaintext());
    }
    if (auto insert_result = InsertDecryptResult(
            list_keys_status->result_list[uri_index].decrypt_result_key_id_map,
            *encryption_key, move(decrypt_context.result), move(plaintext));
        !insert_result.Successful()) {
      auto got_failure = false;
      if (list_keys_status->got_failure.compare_exchange_strong(got_failure,
                                                                true)) {
        list_private_keys_context.result = insert_result;
        SCP_ERROR_CONTEXT(kPrivateKeyClientProvider, list_private_keys_context,
                          list_private_keys_context.result,
                          "Failed to insert decrypt result.");
        list_private_keys_context.Finish();
      }
      return;
    }
    finished_key_split_count_prev =
        list_keys_status->finished_key_split_count.fetch_add(1);
  }

  // Finished all remote calls.
  auto endpoint_count = list_private_keys_context.request->key_endpoints_size();
  if (list_keys_status->fetching_call_returned_count ==
          list_keys_status->call_count_per_endpoint * endpoint_count &&
      finished_key_split_count_prev ==
          list_keys_status->total_key_split_count - 1) {
    list_private_keys_context.response = make_shared<ListPrivateKeysResponse>();

    bool empty_key_list = true;
    if (list_keys_status->key_id_set.size() == 0) {
      ExecutionResult failure_result;
      for (int i = 0; i < endpoint_count; ++i) {
        // Only propagate the first error out.
        if (!list_keys_status->result_list[uri_index]
                 .fetch_result.Successful()) {
          empty_key_list = false;
          failure_result =
              list_keys_status->result_list[uri_index].fetch_result;
          break;
        }
      }

      list_private_keys_context.response =
          make_shared<ListPrivateKeysResponse>();
      if (empty_key_list) {
        SCP_WARNING_CONTEXT(kPrivateKeyClientProvider,
                            list_private_keys_context, "Get empty key list.");
        list_private_keys_context.result = SuccessExecutionResult();
      } else {
        SCP_ERROR_CONTEXT(kPrivateKeyClientProvider, list_private_keys_context,
                          failure_result, "Failed to fetch keys.");
        list_private_keys_context.result = failure_result;
      }
      list_private_keys_context.Finish();
      return;
    }

    for (auto& key_id : list_keys_status->key_id_set) {
      bool all_splits_are_available = true;
      auto single_party_key = PrivateKeyClientUtils::ExtractSinglePartyKey(
          list_keys_status->result_list, key_id);
      vector<DecryptResult> success_decrypt_result;
      if (single_party_key.has_value()) {
        // If contains single party key, ignore the fetch and decrypt results.
        success_decrypt_result.emplace_back(move(single_party_key.value()));
      } else {
        // If doesn't contain single party key, validate every fetch and
        // decrypt results.
        auto execution_result = PrivateKeyClientUtils::ExtractAnyFailure(
            list_keys_status->result_list, key_id);
        if (!execution_result.Successful()) {
          list_private_keys_context.result = execution_result;
          SCP_ERROR_CONTEXT(
              kPrivateKeyClientProvider, list_private_keys_context,
              list_private_keys_context.result,
              "Failed to fetch the private key for key ID: %s", key_id.c_str());
          list_private_keys_context.Finish();
          return;
        }
        // Key splits returned from each endpoint should match the endpoint
        // count.
        for (int i = 0; i < endpoint_count; ++i) {
          DecryptResult decrypt_result;
          auto find_result =
              list_keys_status->result_list[i].decrypt_result_key_id_map.Find(
                  key_id, decrypt_result);
          if (!find_result.Successful() ||
              decrypt_result.encryption_key.key_data.size() != endpoint_count) {
            if (!find_result.Successful()) {
              SCP_ERROR_CONTEXT(kPrivateKeyClientProvider,
                                list_private_keys_context, find_result,
                                "Cannot find the decrypt result for key %s.",
                                key_id.c_str());
            } else {
              SCP_ERROR_CONTEXT(kPrivateKeyClientProvider,
                                list_private_keys_context, find_result,
                                "The key data size is %ld and the endpoint "
                                "count is %ld for key %s.",
                                decrypt_result.encryption_key.key_data.size(),
                                endpoint_count, key_id.c_str());
            }
            if (list_keys_status->listing_method == ListingMethod::kByKeyId) {
              list_private_keys_context.result = FailureExecutionResult(
                  SC_PRIVATE_KEY_CLIENT_PROVIDER_UNMATCHED_ENDPOINTS_SPLITS);
              SCP_ERROR_CONTEXT(
                  kPrivateKeyClientProvider, list_private_keys_context,
                  list_private_keys_context.result,
                  "Unmatched endpoints number and private key split "
                  "data size for key ID %s.",
                  key_id.c_str());
              list_private_keys_context.Finish();
              return;
            } else {
              // For ListByAge, the key split count might not match the
              // endpoint count if the key is corrupted. We just log it
              // instead of error out.
              SCP_WARNING_CONTEXT(
                  kPrivateKeyClientProvider, list_private_keys_context,
                  "Unmatched endpoints number and private key split "
                  "data size for key ID %s.",
                  key_id.c_str());
              all_splits_are_available = false;
              break;
            }
          }
          success_decrypt_result.emplace_back(decrypt_result);
        }
      }
      if (all_splits_are_available) {
        auto private_key_or =
            PrivateKeyClientUtils::ConstructPrivateKey(success_decrypt_result);
        if (!private_key_or.Successful()) {
          list_private_keys_context.result = private_key_or.result();
          SCP_ERROR_CONTEXT(kPrivateKeyClientProvider,
                            list_private_keys_context,
                            list_private_keys_context.result,
                            "Failed to construct private key.");
          list_private_keys_context.Finish();
          return;
        }
        SCP_DEBUG_CONTEXT(kPrivateKeyClientProvider, list_private_keys_context,
                          "Successfully obtained private key for key %s.",
                          private_key_or->key_id().c_str());
        *list_private_keys_context.response->add_private_keys() =
            move(*private_key_or);
      }
    }

    list_private_keys_context.result = SuccessExecutionResult();
    list_private_keys_context.Finish();
  }
}

static shared_ptr<KmsClientOptions> CreateKmsClientOptions(
    shared_ptr<PrivateKeyClientOptions> options) {
  auto kms_client_options = make_shared<KmsClientOptions>();
  // Client cache config
  kms_client_options->enable_gcp_kms_client_cache =
      options->enable_gcp_kms_client_cache;
  kms_client_options->gcp_kms_client_cache_lifetime =
      options->gcp_kms_client_cache_lifetime;

  // RPC retry config
  kms_client_options->enable_gcp_kms_client_retries =
      options->enable_gcp_kms_client_retries;
  kms_client_options->gcp_kms_client_retry_initial_interval =
      options->gcp_kms_client_retry_initial_interval;
  kms_client_options->gcp_kms_client_retry_total_retries =
      options->gcp_kms_client_retry_total_retries;

  return kms_client_options;
}

shared_ptr<PrivateKeyClientProviderInterface>
PrivateKeyClientProviderFactory::Create(
    const shared_ptr<PrivateKeyClientOptions>& options,
    const shared_ptr<core::HttpClientInterface>& http_client,
    const shared_ptr<RoleCredentialsProviderInterface>&
        role_credentials_provider,
    const shared_ptr<AuthTokenProviderInterface>& auth_token_provider,
    const shared_ptr<core::AsyncExecutorInterface>& io_async_executor,
    const shared_ptr<core::AsyncExecutorInterface>& cpu_async_executor) {
  auto kms_client_options = CreateKmsClientOptions(options);
  auto kms_client_provider = KmsClientProviderFactory::Create(
      std::move(kms_client_options), role_credentials_provider,
      io_async_executor, cpu_async_executor);
  auto private_key_fetcher = PrivateKeyFetcherProviderFactory::Create(
      http_client, role_credentials_provider, auth_token_provider);

  return make_shared<PrivateKeyClientProvider>(
      options, http_client, private_key_fetcher, kms_client_provider);
}

}  // namespace google::scp::cpio::client_providers
