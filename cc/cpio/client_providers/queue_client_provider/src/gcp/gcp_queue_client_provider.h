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

#pragma once

#include <memory>
#include <string>
#include <utility>

#include <google/pubsub/v1/pubsub.grpc.pb.h>

#include "core/common/operation_dispatcher/src/operation_dispatcher.h"
#include "core/interface/async_context.h"
#include "core/interface/async_executor_interface.h"
#include "cpio/client_providers/interface/instance_client_provider_interface.h"
#include "cpio/client_providers/interface/queue_client_provider_interface.h"
#include "public/core/interface/execution_result.h"
#include "public/cpio/proto/queue_service/v1/queue_service.pb.h"

#include "error_codes.h"

namespace google::scp::cpio::client_providers {
static constexpr char kPubSubEndpointUri[] = "pubsub.googleapis.com";
static constexpr int kRetryStrategyDelayInMs = 250;
static constexpr int kRetryStrategyMaxRetries = 12;

class GcpPubSubStubFactory;

/*! @copydoc QueueClientProviderInterface
 */
class GcpQueueClientProvider : public QueueClientProviderInterface {
 public:
  virtual ~GcpQueueClientProvider() = default;

  explicit GcpQueueClientProvider(
      const std::shared_ptr<QueueClientOptions>& queue_client_options,
      const std::shared_ptr<InstanceClientProviderInterface>&
          instance_client_provider,
      const std::shared_ptr<core::AsyncExecutorInterface>& cpu_async_executor,
      const std::shared_ptr<core::AsyncExecutorInterface>& io_async_executor,
      const std::shared_ptr<GcpPubSubStubFactory>& pubsub_stub_factory =
          std::make_shared<GcpPubSubStubFactory>())
      : queue_client_options_(queue_client_options),
        instance_client_provider_(instance_client_provider),
        cpu_async_executor_(cpu_async_executor),
        io_async_executor_(io_async_executor),
        operation_dispatcher_(
            io_async_executor,
            core::common::RetryStrategy(core::common::RetryStrategyOptions{
                core::common::RetryStrategyType::Exponential,
                queue_client_options
                    ? static_cast<core::TimeDuration>(
                          queue_client_options->retry_interval.count())
                    : kRetryStrategyDelayInMs,
                queue_client_options ? queue_client_options->max_retry_count
                                     : kRetryStrategyMaxRetries})),
        pubsub_stub_factory_(pubsub_stub_factory) {}

  core::ExecutionResult Init() noexcept override;

  core::ExecutionResult Run() noexcept override;

  core::ExecutionResult Stop() noexcept override;

  void EnqueueMessage(
      core::AsyncContext<cmrt::sdk::queue_service::v1::EnqueueMessageRequest,
                         cmrt::sdk::queue_service::v1::EnqueueMessageResponse>&
          enqueue_message_context) noexcept override;

  void GetTopMessage(
      core::AsyncContext<cmrt::sdk::queue_service::v1::GetTopMessageRequest,
                         cmrt::sdk::queue_service::v1::GetTopMessageResponse>&
          get_top_message_context) noexcept override;

  void UpdateMessageVisibilityTimeout(
      core::AsyncContext<
          cmrt::sdk::queue_service::v1::UpdateMessageVisibilityTimeoutRequest,
          cmrt::sdk::queue_service::v1::UpdateMessageVisibilityTimeoutResponse>&
          update_message_visibility_timeout_context) noexcept override;

  void DeleteMessage(
      core::AsyncContext<cmrt::sdk::queue_service::v1::DeleteMessageRequest,
                         cmrt::sdk::queue_service::v1::DeleteMessageResponse>&
          delete_message_context) noexcept override;

 private:
  /**
   * @brief Publish a message to the PubSub gRPC service.
   * @param enqueue_message_request the enqueue message request.
   * @return cmrt::sdk::queue_service::v1::EnqueueMessageResponse result of the
   * operation.
   *
   */
  core::ExecutionResultOr<cmrt::sdk::queue_service::v1::EnqueueMessageResponse>
  PublishMessage(const cmrt::sdk::queue_service::v1::EnqueueMessageRequest&
                     enqueue_message_request) noexcept;

