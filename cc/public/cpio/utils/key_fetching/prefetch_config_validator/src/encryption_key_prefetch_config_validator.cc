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

#include "encryption_key_prefetch_config_validator.h"

#include <string>
#include <string_view>
#include <utility>

#include "absl/container/flat_hash_map.h"
#include "absl/container/flat_hash_set.h"
#include "absl/status/status.h"
#include "absl/status/statusor.h"
#include "absl/strings/str_cat.h"
#include "core/common/global_logger/src/global_logger.h"
#include "core/common/uuid/src/uuid.h"
#include "google/protobuf/duration.pb.h"
#include "google/protobuf/text_format.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/cpio/utils/key_fetching/src/key_fetching_metric_utils.h"

#include "error_codes.h"

using google::cmrt::sdk::v1::EncryptionKeyPrefetchConfig;
using google::protobuf::TextFormat;
using google::protobuf::io::ArrayInputStream;
using google::scp::core::ExecutionResult;
using google::scp::core::ExecutionResultOr;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::cpio::PrefetchConfigValidationErrorType;
using google::scp::cpio::PushPrefetchConfigValidationErrorRateMetric;
using std::string;
using std::string_view;

namespace google::scp::cpio {

constexpr char kEncryptionKeyPrefetchConfigValidatorComponentName[] =
    "EncryptionKeyPrefetchConfigValidator";

EncryptionKeyPrefetchConfigValidator::EncryptionKeyPrefetchConfigValidator(
    DualWritingMetricClientInterface& metric_client,
    string_view metric_namespace)
    : metric_client_(metric_client), metric_namespace_(metric_namespace) {}

ExecutionResult EncryptionKeyPrefetchConfigValidator::ValidateKeysetConfig(
    const EncryptionKeyPrefetchConfig::KeysetPrefetchConfig& keyset_config) {
  bool has_key_ids = keyset_config.key_ids_size() > 0;
  bool has_prefetch_duration = keyset_config.has_prefetch_duration();

  if (!has_key_ids && !has_prefetch_duration) {
    auto result =
        FailureExecutionResult(SC_CPIO_PREFETCH_CONFIG_MISSING_VALUES);
    PushPrefetchConfigValidationErrorRateMetric(
        metric_client_, keyset_config.key_namespace(),
        PrefetchConfigValidationErrorType::kMissingValues);
    return result;
  }

  if (has_prefetch_duration) {
    const auto& duration = keyset_config.prefetch_duration();
    if (!(duration.seconds() >= 0 && duration.nanos() >= 0)) {
      auto result =
          FailureExecutionResult(SC_CPIO_PREFETCH_CONFIG_INVALID_DURATION);
      PushPrefetchConfigValidationErrorRateMetric(
          metric_client_, keyset_config.key_namespace(),
          PrefetchConfigValidationErrorType::kInvalidDuration);
      return result;
    }
  }
  if (has_key_ids) {
    for (const auto& key_id : keyset_config.key_ids()) {
      if (key_id.empty()) {
        auto result =
            FailureExecutionResult(SC_CPIO_PREFETCH_CONFIG_INVALID_KEY_IDS);
        PushPrefetchConfigValidationErrorRateMetric(
            metric_client_, keyset_config.key_namespace(),
            PrefetchConfigValidationErrorType::kEmptyKeyId);
        return result;
      }
    }
  }
  return SuccessExecutionResult();
}

ExecutionResultOr<EncryptionKeyPrefetchConfig>
EncryptionKeyPrefetchConfigValidator::ParseAndValidate(string_view text_proto) {
  EncryptionKeyPrefetchConfig config;
  ArrayInputStream is(text_proto.data(), text_proto.length());
  if (!TextFormat::Parse(&is, &config)) {
    auto result = FailureExecutionResult(SC_CPIO_PREFETCH_CONFIG_PARSE_ERROR);
    PushPrefetchConfigValidationErrorRateMetric(
        metric_client_, "", PrefetchConfigValidationErrorType::kParseFailure);
    return result;
  }
  for (const auto& keyset_config : config.keyset_prefetch_configs()) {
    if (keyset_config.key_namespace().empty()) {
      auto result = FailureExecutionResult(SC_CPIO_INVALID_PREFETCH_CONFIG);
      PushPrefetchConfigValidationErrorRateMetric(
          metric_client_, keyset_config.key_namespace(),
          PrefetchConfigValidationErrorType::kMissingKeysetNamespace);
      return result;
    }
    RETURN_IF_FAILURE(ValidateKeysetConfig(keyset_config));
  }
  return config;
}

ExecutionResultOr<absl::flat_hash_map<
    std::string, EncryptionKeyPrefetchConfig::KeysetPrefetchConfig>>
EncryptionKeyPrefetchConfigValidator::ParseValidateAndBuildMap(
    string_view text_proto) {
  auto config_or = ParseAndValidate(text_proto);
  if (!config_or.Successful()) {
    return config_or.result();
  }

  const EncryptionKeyPrefetchConfig& config = *config_or;
  absl::flat_hash_map<std::string,
                      EncryptionKeyPrefetchConfig::KeysetPrefetchConfig>
      config_map;

  for (const auto& keyset_config : config.keyset_prefetch_configs()) {
    auto [_, inserted] =
        config_map.try_emplace(keyset_config.key_namespace(), keyset_config);
    if (!inserted) {
      auto result =
          FailureExecutionResult(SC_CPIO_PREFETCH_CONFIG_DUPLICATE_NAMESPACE);
      SCP_ERROR(kEncryptionKeyPrefetchConfigValidatorComponentName, kZeroUuid,
                result,
                "Duplicate key_namespace '%s' found while building map.",
                keyset_config.key_namespace().c_str());
      PushPrefetchConfigValidationErrorRateMetric(
          metric_client_, keyset_config.key_namespace(),
          PrefetchConfigValidationErrorType::kDuplicateKeysetNamespace);
      return result;
    }
  }
  return config_map;
}

}  // namespace google::scp::cpio
