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
#pragma once

#include "core/interface/errors.h"
#include "public/cpio/interface/error_codes.h"

namespace google::scp::core::errors {
/// Registers component code as 0x0225 for GCP KMS client provider.
REGISTER_COMPONENT_CODE(SC_GCP_KMS_CLIENT_PROVIDER, 0x0225)

DEFINE_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_KEY_ARN_NOT_FOUND,
                  SC_GCP_KMS_CLIENT_PROVIDER, 0x0001, "Cannot find the key arn",
                  HttpStatusCode::BAD_REQUEST)

DEFINE_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_CIPHERTEXT_NOT_FOUND,
                  SC_GCP_KMS_CLIENT_PROVIDER, 0x0002, "Cannot find cipher text",
                  HttpStatusCode::BAD_REQUEST)

DEFINE_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_DECRYPTION_FAILED,
                  SC_GCP_KMS_CLIENT_PROVIDER, 0x0004, "Cannot decrpyt",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)

DEFINE_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_BASE64_DECODING_FAILED,
                  SC_GCP_KMS_CLIENT_PROVIDER, 0x0005,
                  "Cannot decode the data using base64",
                  HttpStatusCode::INTERNAL_SERVER_ERROR)

DEFINE_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_CLOUD_UNAVAILABLE,
                  SC_GCP_KMS_CLIENT_PROVIDER, 0x0006,
                  "Cloud KMS in unavailable",
                  HttpStatusCode::SERVICE_UNAVAILABLE)

DEFINE_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_WIP_AUTH_FAILED,
                  SC_GCP_KMS_CLIENT_PROVIDER, 0x0007,
                  "Failed to authenticate with the workload identity pool "
                  "provider",
                  HttpStatusCode::UNAUTHORIZED)

DEFINE_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_INVALID_KEK,
                  SC_GCP_KMS_CLIENT_PROVIDER, 0x0008,
                  "The KEK does not exist or is disabled",
                  HttpStatusCode::BAD_REQUEST)

DEFINE_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_KEK_PERMISSION_DENIED,
                  SC_GCP_KMS_CLIENT_PROVIDER, 0x0009,
                  "Permission denied to decrypt with the KEK",
                  HttpStatusCode::FORBIDDEN)

DEFINE_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_DEK_DECRYPTION_ERROR,
                  SC_GCP_KMS_CLIENT_PROVIDER, 0x000A,
                  "The encrypted DEK cannot be decrypted with the KEK",
                  HttpStatusCode::BAD_REQUEST)

MAP_TO_PUBLIC_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_KEY_ARN_NOT_FOUND,
                         SC_CPIO_INVALID_ARGUMENT)
MAP_TO_PUBLIC_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_CIPHERTEXT_NOT_FOUND,
                         SC_CPIO_INVALID_ARGUMENT)
MAP_TO_PUBLIC_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_DECRYPTION_FAILED,
                         SC_CPIO_INTERNAL_ERROR)
MAP_TO_PUBLIC_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_BASE64_DECODING_FAILED,
                         SC_CPIO_INVALID_ARGUMENT)
MAP_TO_PUBLIC_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_CLOUD_UNAVAILABLE,
                         SC_CPIO_SERVICE_UNAVAILABLE)
MAP_TO_PUBLIC_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_WIP_AUTH_FAILED,
                         SC_CPIO_FEDERATED_AUTH_FAILED)
MAP_TO_PUBLIC_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_INVALID_KEK,
                         SC_CPIO_INVALID_KEK)
MAP_TO_PUBLIC_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_KEK_PERMISSION_DENIED,
                         SC_CPIO_KEK_PERMISSION_DENIED)
MAP_TO_PUBLIC_ERROR_CODE(SC_GCP_KMS_CLIENT_PROVIDER_DEK_DECRYPTION_ERROR,
                         SC_CPIO_DEK_DECRYPTION_ERROR)

}  // namespace google::scp::core::errors
