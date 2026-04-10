// Copyright 2026 Google LLC
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

#include "public/cpio/utils/key_fetching/src/key_fetching_utils.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <limits>
#include <string>
#include <vector>

#include "google/protobuf/util/time_util.h"

#include "core/test/utils/proto_test_utils.h"
#include "core/test/utils/timestamp_test_utils.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/proto/private_key_service/v1/private_key_service.pb.h"

using google::cmrt::sdk::private_key_service::v1::ListPrivateKeysResponse;
using google::cmrt::sdk::private_key_service::v1::PrivateKey;
using google::protobuf::util::TimeUtil;
using google::scp::core::test::MakeProtoTimestamp;
using google::scp::core::test::SubstituteAndParseTextToProto;
using std::string;
using std::vector;
using testing::FieldsAre;
using testing::UnorderedElementsAre;

namespace google::scp::cpio::test {

TEST(KeyFetchingUtilsTest, ExtractKeysSuccessfully) {
  auto created_time_1 = MakeProtoTimestamp(1, 0);
  auto expiry_time_1 = MakeProtoTimestamp(2, 0);
  auto created_time_2 = MakeProtoTimestamp(2, 0);
  auto expiry_time_2 = MakeProtoTimestamp(0, 0);
  auto active_time_2 = MakeProtoTimestamp(3, 0);
  auto response = SubstituteAndParseTextToProto<ListPrivateKeysResponse>(
      R"-(
        private_keys {
            key_id: "key_1"
            private_key: "private_key_1"
            public_key: "public_key_1"
            creation_time {
                $0
            }
            expiration_time {
                $1
            }

        }
        private_keys {
            key_id: "key_2"
            private_key: "private_key_2"
            public_key: "public_key_2"
            creation_time {
                $2
            }
            expiration_time {
                $3
            }
            activation_time {
                $4
            }
        }
      )-",
      created_time_1.DebugString(), expiry_time_1.DebugString(),
      created_time_2.DebugString(), expiry_time_2.DebugString(),
      active_time_2.DebugString());

  auto keys = ExtractKeys(response.private_keys());

  EXPECT_THAT(
      keys,
      UnorderedElementsAre(
          FieldsAre(1000000000, 2000000000, 0, "private_key_1", "public_key_1",
                    "key_1"),
          FieldsAre(2000000000, std::numeric_limits<uint64_t>::max(),
                    3000000000, "private_key_2", "public_key_2", "key_2")));
}

TEST(KeyFetchingUtilsTest, ExtractEmptyKeyList) {
  ListPrivateKeysResponse response;
  auto keys = ExtractKeys(response.private_keys());
  EXPECT_TRUE(keys.empty());
}

}  // namespace google::scp::cpio::test
