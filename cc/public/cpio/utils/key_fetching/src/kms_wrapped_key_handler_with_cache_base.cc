/*
 * Copyright 2026 Google LLC
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

#include "kms_wrapped_key_handler_with_cache_base.h"

#include <chrono>
#include <memory>
#include <string>
#include <utility>

#include "core/common/time_provider/src/time_provider.h"
#include "core/interface/async_executor_interface.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/kms_client/kms_client_interface.h"
#include "public/cpio/utils/dual_writing_metric_client/interface/dual_writing_metric_client_interface.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"

using google::cmrt::sdk::kms_service::v1::DecryptRequest;
using google::cmrt::sdk::v1::AwsWrappedKey;
using google::cmrt::sdk::v1::GcpWrappedKey;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResultOr;
using google::scp::core::common::TimeProvider;
using google::scp::cpio::DualWritingMetricClientInterface;
using google::scp::cpio::KeyFetchingType;
using google::scp::cpio::KmsClientInterface;
using google::scp::cpio::PushKeyFetchingLatencyMetric;
using std::shared_ptr;
using std::string;
using std::chrono::duration_cast;
using std::chrono::milliseconds;
using std::chrono::nanoseconds;

namespace google::scp::cpio {

template <typename WrappedKeyType>
KmsWrappedKeyHandlerWithCacheBase<WrappedKeyType>::
    KmsWrappedKeyHandlerWithCacheBase(
        shared_ptr<AsyncExecutorInterface>& async_executor,
        KmsClientInterface& kms_client,
        WrappedKeyHandlerOptions wrapped_key_handler_options,
        DualWritingMetricClientInterface& metric_client)
    : WrappedKeyHandlerWithCacheBase<WrappedKeyType>(
          async_executor, wrapped_key_handler_options, metric_client),
      kms_client_(kms_client) {}

template <typename WrappedKeyType>
ExecutionResultOr<string>
KmsWrappedKeyHandlerWithCacheBase<WrappedKeyType>::DecryptDek(
    const WrappedKeyType& wrapped_key) noexcept {
  auto decrypt_request_or = CreateDecryptRequest(wrapped_key);
  if (!decrypt_request_or.Successful()) {
    return decrypt_request_or.result();
  }
  DecryptRequest decrypt_request = std::move(*decrypt_request_or);

  auto decryption_start_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();

  // Call KMS to decrypt DEK
  auto response_or = kms_client_.DecryptSync(decrypt_request);

  auto decryption_end_time_in_ns =
      TimeProvider::GetSteadyTimestampInNanosecondsAsClockTicks();
  auto latency_in_millis =
      duration_cast<milliseconds>(
          nanoseconds(decryption_end_time_in_ns - decryption_start_time_in_ns))
          .count();
  // Log new wrapped key decryption latency metric using OpenTelemetry
  PushKeyFetchingLatencyMetric(
      this->metric_client_, this->GetKeyType(), KeyFetchingType::kOnDemand,
      /*keyset_name=*/kDummyLabelValue, latency_in_millis);

  if (!response_or.Successful()) {
    return response_or.result();
  }
  return response_or->plaintext();
}

template class KmsWrappedKeyHandlerWithCacheBase<GcpWrappedKey>;
template class KmsWrappedKeyHandlerWithCacheBase<AwsWrappedKey>;

}  // namespace google::scp::cpio
