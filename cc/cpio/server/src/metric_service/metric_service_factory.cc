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

#include "metric_service_factory.h"

#include <memory>
#include <utility>

#include "cc/core/common/uuid/src/uuid.h"
#include "cpio/server/src/service_utils.h"
#include "public/cpio/proto/metric_service/v1/configuration_keys.pb.h"

using google::cmrt::sdk::metric_service::v1::ClientConfigurationKeys;
using google::cmrt::sdk::metric_service::v1::ClientConfigurationKeys_Name;
using google::scp::core::ExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::common::kZeroUuid;
using std::make_shared;
using std::shared_ptr;

namespace {
constexpr char kMetricServiceFactory[] = "MetricServiceFactory";
}  // namespace

namespace google::scp::cpio {
shared_ptr<InstanceServiceFactoryOptions>
MetricServiceFactory::CreateInstanceServiceFactoryOptions() noexcept {
  auto instance_service_factory_options =
      make_shared<InstanceServiceFactoryOptions>();
  instance_service_factory_options
      ->cpu_async_executor_thread_count_config_label =
      ClientConfigurationKeys_Name(
          ClientConfigurationKeys::CMRT_METRIC_CLIENT_CPU_THREAD_COUNT);
  instance_service_factory_options->cpu_async_executor_queue_cap_config_label =
      ClientConfigurationKeys_Name(
          ClientConfigurationKeys::
              CMRT_METRIC_CLIENT_CPU_THREAD_POOL_QUEUE_CAP);
  instance_service_factory_options
      ->io_async_executor_thread_count_config_label =
      ClientConfigurationKeys_Name(
          ClientConfigurationKeys::CMRT_METRIC_CLIENT_IO_THREAD_COUNT);
  instance_service_factory_options->io_async_executor_queue_cap_config_label =
      ClientConfigurationKeys_Name(
          ClientConfigurationKeys::CMRT_METRIC_CLIENT_IO_THREAD_POOL_QUEUE_CAP);
  return instance_service_factory_options;
}

shared_ptr<MetricClientOptions>
MetricServiceFactory::CreateMetricClientOptions() noexcept {
  auto options = make_shared<MetricClientOptions>();
  TryReadConfigBool(
      config_provider_,
      ClientConfigurationKeys_Name(
          ClientConfigurationKeys::CMRT_METRIC_CLIENT_ENABLE_BATCH_RECORDING),
      options->enable_batch_recording);
  TryReadConfigString(config_provider_,
                      ClientConfigurationKeys_Name(
                          ClientConfigurationKeys::
                              CMRT_METRIC_CLIENT_NAMESPACE_FOR_BATCH_RECORDING),
                      options->namespace_for_batch_recording);
  int32_t batch_recording_time_duration_in_ms;
  if (TryReadConfigInt(
          config_provider_,
          ClientConfigurationKeys_Name(
              ClientConfigurationKeys::
                  CMRT_METRIC_CLIENT_BATCH_RECORDING_TIME_DURATION_IN_MS),
          batch_recording_time_duration_in_ms)
          .Successful()) {
    options->batch_recording_time_duration =
        std::chrono::milliseconds(batch_recording_time_duration_in_ms);
  }
  return options;
}

ExecutionResult MetricServiceFactory::Init() noexcept {
  instance_service_factory_options_ = CreateInstanceServiceFactoryOptions();
  instance_service_factory_ = CreateInstanceServiceFactory();
  auto execution_result = instance_service_factory_->Init();
  if (!execution_result.Successful()) {
    SCP_ERROR(kMetricServiceFactory, kZeroUuid, execution_result,
              "Failed to init InstanceServiceFactory");
    return execution_result;
  }

  instance_client_ = instance_service_factory_->CreateInstanceClient();
  execution_result = instance_client_->Init();
  if (!execution_result.Successful()) {
    SCP_ERROR(kMetricServiceFactory, kZeroUuid, execution_result,
              "Failed to init InstanceClient");
    return execution_result;
  }

  return SuccessExecutionResult();
}

ExecutionResult MetricServiceFactory::Run() noexcept {
  auto execution_result = instance_service_factory_->Run();
  if (!execution_result.Successful()) {
    SCP_ERROR(kMetricServiceFactory, kZeroUuid, execution_result,
              "Failed to run InstanceServiceFactory");
    return execution_result;
  }

  execution_result = instance_client_->Run();
  if (!execution_result.Successful()) {
    SCP_ERROR(kMetricServiceFactory, kZeroUuid, execution_result,
              "Failed to run InstanceClient");
    return execution_result;
  }

  return SuccessExecutionResult();
}

ExecutionResult MetricServiceFactory::Stop() noexcept {
  auto execution_result = instance_client_->Stop();
  if (!execution_result.Successful()) {
    SCP_ERROR(kMetricServiceFactory, kZeroUuid, execution_result,
              "Failed to stop InstanceClient");
    return execution_result;
  }

  execution_result = instance_service_factory_->Stop();
  if (!execution_result.Successful()) {
    SCP_ERROR(kMetricServiceFactory, kZeroUuid, execution_result,
              "Failed to stop InstanceServiceFactory");
    return execution_result;
  }

  return SuccessExecutionResult();
}
}  // namespace google::scp::cpio
