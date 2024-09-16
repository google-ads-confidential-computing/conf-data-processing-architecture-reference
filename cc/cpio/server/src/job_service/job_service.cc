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
#include "core/interface/service_interface.h"
#include "cpio/client_providers/interface/cloud_initializer_interface.h"
#include "cpio/client_providers/interface/job_client_provider_interface.h"
#include "cpio/server/interface/job_service/job_service_factory_interface.h"
#include "cpio/server/interface/unix_socket_addresses.h"
#include "cpio/server/src/service_utils.h"
#include "public/cpio/proto/job_service/v1/configuration_keys.pb.h"
#include "public/cpio/proto/job_service/v1/job_service.grpc.pb.h"
#include "public/cpio/proto/job_service/v1/job_service.pb.h"

#if defined(AWS_SERVER)
#include "cpio/server/src/job_service/aws/aws_job_service_factory.h"
#elif defined(GCP_SERVER)
#include "cpio/server/src/job_service/gcp/gcp_job_service_factory.h"
#elif defined(TEST_AWS_SERVER)
#include "cpio/server/src/job_service/test_aws/test_aws_job_service_factory.h"
#elif defined(TEST_GCP_SERVER)
#include "cpio/server/src/job_service/test_gcp/test_gcp_job_service_factory.h"
#else
#error "Must provide [TEST_]AWS_SERVER or [TEST_]GCP_SERVER"
#endif

using google::cmrt::sdk::job_service::v1::ClientConfigurationKeys;
using google::cmrt::sdk::job_service::v1::ClientConfigurationKeys_Name;
using google::cmrt::sdk::job_service::v1::DeleteOrphanedJobMessageRequest;
using google::cmrt::sdk::job_service::v1::DeleteOrphanedJobMessageResponse;
using google::cmrt::sdk::job_service::v1::GetJobByIdRequest;
using google::cmrt::sdk::job_service::v1::GetJobByIdResponse;
using google::cmrt::sdk::job_service::v1::GetNextJobRequest;
using google::cmrt::sdk::job_service::v1::GetNextJobResponse;
using google::cmrt::sdk::job_service::v1::Job;
using google::cmrt::sdk::job_service::v1::JobService;
using google::cmrt::sdk::job_service::v1::JobStatus;
using google::cmrt::sdk::job_service::v1::PutJobRequest;
using google::cmrt::sdk::job_service::v1::PutJobResponse;
using google::cmrt::sdk::job_service::v1::UpdateJobBodyRequest;
using google::cmrt::sdk::job_service::v1::UpdateJobBodyResponse;
using google::cmrt::sdk::job_service::v1::UpdateJobStatusRequest;
using google::cmrt::sdk::job_service::v1::UpdateJobStatusResponse;
using google::cmrt::sdk::job_service::v1::UpdateJobVisibilityTimeoutRequest;
using google::cmrt::sdk::job_service::v1::UpdateJobVisibilityTimeoutResponse;
using google::scp::core::AsyncContext;
using google::scp::core::ConfigProviderInterface;
using google::scp::core::common::kZeroUuid;
using google::scp::cpio::ExecuteNetworkCall2;
using google::scp::cpio::Init;
using google::scp::cpio::InitializeCloud;
using google::scp::cpio::JobServiceFactoryInterface;
using google::scp::cpio::kJobServiceAddress;
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
using google::scp::cpio::client_providers::JobClientProviderInterface;
using std::bind;
using std::make_shared;
using std::shared_ptr;
using std::string;
using std::placeholders::_1;

namespace {
constexpr int32_t kDefaultNumCompletionQueues = 2;
constexpr int32_t kDefaultMinPollers = 2;
constexpr int32_t kDefaultMaxPollers = 5;

constexpr char kJobService[] = "JobService";
constexpr char kConfigProviderName[] = "config_provider";
constexpr char kCloudInitializerName[] = "cloud_initializer";
constexpr char kJobClientName[] = "job_client";
constexpr char kServiceFactoryName[] = "service_factory";
}  // namespace

shared_ptr<CloudInitializerInterface> cloud_initializer;
shared_ptr<ConfigProviderInterface> config_provider;
shared_ptr<JobServiceFactoryInterface> service_factory;
shared_ptr<JobClientProviderInterface> job_client;

class JobServiceImpl : public JobService::CallbackService {
 public:
  grpc::ServerUnaryReactor* PutJob(grpc::CallbackServerContext* server_context,
                                   const PutJobRequest* request,
                                   PutJobResponse* response) override {
    return ExecuteNetworkCall2<PutJobRequest, PutJobResponse>(
        server_context, request, response,
        bind(&JobClientProviderInterface::PutJob, job_client, _1));
  }

