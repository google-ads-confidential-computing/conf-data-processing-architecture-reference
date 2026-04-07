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

#include <string>
#include <string_view>

#include "absl/container/flat_hash_map.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/utils/dual_writing_metric_client/interface/dual_writing_metric_client_interface.h"
#include "public/cpio/utils/key_fetching/proto/encryption_key_prefetch_config.pb.h"

namespace google::scp::cpio {

class EncryptionKeyPrefetchConfigValidator {
 public:
  EncryptionKeyPrefetchConfigValidator(
      DualWritingMetricClientInterface& metric_client,
      std::string_view metric_namespace);

  /**
   * Parses and validates the text proto, then converts it to a map.
   */
  core::ExecutionResultOr<absl::flat_hash_map<
      std::string,
      google::cmrt::sdk::v1::EncryptionKeyPrefetchConfig::KeysetPrefetchConfig>>
  ParseValidateAndBuildMap(std::string_view text_proto);

 private:
  core::ExecutionResult ValidateKeysetConfig(
      const google::cmrt::sdk::v1::EncryptionKeyPrefetchConfig::
          KeysetPrefetchConfig& keyset_config);
  /**
   * Parses the incoming text_proto into the expected config proto. If parsing
   * fails, returns an error.
   */
  core::ExecutionResultOr<google::cmrt::sdk::v1::EncryptionKeyPrefetchConfig>
  ParseAndValidate(std::string_view text_proto);

  DualWritingMetricClientInterface& metric_client_;
  std::string metric_namespace_;
};

}  // namespace google::scp::cpio
