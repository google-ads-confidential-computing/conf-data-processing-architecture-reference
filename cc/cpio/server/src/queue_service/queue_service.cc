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
#include <execinfo.h>
#include <unistd.h>

#include <csignal>
#include <functional>
#include <string>

#include "core/async_executor/src/async_executor.h"
#include "core/interface/async_executor_interface.h"
#include "core/interface/http_client_interface.h"
#include "core/interface/service_interface.h"
#include "core/logger/src/logger.h"
#include "cpio/client_providers/interface/cloud_initializer_interface.h"
#include "cpio/client_providers/interface/instance_client_provider_interface.h"
#include "cpio/client_providers/interface/queue_client_provider_interface.h"
#include "cpio/server/interface/unix_socket_addresses.h"
#include "cpio/server/src/service_utils.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/proto/queue_service/v1/configuration_keys.pb.h"
#include "public/cpio/proto/queue_service/v1/queue_service.grpc.pb.h"
#include "public/cpio/proto/queue_service/v1/queue_service.pb.h"

#if defined(AWS_SERVER)
#include "cpio/server/src/queue_service/aws/aws_queue_service_factory.h"
#elif defined(GCP_SERVER)
#include "cpio/server/src/queue_service/gcp/gcp_queue_service_factory.h"
#elif defined(TEST_AWS_SERVER)
#include "cpio/server/src/queue_service/test_aws/test_aws_queue_service_factory.h"
#elif defined(TEST_GCP_SERVER)
#include "cpio/server/src/queue_service/test_gcp/test_gcp_queue_service_factory.h"
#else
#error "Must provide [TEST_]AWS_SERVER or [TEST_]GCP_SERVER"
#endif

using google::cmrt::sdk::queue_service::v1::ClientConfigurationKeys;
using google::cmrt::sdk::queue_service::v1::ClientConfigurationKeys_Name;
using google::cmrt::sdk::queue_service::v1::DeleteMessageRequest;
using google::cmrt::sdk::queue_service::v1::DeleteMessageResponse;
using google::cmrt::sdk::queue_service::v1::EnqueueMessageRequest;
using google::cmrt::sdk::queue_service::v1::EnqueueMessageResponse;
using google::cmrt::sdk::queue_service::v1::GetTopMessageRequest;
using google::cmrt::sdk::queue_service::v1::GetTopMessageResponse;
using google::cmrt::sdk::queue_service::v1::QueueService;
using google::cmrt::sdk::queue_service::v1::
    UpdateMessageVisibilityTimeoutRequest;
using google::cmrt::sdk::queue_service::v1::
    UpdateMessageVisibilityTimeoutResponse;
using google::scp::core::AsyncContext;
using google::scp::core::ConfigProviderInterface;
using google::scp::core::common::kZeroUuid;
using google::scp::cpio::ExecuteNetworkCall2;
using google::scp::cpio::Init;
using google::scp::cpio::InitializeCloud;
using google::scp::cpio::kQueueServiceAddress;
using google::scp::cpio::QueueServiceFactoryInterface;
using google::scp::cpio::ReadConfigInt;
using google::scp::cpio::ReadConfigString;
using google::scp::cpio::Run;
using google::scp::cpio::RunConfigProvider;
using google::scp::cpio::RunLogger;
using google::scp::cpio::RunServer;
using google::scp::cpio::ShutdownCloud;
using google::scp::cpio::SignalSegmentationHandler;
using google::scp::cpio::Stop;
using google::scp::cpio::StopLogger;
using google::scp::cpio::TryReadConfigInt;
using google::scp::cpio::client_providers::CloudInitializerInterface;
using google::scp::cpio::client_providers::InstanceClientProviderFactory;
using google::scp::cpio::client_providers::InstanceClientProviderInterface;
using google::scp::cpio::client_providers::QueueClientProviderInterface;
using std::bind;
using std::make_shared;
using std::shared_ptr;
using std::string;
using std::placeholders::_1;

namespace {
constexpr int32_t kDefaultNumCompletionQueues = 2;
constexpr int32_t kDefaultMinPollers = 2;
constexpr int32_t kDefaultMaxPollers = 5;

constexpr char kQueueService[] = "QueueService";
constexpr char kConfigProviderName[] = "config_provider";
constexpr char kCloudInitializerName[] = "cloud_initializer";
constexpr char kQueueClientName[] = "queue_client";
constexpr char kServiceFactoryName[] = "service_factory";
}  // namespace

shared_ptr<CloudInitializerInterface> cloud_initializer;
shared_ptr<ConfigProviderInterface> config_provider;
shared_ptr<QueueServiceFactoryInterface> service_factory;
shared_ptr<QueueClientProviderInterface> queue_client;

