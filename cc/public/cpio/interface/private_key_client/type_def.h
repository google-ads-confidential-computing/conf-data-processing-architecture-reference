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

#ifndef SCP_CPIO_INTERFACE_PRIVATE_KEY_CLIENT_TYPE_DEF_H_
#define SCP_CPIO_INTERFACE_PRIVATE_KEY_CLIENT_TYPE_DEF_H_

#include <chrono>
#include <string>
#include <vector>

#include "public/cpio/interface/type_def.h"

namespace google::scp::cpio {
/// Configuration for PrivateKeyClient.
struct PrivateKeyClientOptions {
  virtual ~PrivateKeyClientOptions() = default;

  bool enable_gcp_kms_client_cache = false;
  std::chrono::seconds gcp_kms_client_cache_lifetime =
      std::chrono::seconds(3600 * 24);  // 1 day
};
}  // namespace google::scp::cpio

#endif  // SCP_CPIO_INTERFACE_PRIVATE_KEY_CLIENT_TYPE_DEF_H_