  grpc::ServerUnaryReactor* GetNextJob(
      grpc::CallbackServerContext* server_context,
      const GetNextJobRequest* request, GetNextJobResponse* response) override {
    return ExecuteNetworkCall2<GetNextJobRequest, GetNextJobResponse>(
        server_context, request, response,
        bind(&JobClientProviderInterface::GetNextJob, job_client, _1));
  }

  grpc::ServerUnaryReactor* GetJobById(
      grpc::CallbackServerContext* server_context,
      const GetJobByIdRequest* request, GetJobByIdResponse* response) override {
    return ExecuteNetworkCall2<GetJobByIdRequest, GetJobByIdResponse>(
        server_context, request, response,
        bind(&JobClientProviderInterface::GetJobById, job_client, _1));
  }

  grpc::ServerUnaryReactor* UpdateJobBody(
      grpc::CallbackServerContext* server_context,
      const UpdateJobBodyRequest* request,
      UpdateJobBodyResponse* response) override {
    return ExecuteNetworkCall2<UpdateJobBodyRequest, UpdateJobBodyResponse>(
        server_context, request, response,
        bind(&JobClientProviderInterface::UpdateJobBody, job_client, _1));
  }

  grpc::ServerUnaryReactor* UpdateJobStatus(
      grpc::CallbackServerContext* server_context,
      const UpdateJobStatusRequest* request,
      UpdateJobStatusResponse* response) override {
    return ExecuteNetworkCall2<UpdateJobStatusRequest, UpdateJobStatusResponse>(
        server_context, request, response,
        bind(&JobClientProviderInterface::UpdateJobStatus, job_client, _1));
  }

  grpc::ServerUnaryReactor* UpdateJobVisibilityTimeout(
      grpc::CallbackServerContext* server_context,
      const UpdateJobVisibilityTimeoutRequest* request,
      UpdateJobVisibilityTimeoutResponse* response) override {
    return ExecuteNetworkCall2<UpdateJobVisibilityTimeoutRequest,
                               UpdateJobVisibilityTimeoutResponse>(
        server_context, request, response,
        bind(&JobClientProviderInterface::UpdateJobVisibilityTimeout,
             job_client, _1));
  }

  grpc::ServerUnaryReactor* DeleteOrphanedJobMessage(
      grpc::CallbackServerContext* server_context,
      const DeleteOrphanedJobMessageRequest* request,
      DeleteOrphanedJobMessageResponse* response) override {
    return ExecuteNetworkCall2<DeleteOrphanedJobMessageRequest,
                               DeleteOrphanedJobMessageResponse>(
        server_context, request, response,
        bind(&JobClientProviderInterface::DeleteOrphanedJobMessage, job_client,
             _1));
  }
};

static void SignalHandler(int signum) {
  Stop(job_client, kJobClientName);
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
          ClientConfigurationKeys::CMRT_JOB_CLIENT_COMPLETION_QUEUE_COUNT),
      num_completion_queues);
  auto min_pollers = kDefaultMinPollers;
  TryReadConfigInt(config_provider,
                   ClientConfigurationKeys_Name(
                       ClientConfigurationKeys::CMRT_JOB_CLIENT_MIN_POLLERS),
                   min_pollers);
  auto max_pollers = kDefaultMaxPollers;
  TryReadConfigInt(config_provider,
                   ClientConfigurationKeys_Name(
                       ClientConfigurationKeys::CMRT_JOB_CLIENT_MAX_POLLERS),
                   max_pollers);

  JobServiceImpl service;
  RunServer<JobServiceImpl>(service, kJobServiceAddress, num_completion_queues,
                            min_pollers, max_pollers);

  return 0;
}

void RunClients() {
#if defined(AWS_SERVER)
  SCP_INFO(kJobService, kZeroUuid, "Start AWS Job Server");
  service_factory =
      make_shared<google::scp::cpio::AwsJobServiceFactory>(config_provider);
#elif defined(GCP_SERVER)
  SCP_INFO(kJobService, kZeroUuid, "Start GCP Job Server");
  service_factory =
      make_shared<google::scp::cpio::GcpJobServiceFactory>(config_provider);
#elif defined(TEST_AWS_SERVER)
  SCP_INFO(kJobService, kZeroUuid, "Start test AWS Job Server");
  service_factory =
      make_shared<google::scp::cpio::TestAwsJobServiceFactory>(config_provider);
#elif defined(TEST_GCP_SERVER)
  SCP_INFO(kJobService, kZeroUuid, "Start test GCP Job Server");
  service_factory =
      make_shared<google::scp::cpio::TestGcpJobServiceFactory>(config_provider);
#endif

  Init(service_factory, kServiceFactoryName);
  Run(service_factory, kServiceFactoryName);

  job_client = service_factory->CreateJobClient();
  Init(job_client, kJobClientName);
  Run(job_client, kJobClientName);
}