class QueueServiceImpl : public QueueService::CallbackService {
 public:
  grpc::ServerUnaryReactor* EnqueueMessage(
      grpc::CallbackServerContext* server_context,
      const EnqueueMessageRequest* request,
      EnqueueMessageResponse* response) override {
    return ExecuteNetworkCall2<EnqueueMessageRequest, EnqueueMessageResponse>(
        server_context, request, response,
        bind(&QueueClientProviderInterface::EnqueueMessage, queue_client, _1));
  }

  grpc::ServerUnaryReactor* GetTopMessage(
      grpc::CallbackServerContext* server_context,
      const GetTopMessageRequest* request,
      GetTopMessageResponse* response) override {
    return ExecuteNetworkCall2<GetTopMessageRequest, GetTopMessageResponse>(
        server_context, request, response,
        bind(&QueueClientProviderInterface::GetTopMessage, queue_client, _1));
  }

  grpc::ServerUnaryReactor* UpdateMessageVisibilityTimeout(
      grpc::CallbackServerContext* server_context,
      const UpdateMessageVisibilityTimeoutRequest* request,
      UpdateMessageVisibilityTimeoutResponse* response) override {
    return ExecuteNetworkCall2<UpdateMessageVisibilityTimeoutRequest,
                               UpdateMessageVisibilityTimeoutResponse>(
        server_context, request, response,
        bind(&QueueClientProviderInterface::UpdateMessageVisibilityTimeout,
             queue_client, _1));
  }

  grpc::ServerUnaryReactor* DeleteMessage(
      grpc::CallbackServerContext* server_context,
      const DeleteMessageRequest* request,
      DeleteMessageResponse* response) override {
    return ExecuteNetworkCall2<DeleteMessageRequest, DeleteMessageResponse>(
        server_context, request, response,
        bind(&QueueClientProviderInterface::DeleteMessage, queue_client, _1));
  }
};

static void SignalHandler(int signum) {
  Stop(queue_client, kQueueClientName);
  Stop(service_factory, kServiceFactoryName);
  ShutdownCloud(cloud_initializer, kCloudInitializerName);
  StopLogger();
  Stop(config_provider, kConfigProviderName);
  SignalSegmentationHandler(signum);
  exit(signum);
}

void RunClients();

int main(int argc, char* argv[]) {
  signal(SIGINT, SignalHandler);
  signal(SIGTERM, SignalHandler);
  signal(SIGPIPE, SIG_IGN);

  RunConfigProvider(config_provider, kConfigProviderName);

  RunLogger(config_provider);

  InitializeCloud(cloud_initializer, kCloudInitializerName);

  RunClients();

  auto num_completion_queues = kDefaultNumCompletionQueues;
  TryReadConfigInt(
      config_provider,
      ClientConfigurationKeys_Name(
          ClientConfigurationKeys::CMRT_QUEUE_CLIENT_COMPLETION_QUEUE_COUNT),
      num_completion_queues);
  auto min_pollers = kDefaultMinPollers;
  TryReadConfigInt(config_provider,
                   ClientConfigurationKeys_Name(
                       ClientConfigurationKeys::CMRT_QUEUE_CLIENT_MIN_POLLERS),
                   min_pollers);
  auto max_pollers = kDefaultMaxPollers;
  TryReadConfigInt(config_provider,
                   ClientConfigurationKeys_Name(
                       ClientConfigurationKeys::CMRT_QUEUE_CLIENT_MAX_POLLERS),
                   max_pollers);

  QueueServiceImpl service;
  RunServer<QueueServiceImpl>(service, kQueueServiceAddress,
                              num_completion_queues, min_pollers, max_pollers);

  return 0;
}

void RunClients() {
#if defined(AWS_SERVER)
  SCP_INFO(kQueueService, kZeroUuid, "Start AWS Queue Server");
  service_factory =
      make_shared<google::scp::cpio::AwsQueueServiceFactory>(config_provider);
#elif defined(GCP_SERVER)
  SCP_INFO(kQueueService, kZeroUuid, "Start GCP Queue Server");
  service_factory =
      make_shared<google::scp::cpio::GcpQueueServiceFactory>(config_provider);
#elif defined(TEST_AWS_SERVER)
  SCP_INFO(kQueueService, kZeroUuid, "Start test AWS Queue Server");
  service_factory = make_shared<google::scp::cpio::TestAwsQueueServiceFactory>(
      config_provider);
#elif defined(TEST_GCP_SERVER)
  SCP_INFO(kQueueService, kZeroUuid, "Start test GCP Queue Server");
  service_factory = make_shared<google::scp::cpio::TestGcpQueueServiceFactory>(
      config_provider);
#endif
  Init(service_factory, kServiceFactoryName);
  Run(service_factory, kServiceFactoryName);

  queue_client = service_factory->CreateQueueClient();
  Init(queue_client, kQueueClientName);
  Run(queue_client, kQueueClientName);
}
