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

#include <vector>

#include "public/cpio/proto/private_key_service/v1/private_key_service.pb.h"
#include "public/cpio/utils/key_fetching/interface/key_fetcher_with_cache_interface.h"

namespace google::scp::cpio {

/**
 * @brief Extract keys from given ListPrivateKeysResponse.
 *
 * @param private_keys the given private_keys from
 * ListActiveEncryptionKeysResponse or ListPrivateKeysResponse
 * @return keys list
 */
std::vector<Key> ExtractKeys(
    const google::protobuf::RepeatedPtrField<
        google::cmrt::sdk::private_key_service::v1::PrivateKey>&
        private_keys) noexcept;

}  // namespace google::scp::cpio
