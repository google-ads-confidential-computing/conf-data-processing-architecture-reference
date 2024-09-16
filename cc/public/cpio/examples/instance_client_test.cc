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

#include <chrono>
#include <iostream>
#include <memory>
#include <string>

#include "core/test/utils/conditional_wait.h"
#include "public/core/interface/errors.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/interface/cpio.h"
#include "public/cpio/interface/instance_client/instance_client_interface.h"
#include "public/cpio/interface/type_def.h"
#include "public/cpio/proto/instance_service/v1/instance_service.pb.h"

using google::cmrt::sdk::instance_service::v1::
    GetCurrentInstanceResourceNameRequest;
using google::cmrt::sdk::instance_service::v1::
    GetCurrentInstanceResourceNameResponse;
using google::cmrt::sdk::instance_service::v1::GetTagsByResourceNameRequest;
using google::cmrt::sdk::instance_service::v1::GetTagsByResourceNameResponse;
using google::scp::core::AsyncContext;
using google::scp::core::ExecutionResult;
using google::scp::core::GetErrorMessage;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::test::WaitUntil;
using google::scp::cpio::Cpio;
using google::scp::cpio::CpioOptions;
using google::scp::cpio::InstanceClientFactory;
using google::scp::cpio::InstanceClientInterface;
using google::scp::cpio::InstanceClientOptions;
using google::scp::cpio::LogOption;
using std::atomic;
using std::make_shared;
using std::make_unique;
using std::map;
using std::move;
using std::shared_ptr;
using std::string;
using std::unique_ptr;
using std::placeholders::_1;
using std::placeholders::_2;

unique_ptr<InstanceClientInterface> instance_client;

void GetTagsByResourceNameCallback(
    atomic<bool>& finished,
    AsyncContext<GetTagsByResourceNameRequest, GetTagsByResourceNameResponse>&
        context) {
  if (!context.result.Successful()) {
    std::cout << "GetTagsByResourceName failed: "
              << GetErrorMessage(context.result.status_code) << std::endl;
  } else {
    std::cout << "GetTagsByResourceName succeeded, and the tags are: "
              << std::endl;
    for (const auto& tag : context.response->tags()) {
      std::cout << tag.first << " : " << tag.second << std::endl;
    }
  }
  finished = true;
}

void GetCurrentInstanceResourceNameCallback(
    atomic<bool>& finished,
    AsyncContext<GetCurrentInstanceResourceNameRequest,
                 GetCurrentInstanceResourceNameResponse>& context) {
  if (!context.result.Successful()) {
    std::cout << "Hpke encrypt failure!"
              << GetErrorMessage(context.result.status_code) << std::endl;
    return;
  }

  std::cout << "GetCurrentInstanceResourceName succeeded, and the "
               "instance resource name is: "
            << context.response->instance_resource_name() << std::endl;

  auto get_tags_request = make_shared<GetTagsByResourceNameRequest>();
  get_tags_request->set_resource_name(
      context.response->instance_resource_name());
  auto get_tags_context =
      AsyncContext<GetTagsByResourceNameRequest, GetTagsByResourceNameResponse>(
          move(get_tags_request),
          bind(GetTagsByResourceNameCallback, std::ref(finished), _1));
  instance_client->GetTagsByResourceName(get_tags_context);
}

int main(int argc, char* argv[]) {
  CpioOptions cpio_options;
  cpio_options.log_option = LogOption::kConsoleLog;
  auto result = Cpio::InitCpio(cpio_options);
  if (!result.Successful()) {
    std::cout << "Failed to initialize CPIO: "
              << GetErrorMessage(result.status_code) << std::endl;
  }

  InstanceClientOptions instance_client_options;
  instance_client =
      InstanceClientFactory::Create(move(instance_client_options));
  result = instance_client->Init();
  if (!result.Successful()) {
    std::cout << "Cannot init instance client!"
              << GetErrorMessage(result.status_code) << std::endl;
    return 0;
  }
  result = instance_client->Run();
  if (!result.Successful()) {
    std::cout << "Cannot run instance client!"
              << GetErrorMessage(result.status_code) << std::endl;
    return 0;
  }

  atomic<bool> finished = false;
  auto get_current_instance_resource_name_context =
      AsyncContext<GetCurrentInstanceResourceNameRequest,
                   GetCurrentInstanceResourceNameResponse>(
          make_shared<GetCurrentInstanceResourceNameRequest>(),
          bind(GetCurrentInstanceResourceNameCallback, std::ref(finished), _1));

  instance_client->GetCurrentInstanceResourceName(
      get_current_instance_resource_name_context);
  WaitUntil([&finished]() { return finished.load(); },
            std::chrono::milliseconds(3000));

  result = instance_client->Stop();
  if (!result.Successful()) {
    std::cout << "Cannot stop instance client!"
              << GetErrorMessage(result.status_code) << std::endl;
  }

  result = Cpio::ShutdownCpio(cpio_options);
  if (!result.Successful()) {
    std::cout << "Failed to shutdown CPIO: "
              << GetErrorMessage(result.status_code) << std::endl;
  }
}
