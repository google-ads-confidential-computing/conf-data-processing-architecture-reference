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
#include "core/logger/src/log_providers/json_log_provider.h"

#include <sstream>

#include <gtest/gtest.h>
#include <nlohmann/json.hpp>

#include "core/common/uuid/src/uuid.h"
#include "core/interface/logger_interface.h"

using google::scp::core::LogLevel;
using google::scp::core::common::Uuid;

namespace google::scp::core::logger {

namespace {

void LogWithArgs(JsonLogProvider& provider, const LogLevel& level,
                 const Uuid& correlation_id, const Uuid& parent_activity_id,
                 const Uuid& activity_id, std::string_view component_name,
                 std::string_view machine_name, std::string_view cluster_name,
                 std::string_view location, const char* message, ...) {
  va_list args;
  va_start(args, message);
  provider.Log(level, correlation_id, parent_activity_id, activity_id,
               component_name, machine_name, cluster_name, location, message,
               args);
  va_end(args);
}

}  // namespace

TEST(JsonLogProviderTest, EmitsJsonLog) {
  std::ostringstream buf;
  JsonLogProvider provider(buf);
  auto correlation_id = Uuid::GenerateUuid();
  auto parent_activity_id = Uuid::GenerateUuid();
  auto activity_id = Uuid::GenerateUuid();
  LogWithArgs(provider, LogLevel::kInfo, correlation_id, parent_activity_id,
              activity_id, "component_name", "machine_name", "cluster_name",
              "file:function:1", "Test message: %s", "arg1");

  auto entry = nlohmann::json::parse(buf.str());
  EXPECT_EQ(entry["severity"], "INFO");
  EXPECT_EQ(entry["message"], "Test message: arg1");
  EXPECT_EQ(entry["component_name"], "component_name");
  EXPECT_EQ(entry["machine_name"], "machine_name");
  EXPECT_EQ(entry["cluster_name"], "cluster_name");
  EXPECT_EQ(entry["correlation_id"], common::ToString(correlation_id));
  EXPECT_EQ(entry["parent_activity_id"],
            common::ToString(parent_activity_id));
  EXPECT_EQ(entry["activity_id"], common::ToString(activity_id));
  EXPECT_EQ(entry["sourceLocation"]["file"], "file");
  EXPECT_EQ(entry["sourceLocation"]["function"], "function");
  EXPECT_EQ(entry["sourceLocation"]["line"], "1");
  EXPECT_TRUE(entry["timestamp"]["seconds"].is_number());
  EXPECT_TRUE(entry["timestamp"]["nanos"].is_number());
}

};  // namespace google::scp::core::logger

