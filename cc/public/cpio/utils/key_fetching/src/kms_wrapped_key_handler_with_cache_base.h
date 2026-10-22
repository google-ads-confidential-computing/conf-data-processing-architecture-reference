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

#pragma once

#include <memory>
#include <string>

#include "core/interface/async_executor_interface.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/kms_client/kms_client_interface.h"
#include "public/cpio/utils/dual_writing_metric_client/interface/dual_writing_metric_client_interface.h"

#include "wrapped_key_handler_with_cache_base.h"

namespace google::scp::cpio {

template <typename WrappedKeyType>
class KmsWrappedKeyHandlerWithCacheBase
    : public WrappedKeyHandlerWithCacheBase<WrappedKeyType> {
 public:
  KmsWrappedKeyHandlerWithCacheBase(
      std::shared_ptr<google::scp::core::AsyncExecutorInterface>&
          async_executor,
      KmsClientInterface& kms_client,
      WrappedKeyHandlerOptions wrapped_key_handler_options,
      DualWritingMetricClientInterface& metric_client);

 protected:
  virtual std::string GetKekPrefix() noexcept = 0;

  virtual google::scp::core::ExecutionResultOr<
      google::cmrt::sdk::kms_service::v1::DecryptRequest>
  CreateDecryptRequest(const WrappedKeyType& wrapped_key) noexcept = 0;

  google::scp::core::ExecutionResultOr<std::string> DecryptDek(
      const WrappedKeyType& wrapped_key) noexcept override;

  KmsClientInterface& kms_client_;
};

}  // namespace google::scp::cpio
