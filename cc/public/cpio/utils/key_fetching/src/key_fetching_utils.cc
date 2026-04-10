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

#include "key_fetching_utils.h"

#include <limits>
#include <string>
#include <vector>

#include "google/protobuf/util/time_util.h"
#include "public/cpio/proto/private_key_service/v1/private_key_service.pb.h"

using google::protobuf::util::TimeUtil;
using std::string;
using std::vector;

namespace google::scp::cpio {

vector<Key> ExtractKeys(const google::protobuf::RepeatedPtrField<
                        google::cmrt::sdk::private_key_service::v1::PrivateKey>&
                            private_keys) noexcept {
  vector<Key> output_keys;
  for (auto& fetched_key : private_keys) {
    Key key;
    key.key_id = fetched_key.key_id();
    key.creation_timestamp =
        TimeUtil::TimestampToNanoseconds(fetched_key.creation_time());
    // Sets the expiration time to uint64_t max when the key's expiration_time
    // is set to Unix time 0. Within the Key System (KS), this value signifies
    // that the key has no expiration time.
    key.expiration_timestamp =
        fetched_key.expiration_time().seconds()
            ? static_cast<uint64_t>(TimeUtil::TimestampToNanoseconds(
                  fetched_key.expiration_time()))
            : std::numeric_limits<uint64_t>::max();
    key.activation_timestamp =
        fetched_key.activation_time().seconds()
            ? static_cast<uint64_t>(TimeUtil::TimestampToNanoseconds(
                  fetched_key.activation_time()))
            : 0;
    if (!fetched_key.private_key().empty()) {
      key.private_key = fetched_key.private_key();
    }
    if (!fetched_key.public_key().empty()) {
      key.public_key = fetched_key.public_key();
    }
    output_keys.push_back(key);
  }
  return output_keys;
}

}  // namespace google::scp::cpio