  /**
   * @brief Pull the top messsage from the PubSub gRPC service.
   * @return cmrt::sdk::queue_service::v1::GetTopMessageResponse result of the
   * operation.
   */
  core::ExecutionResultOr<cmrt::sdk::queue_service::v1::GetTopMessageResponse>
  PullMessage() noexcept;

  /**
   * @brief Modify ack deadline for a message from the PubSub gRPC service.
   * @param update_message_visibility_timeout_request the update message
   * visibility timeout request.
   * @return
   * cmrt::sdk::queue_service::v1::UpdateMessageVisibilityTimeoutResponse result
   * of the operation.
   */
  core::ExecutionResultOr<
      cmrt::sdk::queue_service::v1::UpdateMessageVisibilityTimeoutResponse>
  ModifyMessageAckDeadline(
      const cmrt::sdk::queue_service::v1::UpdateMessageVisibilityTimeoutRequest&
          update_message_visibility_timeout_request) noexcept;

  /**
   * @brief Acknowledge a message from the PubSub from the PubSub gRPC service.
   * @param delete_message_request the delete message request.
   * @return cmrt::sdk::queue_service::v1::DeleteMessageResponse result of the
   * operation.
   */
  core::ExecutionResultOr<cmrt::sdk::queue_service::v1::DeleteMessageResponse>
  AcknowledgeMessage(const cmrt::sdk::queue_service::v1::DeleteMessageRequest&
                         delete_message_request) noexcept;

  /// The configuration for queue client.
  std::shared_ptr<QueueClientOptions> queue_client_options_;

  /// The instance client provider.
  std::shared_ptr<InstanceClientProviderInterface> instance_client_provider_;

  /// The instance of the async executor.
  const std::shared_ptr<core::AsyncExecutorInterface> cpu_async_executor_,
      io_async_executor_;

  /// Operation distpatcher.
  core::common::OperationDispatcher operation_dispatcher_;

  /// Project ID of current instance.
  std::string project_id_;

  /// Topic name of current instance. Format is
  /// projects/{project_id}/topics/{topic_name}.
  std::string topic_name_;

  /// Subscription name of current instance.Format is
  /// projects/{project_id}/subscriptions/{subscription_name}.
  std::string subscription_name_;

  /// An Instance of the GCP Pub/Sub stub factory.
  std::shared_ptr<GcpPubSubStubFactory> pubsub_stub_factory_;

  /// An Instance of the GCP Publisher stub.
  std::shared_ptr<google::pubsub::v1::Publisher::StubInterface> publisher_stub_;

  /// An Instance of the GCP Subscriber stub.
  std::shared_ptr<google::pubsub::v1::Subscriber::StubInterface>
      subscriber_stub_;
};

/// Provides GCP Pub/Sub stubs.
class GcpPubSubStubFactory {
 public:
  /**
   * @brief Creates Publisher Stub.
   *
   * @param options the QueueClientOptions.
   * @return std::shared_ptr<google::pubsub::v1::Publisher::Stub> the creation
   * result.
   */
  virtual std::shared_ptr<google::pubsub::v1::Publisher::StubInterface>
  CreatePublisherStub(
      const std::shared_ptr<QueueClientOptions>& options) noexcept;
  /**
   * @brief Creates Subscriber Stub.
   *
   * @param options the QueueClientOptions.
   * @return std::shared_ptr<google::pubsub::v1::Subscriber::Stub> the creation
   * result.
   */
  virtual std::shared_ptr<google::pubsub::v1::Subscriber::StubInterface>
  CreateSubscriberStub(
      const std::shared_ptr<QueueClientOptions>& options) noexcept;

  virtual ~GcpPubSubStubFactory() = default;

 private:
  /**
   * @brief Gets Pub/Sub Channel.
   *
   * @param options the QueueClientOptions.
   * @return std::shared_ptr<grpc::Channel> the creation result.
   */
  virtual std::shared_ptr<grpc::Channel> GetPubSubChannel(
      const std::shared_ptr<QueueClientOptions>& options) noexcept;

 protected:
  // An Instance of the gRPC Channel for Publisher and Subscriber Stubs.
  std::shared_ptr<grpc::Channel> channel_;
};

}  // namespace google::scp::cpio::client_providers
