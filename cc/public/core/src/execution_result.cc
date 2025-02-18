
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

#include "public/core/interface/execution_result.h"

#include <map>

#include "public/core/interface/execution_result.pb.h"

using std::map;

namespace google::scp::core {
map<core::proto::ExecutionStatus, ExecutionStatus> ReverseMap(
    const map<ExecutionStatus, core::proto::ExecutionStatus>& m) {
  map<core::proto::ExecutionStatus, ExecutionStatus> r;
  for (const auto& kv : m) {
    r[kv.second] = kv.first;
  }
  return r;
}

const map<ExecutionStatus, core::proto::ExecutionStatus>
    kExecutionStatusToProtoMap = {
        {ExecutionStatus::Success,
         core::proto::ExecutionStatus::EXECUTION_STATUS_SUCCESS},
        {ExecutionStatus::Failure,
         core::proto::ExecutionStatus::EXECUTION_STATUS_FAILURE},
        {ExecutionStatus::Retry,
         core::proto::ExecutionStatus::EXECUTION_STATUS_RETRY}};

const std::map<core::proto::ExecutionStatus, ExecutionStatus>
    kProtoToExecutionStatusMap = ReverseMap(kExecutionStatusToProtoMap);

core::proto::ExecutionStatus ToStatusProto(ExecutionStatus& status) {
  return kExecutionStatusToProtoMap.at(status);
}

core::proto::ExecutionResult ExecutionResult::ToProto() {
  core::proto::ExecutionResult result_proto;
  result_proto.set_status(ToStatusProto(status));
  result_proto.set_status_code(status_code);
  return result_proto;
}

ExecutionResult::ExecutionResult(
    const core::proto::ExecutionResult result_proto) {
  auto mapped_status = ExecutionStatus::Failure;
  // Handle proto status UNKNOWN
  if (kProtoToExecutionStatusMap.find(result_proto.status()) !=
      kProtoToExecutionStatusMap.end()) {
    mapped_status = kProtoToExecutionStatusMap.at(result_proto.status());
  }
  status = mapped_status;
  status_code = result_proto.status_code();
}
}  // namespace google::scp::core
