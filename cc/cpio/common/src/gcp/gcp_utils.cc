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

#include "gcp_utils.h"

#include <grpcpp/support/status.h>

#include <nlohmann/json.hpp>

#include "absl/strings/str_format.h"
#include "core/common/global_logger/src/global_logger.h"
#include "google/cloud/status.h"

#include "error_codes.h"

using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using google::scp::core::errors::SC_GCP_ABORTED;
using google::scp::core::errors::SC_GCP_ALREADY_EXISTS;
using google::scp::core::errors::SC_GCP_CANCELLED;
using google::scp::core::errors::SC_GCP_DATA_LOSS;
using google::scp::core::errors::SC_GCP_DEADLINE_EXCEEDED;
using google::scp::core::errors::SC_GCP_FAILED_PRECONDITION;
using google::scp::core::errors::SC_GCP_INTERNAL_SERVICE_ERROR;
using google::scp::core::errors::SC_GCP_INVALID_ARGUMENT;
using google::scp::core::errors::SC_GCP_NOT_FOUND;
using google::scp::core::errors::SC_GCP_OUT_OF_RANGE;
using google::scp::core::errors::SC_GCP_PERMISSION_DENIED;
using google::scp::core::errors::SC_GCP_RESOURCE_EXHAUSTED;
using google::scp::core::errors::SC_GCP_UNAUTHENTICATED;
using google::scp::core::errors::SC_GCP_UNAVAILABLE;
using google::scp::core::errors::SC_GCP_UNIMPLEMENTED;
using google::scp::core::errors::SC_GCP_UNKNOWN;
using std::string;

namespace {
// Filename for logging errors
static constexpr char kGcpErrorConverter[] = "GcpErrorConverter";
constexpr char kAudience[] = "//iam.googleapis.com/%s";

google::scp::core::StatusCode ToScpErrorCode(
    google::cloud::StatusCode cloud_status_code) noexcept {
  switch (cloud_status_code) {
    case google::cloud::StatusCode::kOk:
      return SC_OK;
    case google::cloud::StatusCode::kNotFound:
      return SC_GCP_NOT_FOUND;
    case google::cloud::StatusCode::kInvalidArgument:
      return SC_GCP_INVALID_ARGUMENT;
    case google::cloud::StatusCode::kDeadlineExceeded:
      return SC_GCP_DEADLINE_EXCEEDED;
    case google::cloud::StatusCode::kAlreadyExists:
      return SC_GCP_ALREADY_EXISTS;
    case google::cloud::StatusCode::kUnimplemented:
      return SC_GCP_UNIMPLEMENTED;
    case google::cloud::StatusCode::kOutOfRange:
      return SC_GCP_OUT_OF_RANGE;
    case google::cloud::StatusCode::kCancelled:
      return SC_GCP_CANCELLED;
    case google::cloud::StatusCode::kAborted:
      return SC_GCP_ABORTED;
    case google::cloud::StatusCode::kUnavailable:
      return SC_GCP_UNAVAILABLE;
    case google::cloud::StatusCode::kUnauthenticated:
      return SC_GCP_UNAUTHENTICATED;
    case google::cloud::StatusCode::kPermissionDenied:
      return SC_GCP_PERMISSION_DENIED;
    case google::cloud::StatusCode::kDataLoss:
      return SC_GCP_DATA_LOSS;
    case google::cloud::StatusCode::kFailedPrecondition:
      return SC_GCP_FAILED_PRECONDITION;
    case google::cloud::StatusCode::kResourceExhausted:
      return SC_GCP_RESOURCE_EXHAUSTED;
    case google::cloud::StatusCode::kInternal:
      return SC_GCP_INTERNAL_SERVICE_ERROR;
    default:
      return SC_GCP_UNKNOWN;
  }
}
}  // namespace

namespace google::scp::cpio::common {

ExecutionResult GcpUtils::GcpErrorConverter(cloud::Status status) noexcept {
  cloud::StatusCode cloud_status_code = status.code();
  auto scp_status_code = ToScpErrorCode(cloud_status_code);
  if (scp_status_code == SC_OK) {
    return SuccessExecutionResult();
  }
  ExecutionResult failure = FailureExecutionResult(scp_status_code);

  SCP_ERROR(kGcpErrorConverter, kZeroUuid, failure,
            "GCP cloud service error: code is %s (%d) and error message is %s.",
            cloud::StatusCodeToString(cloud_status_code).c_str(),
            cloud_status_code, status.message().c_str());
  return failure;
}

ExecutionResult GcpUtils::GcpErrorConverter(grpc::Status status) noexcept {
  // grpc::StatusCode is a 1:1 mapping to google::cloud::StatusCode.
  cloud::StatusCode cloud_status_code =
      static_cast<cloud::StatusCode>(status.error_code());
  auto scp_status_code = ToScpErrorCode(cloud_status_code);
  if (scp_status_code == SC_OK) {
    return SuccessExecutionResult();
  }
  ExecutionResult failure = FailureExecutionResult(scp_status_code);

  SCP_ERROR(kGcpErrorConverter, kZeroUuid, failure,
            "GCP gRPC service error: code is %s (%d) and error message is %s.",
            cloud::StatusCodeToString(cloud_status_code).c_str(),
            cloud_status_code, status.error_message().c_str());
  return failure;
}

string GcpUtils::CreateAttestedCredentials(
    const string& wip_provider) noexcept {
  return nlohmann::json{
      {"type", "external_account"},
      {"audience", absl::StrFormat(kAudience, wip_provider)},
      {"subject_token_type", "urn:ietf:params:oauth:token-type:jwt"},
      {"token_url", "https://sts.googleapis.com/v1/token"},
      {"credential_source",
       nlohmann::json{
           {"file",
            "/run/container_launcher/attestation_verifier_claims_token"}}},
  }
      .dump();
}
}  // namespace google::scp::cpio::common
