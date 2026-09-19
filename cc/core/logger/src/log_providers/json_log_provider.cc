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
#include "json_log_provider.h"

#include <cstdio>
#include <iostream>
#include <string>
#include <string_view>
#include <vector>

#include <nlohmann/json.hpp>

#include "absl/strings/str_split.h"
#include "core/common/time_provider/src/time_provider.h"
#include "core/common/uuid/src/uuid.h"
#include "core/logger/src/log_utils.h"

namespace google::scp::core::logger {

namespace {

using google::scp::core::common::TimeProvider;
using google::scp::core::common::Uuid;

const char* ToGcpSeverity(const LogLevel& level) noexcept {
  switch (level) {
    case LogLevel::kEmergency:
      return "EMERGENCY";
    case LogLevel::kAlert:
      return "ALERT";
    case LogLevel::kCritical:
      return "CRITICAL";
    case LogLevel::kError:
      return "ERROR";
    case LogLevel::kWarning:
      return "WARNING";
    case LogLevel::kInfo:
      return "INFO";
    case LogLevel::kDebug:
      return "DEBUG";
    case LogLevel::kNone:
    default:
      return "DEFAULT";
  }
}

nlohmann::json Now() {
  constexpr int64_t kNanosPerSecond = 1'000'000'000;
  auto now_ns = TimeProvider::GetWallTimestampInNanosecondsAsClockTicks();
  return {{"seconds", now_ns / kNanosPerSecond},
          {"nanos", static_cast<int>(now_ns % kNanosPerSecond)}};
}

nlohmann::json ParseLocation(std::string_view location) {
  std::vector<std::string_view> parts = absl::StrSplit(location, ':');
  nlohmann::json source_location = nlohmann::json::object();
  if (parts.size() >= 3) {
    source_location["file"] = parts[0];
    source_location["function"] = parts[1];
    source_location["line"] = parts[2];
  } else {
    source_location["file"] = location;
  }
  return source_location;
}

}  // namespace

JsonLogProvider::JsonLogProvider(std::ostream& out) : out_(out) {}

ExecutionResult JsonLogProvider::Init() noexcept {
  return SuccessExecutionResult();
}

ExecutionResult JsonLogProvider::Run() noexcept {
  return SuccessExecutionResult();
}

ExecutionResult JsonLogProvider::Stop() noexcept {
  return SuccessExecutionResult();
}

void JsonLogProvider::Log(const LogLevel& level, const Uuid& correlation_id,
                          const Uuid& parent_activity_id,
                          const Uuid& activity_id,
                          const std::string_view& component_name,
                          const std::string_view& machine_name,
                          const std::string_view& cluster_name,
                          const std::string_view& location,
                          const std::string_view& message,
                          va_list args) noexcept {
  nlohmann::json log_entry = {
      {"severity", ToGcpSeverity(level)},
      {"message", FormatMessage(message, args)},
      {"timestamp", Now()},
      {"sourceLocation", ParseLocation(location)},
      {"component_name", component_name},
      {"machine_name", machine_name},
      {"cluster_name", cluster_name},
      {"correlation_id", common::ToString(correlation_id)},
      {"parent_activity_id", common::ToString(parent_activity_id)},
      {"activity_id", common::ToString(activity_id)},
  };

  // Dump as compact single-line JSON with replacement char on invalid UTF-8
  out_ << log_entry.dump(/*indent=*/-1, /*indent_char=*/' ',
                         /*ensure_ascii=*/false,
                         nlohmann::json::error_handler_t::replace)
       << std::endl;
}

}  // namespace google::scp::core::logger

