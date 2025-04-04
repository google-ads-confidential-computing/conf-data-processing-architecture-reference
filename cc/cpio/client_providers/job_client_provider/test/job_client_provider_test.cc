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

#include "cc/cpio/client_providers/job_client_provider/src/job_client_provider.h"

#include <chrono>
#include <memory>
#include <string>

#include <nlohmann/json.hpp>

#include "core/async_executor/mock/mock_async_executor.h"
#include "core/async_executor/src/async_executor.h"
#include "core/test/utils/conditional_wait.h"
#include "core/test/utils/scp_test_base.h"
#include "cpio/client_providers/job_client_provider/mock/mock_job_client_provider_with_overrides.h"
#include "cpio/client_providers/job_client_provider/src/error_codes.h"
#include "cpio/client_providers/job_client_provider/src/job_client_utils.h"
#include "cpio/client_providers/job_client_provider/test/hello_world.pb.h"
#include "cpio/client_providers/nosql_database_client_provider/mock/mock_nosql_database_client_provider.h"
#include "cpio/client_providers/nosql_database_client_provider/src/common/error_codes.h"
#include "cpio/client_providers/queue_client_provider/mock/mock_queue_client_provider.h"
#include "cpio/common/src/gcp/gcp_utils.h"
#include "google/protobuf/util/json_util.h"
#include "google/protobuf/util/time_util.h"
#include "public/core/interface/execution_result.h"
#include "public/core/test/interface/execution_result_matchers.h"
#include "public/cpio/proto/job_service/v1/job_service.pb.h"

using absl::OkStatus;
using google::cmrt::sdk::job_service::v1::DeleteOrphanedJobMessageRequest;
using google::cmrt::sdk::job_service::v1::DeleteOrphanedJobMessageResponse;
using google::cmrt::sdk::job_service::v1::GetJobByIdRequest;
using google::cmrt::sdk::job_service::v1::GetJobByIdResponse;
using google::cmrt::sdk::job_service::v1::GetNextJobRequest;
using google::cmrt::sdk::job_service::v1::GetNextJobResponse;
using google::cmrt::sdk::job_service::v1::Job;
using google::cmrt::sdk::job_service::v1::JobStatus;
using google::cmrt::sdk::job_service::v1::JobStatus_Name;
using google::cmrt::sdk::job_service::v1::PutJobRequest;
using google::cmrt::sdk::job_service::v1::PutJobResponse;
using google::cmrt::sdk::job_service::v1::UpdateJobBodyRequest;
using google::cmrt::sdk::job_service::v1::UpdateJobBodyResponse;
using google::cmrt::sdk::job_service::v1::UpdateJobStatusRequest;
using google::cmrt::sdk::job_service::v1::UpdateJobStatusResponse;
using google::cmrt::sdk::job_service::v1::UpdateJobVisibilityTimeoutRequest;
using google::cmrt::sdk::job_service::v1::UpdateJobVisibilityTimeoutResponse;
using google::cmrt::sdk::nosql_database_service::v1::CreateDatabaseItemResponse;
using google::cmrt::sdk::nosql_database_service::v1::GetDatabaseItemResponse;
using google::cmrt::sdk::nosql_database_service::v1::Item;
using google::cmrt::sdk::nosql_database_service::v1::UpsertDatabaseItemResponse;
using google::cmrt::sdk::queue_service::v1::DeleteMessageResponse;
using google::cmrt::sdk::queue_service::v1::EnqueueMessageResponse;
using google::cmrt::sdk::queue_service::v1::GetTopMessageResponse;
using google::cmrt::sdk::queue_service::v1::
    UpdateMessageVisibilityTimeoutResponse;
using google::protobuf::Duration;
using google::protobuf::util::JsonStringToMessage;
using google::protobuf::util::MessageToJsonString;
using google::protobuf::util::TimeUtil;
using google::scp::core::AsyncContext;
using google::scp::core::AsyncExecutor;
using google::scp::core::AsyncExecutorInterface;
using google::scp::core::ExecutionResult;
using google::scp::core::FailureExecutionResult;
using google::scp::core::RetryExecutionResult;
using google::scp::core::SuccessExecutionResult;
using google::scp::core::async_executor::mock::MockAsyncExecutor;
using google::scp::core::errors::SC_CPIO_INTERNAL_ERROR;
using google::scp::core::errors::SC_CPIO_INVALID_ARGUMENT;
using google::scp::core::errors::SC_CPIO_REQUEST_TIMEOUT;
using google::scp::core::errors::SC_GCP_ALREADY_EXISTS;
using google::scp::core::errors::SC_JOB_CLIENT_PROVIDER_INVALID_DURATION;
using google::scp::core::errors::SC_JOB_CLIENT_PROVIDER_INVALID_JOB_ITEM;
using google::scp::core::errors::SC_JOB_CLIENT_PROVIDER_INVALID_JOB_STATUS;
using google::scp::core::errors::SC_JOB_CLIENT_PROVIDER_INVALID_RECEIPT_INFO;
using google::scp::core::errors::
    SC_JOB_CLIENT_PROVIDER_JOB_CLIENT_OPTIONS_REQUIRED;
using google::scp::core::errors::SC_JOB_CLIENT_PROVIDER_MISSING_JOB_ID;
using google::scp::core::errors::SC_JOB_CLIENT_PROVIDER_UPDATION_CONFLICT;
using google::scp::core::errors::
    SC_NO_SQL_DATABASE_PROVIDER_CONDITIONAL_CHECKED_FAILED;
using google::scp::core::errors::
    SC_NO_SQL_DATABASE_PROVIDER_INVALID_PARTITION_KEY_NAME;
using google::scp::core::errors::
    SC_NO_SQL_DATABASE_PROVIDER_INVALID_SORT_KEY_NAME;
using google::scp::core::errors::
    SC_NO_SQL_DATABASE_PROVIDER_JSON_FAILED_TO_PARSE;
using google::scp::core::errors::SC_NO_SQL_DATABASE_PROVIDER_RECORD_CORRUPTED;
using google::scp::core::errors::SC_NO_SQL_DATABASE_PROVIDER_RECORD_NOT_FOUND;
using google::scp::core::errors::SC_NO_SQL_DATABASE_PROVIDER_RETRIABLE_ERROR;
using google::scp::core::errors::SC_NO_SQL_DATABASE_PROVIDER_TABLE_NOT_FOUND;
using google::scp::core::errors::SC_NO_SQL_DATABASE_PROVIDER_UNRETRIABLE_ERROR;
using google::scp::core::test::ResultIs;
using google::scp::core::test::ScpTestBase;
using google::scp::core::test::WaitUntil;
using google::scp::cpio::client_providers::mock::
    MockJobClientProviderWithOverrides;
using google::scp::cpio::client_providers::mock::
    MockNoSQLDatabaseClientProvider;
using google::scp::cpio::client_providers::mock::MockQueueClientProvider;
using helloworld::HelloWorld;
using std::make_shared;
using std::make_unique;
using std::shared_ptr;
using std::string;
using std::unique_ptr;
using std::chrono::seconds;
using testing::Eq;
using testing::InSequence;
using testing::Ne;
using testing::NiceMock;
using testing::Return;

namespace {
constexpr char kHelloWorldName[] = "hello world";
constexpr int kHelloWorldId = 42356441;
const google::protobuf::Timestamp kHelloWorldProtoCreatedTime =
    TimeUtil::SecondsToTimestamp(1672531200);
const google::protobuf::Timestamp kDefaultTimestamp =
    TimeUtil::SecondsToTimestamp(0);

constexpr char kQueueMessageReceiptInfo[] = "receipt-info";
constexpr char kJobId[] = "job-id";
constexpr char kServerJobId[] = "server-job-id";
constexpr char kDefaultTimestampValueInString[] = "0";
constexpr int kDefaultRetryCount = 0;
const Duration kUpdatedVisibilityTimeout = TimeUtil::SecondsToDuration(90);
const Duration kExceededVisibilityTimeout = TimeUtil::SecondsToDuration(1000);
const Duration kNegativeVisibilityTimeout = TimeUtil::SecondsToDuration(-20);

constexpr char kJobsQueueName[] = "Queue";
constexpr char kJobsTableName[] = "Jobs";
const google::protobuf::Timestamp kCreatedTime =
    TimeUtil::SecondsToTimestamp(1680709200);
const google::protobuf::Timestamp kLastUpdatedTime =
    TimeUtil::SecondsToTimestamp(1680739200);
const google::protobuf::Timestamp kStaleUpdatedTime =
    TimeUtil::SecondsToTimestamp(946684800);

string CreateHelloWorldProtoAsJsonString() {
  HelloWorld hello_world_input;
  hello_world_input.set_name(kHelloWorldName);
  hello_world_input.set_id(kHelloWorldId);
  *hello_world_input.mutable_created_time() = kHelloWorldProtoCreatedTime;

  string json_string;
  EXPECT_EQ(MessageToJsonString(hello_world_input, &json_string), OkStatus());
  return json_string;
}

}  // namespace

namespace google::scp::cpio::client_providers::job_client::test {

Item CreateJobAsDatabaseItem(
    const string& job_body, const JobStatus& job_status,
    const google::protobuf::Timestamp& current_time,
    const google::protobuf::Timestamp& updated_time, const int retry_count,
    const google::protobuf::Timestamp& processing_started_time) {
  Item item;
  *item.mutable_key()->mutable_partition_key() =
      google::scp::cpio::client_providers::JobClientUtils::MakeStringAttribute(
          kJobsTablePartitionKeyName, kJobId);
  *item.add_attributes() =
      google::scp::cpio::client_providers::JobClientUtils::MakeStringAttribute(
          kServerJobIdColumnName, kServerJobId);
  *item.add_attributes() = google::scp::cpio::client_providers::JobClientUtils::
      MakeJsonStringAttribute(kJobBodyColumnName, job_body);
  *item.add_attributes() =
      google::scp::cpio::client_providers::JobClientUtils::MakeStringAttribute(
          kJobStatusColumnName, JobStatus_Name(job_status));
  *item.add_attributes() =
      google::scp::cpio::client_providers::JobClientUtils::MakeStringAttribute(
          kCreatedTimeColumnName, TimeUtil::ToString(current_time));
  *item.add_attributes() =
      google::scp::cpio::client_providers::JobClientUtils::MakeStringAttribute(
          kUpdatedTimeColumnName, TimeUtil::ToString(updated_time));
  *item.add_attributes() =
      google::scp::cpio::client_providers::JobClientUtils::MakeIntAttribute(
          kRetryCountColumnName, retry_count);
  *item.add_attributes() =
      google::scp::cpio::client_providers::JobClientUtils::MakeStringAttribute(
          kProcessingStartedTimeColumnName,
          TimeUtil::ToString(processing_started_time));
  return item;
}

class JobClientProviderTest : public ScpTestBase,
                              public ::testing::WithParamInterface<Duration> {
 protected:
  JobClientProviderTest() {
    job_client_options_ = make_shared<JobClientOptions>();
    job_client_options_->job_queue_name = kJobsQueueName;
    job_client_options_->job_table_name = kJobsTableName;
    job_client_options_->read_job_retry_interval =
        std::chrono::milliseconds(101);
    job_client_options_->read_job_max_retry_count = 0;
    queue_client_provider_ = make_shared<NiceMock<MockQueueClientProvider>>();
    nosql_database_client_provider_ =
        make_shared<NiceMock<MockNoSQLDatabaseClientProvider>>();
    io_async_executor_ = make_shared<AsyncExecutor>(2, 1000);

    job_client_provider_ = make_unique<MockJobClientProviderWithOverrides>(
        job_client_options_, queue_client_provider_,
        nosql_database_client_provider_, io_async_executor_);

    put_job_context_.request = make_shared<PutJobRequest>();
    put_job_context_.callback = [this](auto) { finish_called_ = true; };

    get_next_job_context_.request = make_shared<GetNextJobRequest>();
    get_next_job_context_.callback = [this](auto) { finish_called_ = true; };

    get_job_by_id_context_.request = make_shared<GetJobByIdRequest>();
    get_job_by_id_context_.callback = [this](auto) { finish_called_ = true; };

    update_job_body_context_.request = make_shared<UpdateJobBodyRequest>();
    update_job_body_context_.callback = [this](auto) { finish_called_ = true; };

    update_job_status_context_.request = make_shared<UpdateJobStatusRequest>();
    update_job_status_context_.callback = [this](auto) {
      finish_called_ = true;
    };

    update_job_visibility_timeout_context_.request =
        make_shared<UpdateJobVisibilityTimeoutRequest>();
    update_job_visibility_timeout_context_.callback = [this](auto) {
      finish_called_ = true;
    };

    delete_orphaned_job_context_.request =
        make_shared<DeleteOrphanedJobMessageRequest>();
    delete_orphaned_job_context_.callback = [this](auto) {
      finish_called_ = true;
    };

    EXPECT_SUCCESS(io_async_executor_->Init());
    EXPECT_SUCCESS(io_async_executor_->Run());
  }

  void TearDown() override {
    EXPECT_SUCCESS(job_client_provider_->Stop());
    EXPECT_SUCCESS(io_async_executor_->Stop());
  }

  shared_ptr<JobClientOptions> job_client_options_;
  shared_ptr<MockQueueClientProvider> queue_client_provider_;
  shared_ptr<MockNoSQLDatabaseClientProvider> nosql_database_client_provider_;
  shared_ptr<AsyncExecutorInterface> io_async_executor_;
  unique_ptr<JobClientProvider> job_client_provider_;

  AsyncContext<PutJobRequest, PutJobResponse> put_job_context_;

  AsyncContext<GetNextJobRequest, GetNextJobResponse> get_next_job_context_;

  AsyncContext<GetJobByIdRequest, GetJobByIdResponse> get_job_by_id_context_;

  AsyncContext<UpdateJobBodyRequest, UpdateJobBodyResponse>
      update_job_body_context_;

  AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>
      update_job_status_context_;

  AsyncContext<UpdateJobVisibilityTimeoutRequest,
               UpdateJobVisibilityTimeoutResponse>
      update_job_visibility_timeout_context_;

  AsyncContext<DeleteOrphanedJobMessageRequest,
               DeleteOrphanedJobMessageResponse>
      delete_orphaned_job_context_;

  // We check that this gets flipped after every call to ensure the context's
  // Finish() is called.
  std::atomic_bool finish_called_{false};
};

TEST_F(JobClientProviderTest, InitWithNullJobClientOptions) {
  auto client = make_unique<MockJobClientProviderWithOverrides>(
      nullptr, queue_client_provider_, nosql_database_client_provider_,
      make_shared<MockAsyncExecutor>());

  EXPECT_THAT(client->Init(),
              ResultIs(FailureExecutionResult(
                  SC_JOB_CLIENT_PROVIDER_JOB_CLIENT_OPTIONS_REQUIRED)));
}

TEST_F(JobClientProviderTest, InitWithEmptyJobQueueName) {
  auto job_client_options = make_shared<JobClientOptions>();
  job_client_options->job_table_name = "table";
  auto client = make_unique<MockJobClientProviderWithOverrides>(
      job_client_options, queue_client_provider_,
      nosql_database_client_provider_, make_shared<MockAsyncExecutor>());

  EXPECT_THAT(client->Init(),
              ResultIs(FailureExecutionResult(
                  SC_JOB_CLIENT_PROVIDER_JOB_CLIENT_OPTIONS_REQUIRED)));
}

TEST_F(JobClientProviderTest, InitWithEmptyJobTableName) {
  auto job_client_options = make_shared<JobClientOptions>();
  job_client_options->job_queue_name = "queue";
  auto client = make_unique<MockJobClientProviderWithOverrides>(
      job_client_options, queue_client_provider_,
      nosql_database_client_provider_, make_shared<MockAsyncExecutor>());

  EXPECT_THAT(client->Init(),
              ResultIs(FailureExecutionResult(
                  SC_JOB_CLIENT_PROVIDER_JOB_CLIENT_OPTIONS_REQUIRED)));
}

MATCHER_P7(HasCreateItemParamsForJobCreations, table_name, job_id,
           encoded_job_body, job_status, job_created_time_default_value,
           job_updated_time_default_value, retry_count, "") {
  return ExplainMatchResult(Eq(table_name),
                            arg.request->mutable_key()->table_name(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_id),
                            arg.request->mutable_key()
                                ->mutable_partition_key()
                                ->value_string(),
                            result_listener) &&
         ExplainMatchResult(Ne(""), arg.request->attributes(0).value_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(encoded_job_body),
                            arg.request->attributes(1).value_json_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_status),
                            arg.request->attributes(2).value_string(),
                            result_listener) &&
         ExplainMatchResult(Ne(job_created_time_default_value),
                            arg.request->attributes(3).value_string(),
                            result_listener) &&
         ExplainMatchResult(Ne(job_updated_time_default_value),
                            arg.request->attributes(4).value_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(retry_count),
                            arg.request->attributes(5).value_int(),
                            result_listener);
}

TEST_F(JobClientProviderTest, PutJobSuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  string server_job_id;
  EXPECT_CALL(*queue_client_provider_, EnqueueMessage)
      .WillOnce([&server_job_id](auto& enqueue_message_context) {
        enqueue_message_context.response =
            make_shared<EnqueueMessageResponse>();
        auto job_message_body =
            JobMessageBody(enqueue_message_context.request->message_body());
        EXPECT_EQ(job_message_body.job_id, kJobId);
        server_job_id = job_message_body.server_job_id;
        enqueue_message_context.result = SuccessExecutionResult();
        enqueue_message_context.Finish();
      });

  string job_body = CreateHelloWorldProtoAsJsonString();
  google::protobuf::Timestamp job_created_time_in_request;
  EXPECT_CALL(*nosql_database_client_provider_,
              CreateDatabaseItem(HasCreateItemParamsForJobCreations(
                  kJobsTableName, kJobId, job_body,
                  JobStatus_Name(JobStatus::JOB_STATUS_CREATED),
                  kDefaultTimestampValueInString,
                  kDefaultTimestampValueInString, kDefaultRetryCount)))
      .WillOnce([&job_created_time_in_request](
                    auto& create_database_item_context) {
        auto created_time_in_string =
            create_database_item_context.request->attributes(3).value_string();
        TimeUtil::FromString(created_time_in_string,
                             &job_created_time_in_request);
        create_database_item_context.response =
            make_shared<CreateDatabaseItemResponse>();
        create_database_item_context.result = SuccessExecutionResult();
        create_database_item_context.Finish();
      });

  put_job_context_.request->set_job_id(kJobId);
  *put_job_context_.request->mutable_job_body() = job_body;
  Job job_output;
  put_job_context_.callback =
      [this, &server_job_id, &job_created_time_in_request](
          AsyncContext<PutJobRequest, PutJobResponse>& put_job_context) {
        EXPECT_SUCCESS(put_job_context.result);
        auto job_output = put_job_context.response->job();

        EXPECT_EQ(job_output.job_id(), kJobId);
        EXPECT_EQ(job_output.server_job_id(), server_job_id);

        auto job_body_output = job_output.job_body();
        HelloWorld hello_world_output;
        EXPECT_EQ(JsonStringToMessage(job_body_output, &hello_world_output),
                  OkStatus());
        EXPECT_EQ(hello_world_output.name(), kHelloWorldName);
        EXPECT_EQ(hello_world_output.id(), kHelloWorldId);
        EXPECT_EQ(hello_world_output.created_time(),
                  kHelloWorldProtoCreatedTime);

        EXPECT_EQ(job_output.job_status(), JobStatus::JOB_STATUS_CREATED);
        EXPECT_EQ(job_output.created_time(), job_created_time_in_request);
        EXPECT_EQ(job_output.updated_time(), job_created_time_in_request);
        EXPECT_EQ(job_output.retry_count(), kDefaultRetryCount);
        EXPECT_EQ(job_output.processing_started_time(), kDefaultTimestamp);
        finish_called_ = true;
      };

  job_client_provider_->PutJob(put_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, PutJobWithMissingJobIdFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  put_job_context_.callback =
      [this](AsyncContext<PutJobRequest, PutJobResponse>& put_job_context) {
        EXPECT_THAT(put_job_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_MISSING_JOB_ID)));
        finish_called_ = true;
      };

  job_client_provider_->PutJob(put_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, PutJobWithEnqueueMessageFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*queue_client_provider_, EnqueueMessage)
      .WillOnce([](auto& enqueue_message_context) {
        enqueue_message_context.result =
            FailureExecutionResult(SC_CPIO_INTERNAL_ERROR);
        enqueue_message_context.Finish();
      });

  put_job_context_.request->set_job_id(kJobId);
  put_job_context_.callback =
      [this](AsyncContext<PutJobRequest, PutJobResponse>& put_job_context) {
        EXPECT_THAT(put_job_context.result,
                    ResultIs(FailureExecutionResult(SC_CPIO_INTERNAL_ERROR)));
        finish_called_ = true;
      };

  job_client_provider_->PutJob(put_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, PutJobWithCreateDatabaseItemFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*queue_client_provider_, EnqueueMessage)
      .WillOnce([](auto& enqueue_message_context) {
        enqueue_message_context.response =
            make_shared<EnqueueMessageResponse>();
        auto job_message_body =
            JobMessageBody(enqueue_message_context.request->message_body());
        EXPECT_EQ(job_message_body.job_id, kJobId);
        enqueue_message_context.result = SuccessExecutionResult();
        enqueue_message_context.Finish();
      });

  EXPECT_CALL(*nosql_database_client_provider_, CreateDatabaseItem)
      .WillOnce([](auto& create_database_item_context) {
        create_database_item_context.result =
            FailureExecutionResult(SC_GCP_ALREADY_EXISTS);
        create_database_item_context.Finish();
      });

  string job_body = CreateHelloWorldProtoAsJsonString();
  put_job_context_.request->set_job_id(kJobId);
  *put_job_context_.request->mutable_job_body() = job_body;
  put_job_context_.callback =
      [this](AsyncContext<PutJobRequest, PutJobResponse>& put_job_context) {
        EXPECT_THAT(put_job_context.result,
                    ResultIs(FailureExecutionResult(SC_GCP_ALREADY_EXISTS)));
        finish_called_ = true;
      };

  job_client_provider_->PutJob(put_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

MATCHER_P3(HasGetDatabaseItemParamsForGetNextJob, table_name, job_id,
           server_job_id, "") {
  return ExplainMatchResult(Eq(table_name),
                            arg.request->mutable_key()->table_name(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_id),
                            arg.request->mutable_key()
                                ->mutable_partition_key()
                                ->value_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(server_job_id),
                            arg.request->required_attributes(0).value_string(),
                            result_listener);
}

TEST_F(JobClientProviderTest, GetNextJobSuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*queue_client_provider_, GetTopMessage)
      .WillOnce([](auto& get_top_message_context) {
        get_top_message_context.response = make_shared<GetTopMessageResponse>();
        auto message_body = JobMessageBody(kJobId, kServerJobId);
        get_top_message_context.response->set_message_body(
            message_body.ToJsonString());
        get_top_message_context.response->set_receipt_info(
            kQueueMessageReceiptInfo);
        get_top_message_context.result = SuccessExecutionResult();
        get_top_message_context.Finish();
      });

  auto created_time = TimeUtil::GetCurrentTime();
  auto updated_time = created_time;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetNextJob(
                  kJobsTableName, kJobId, kServerJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  get_next_job_context_.callback =
      [this, &created_time, &updated_time,
       &retry_count](AsyncContext<GetNextJobRequest, GetNextJobResponse>&
                         get_next_job_context) {
        EXPECT_SUCCESS(get_next_job_context.result);
        auto job_output = get_next_job_context.response->job();
        EXPECT_EQ(job_output.job_id(), kJobId);

        auto job_body_output = job_output.job_body();
        HelloWorld hello_world_output;
        EXPECT_EQ(JsonStringToMessage(job_body_output, &hello_world_output),
                  OkStatus());
        EXPECT_EQ(hello_world_output.name(), kHelloWorldName);
        EXPECT_EQ(hello_world_output.id(), kHelloWorldId);
        EXPECT_EQ(hello_world_output.created_time(),
                  kHelloWorldProtoCreatedTime);

        EXPECT_EQ(job_output.job_status(), JobStatus::JOB_STATUS_CREATED);
        EXPECT_EQ(job_output.created_time(), created_time);
        EXPECT_EQ(job_output.updated_time(), updated_time);
        EXPECT_EQ(job_output.retry_count(), retry_count);

        EXPECT_EQ(get_next_job_context.response->receipt_info(),
                  kQueueMessageReceiptInfo);

        finish_called_ = true;
      };

  job_client_provider_->GetNextJob(get_next_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, GetNextJobWithGetTopMessageFailure) {
  EXPECT_CALL(*queue_client_provider_, GetTopMessage)
      .WillOnce([](auto& get_top_message_context) {
        get_top_message_context.result =
            FailureExecutionResult(SC_CPIO_INTERNAL_ERROR);
        get_top_message_context.Finish();
      });

  get_next_job_context_.callback =
      [this](AsyncContext<GetNextJobRequest, GetNextJobResponse>&
                 get_next_job_context) {
        EXPECT_THAT(get_next_job_context.result,
                    ResultIs(FailureExecutionResult(SC_CPIO_INTERNAL_ERROR)));
        finish_called_ = true;
      };

  job_client_provider_->GetNextJob(get_next_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, GetNextJobWithGetDatabaseItemFailed) {
  job_client_options_->read_job_max_retry_count = 3;
  job_client_options_->read_job_retry_interval = std::chrono::milliseconds(101);
  job_client_provider_ = make_unique<MockJobClientProviderWithOverrides>(
      job_client_options_, queue_client_provider_,
      nosql_database_client_provider_, io_async_executor_);
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  ON_CALL(*queue_client_provider_, GetTopMessage)
      .WillByDefault([](auto& get_top_message_context) {
        get_top_message_context.response = make_shared<GetTopMessageResponse>();
        auto message_body = JobMessageBody(kJobId, kServerJobId);
        get_top_message_context.response->set_message_body(
            message_body.ToJsonString());
        get_top_message_context.response->set_receipt_info(
            kQueueMessageReceiptInfo);
        get_top_message_context.result = SuccessExecutionResult();
        get_top_message_context.Finish();
      });

  ON_CALL(*nosql_database_client_provider_,
          GetDatabaseItem(HasGetDatabaseItemParamsForGetNextJob(
              kJobsTableName, kJobId, kServerJobId)))
      .WillByDefault([](auto& get_database_item_context) {
        get_database_item_context.result = FailureExecutionResult(
            SC_NO_SQL_DATABASE_PROVIDER_RECORD_CORRUPTED);
        get_database_item_context.Finish();
      });

  get_next_job_context_.callback =
      [this](AsyncContext<GetNextJobRequest, GetNextJobResponse>&
                 get_next_job_context) {
        EXPECT_THAT(get_next_job_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_NO_SQL_DATABASE_PROVIDER_RECORD_CORRUPTED)));
        finish_called_ = true;
      };

  job_client_provider_->GetNextJob(get_next_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       GetNextJobWithGetDatabaseItemSuccessAfterRetriesOnNoRecordFound) {
  job_client_options_->read_job_max_retry_count = 3;
  job_client_options_->read_job_retry_interval = std::chrono::milliseconds(101);
  job_client_provider_ = make_unique<MockJobClientProviderWithOverrides>(
      job_client_options_, queue_client_provider_,
      nosql_database_client_provider_, io_async_executor_);
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  ON_CALL(*queue_client_provider_, GetTopMessage)
      .WillByDefault([](auto& get_top_message_context) {
        get_top_message_context.response = make_shared<GetTopMessageResponse>();
        auto message_body = JobMessageBody(kJobId, kServerJobId);
        get_top_message_context.response->set_message_body(
            message_body.ToJsonString());
        get_top_message_context.response->set_receipt_info(
            kQueueMessageReceiptInfo);
        get_top_message_context.result = SuccessExecutionResult();
        get_top_message_context.Finish();
      });

  auto created_time = TimeUtil::GetCurrentTime();
  auto updated_time = created_time;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  {
    InSequence seq;
    EXPECT_CALL(*nosql_database_client_provider_,
                GetDatabaseItem(HasGetDatabaseItemParamsForGetNextJob(
                    kJobsTableName, kJobId, kServerJobId)))
        .Times(2)
        .WillRepeatedly([](auto& get_database_item_context) {
          get_database_item_context.result = FailureExecutionResult(
              SC_NO_SQL_DATABASE_PROVIDER_RECORD_NOT_FOUND);
          get_database_item_context.Finish();
        });

    EXPECT_CALL(*nosql_database_client_provider_,
                GetDatabaseItem(HasGetDatabaseItemParamsForGetNextJob(
                    kJobsTableName, kJobId, kServerJobId)))
        .WillOnce([&item](auto& get_database_item_context) {
          get_database_item_context.response =
              make_shared<GetDatabaseItemResponse>();
          *get_database_item_context.response->mutable_item() = item;
          get_database_item_context.result = SuccessExecutionResult();
          get_database_item_context.Finish();
        });
  }

  get_next_job_context_.callback =
      [this, &created_time, &updated_time,
       &retry_count](AsyncContext<GetNextJobRequest, GetNextJobResponse>&
                         get_next_job_context) {
        EXPECT_SUCCESS(get_next_job_context.result);
        auto job_output = get_next_job_context.response->job();
        EXPECT_EQ(job_output.job_id(), kJobId);

        auto job_body_output = job_output.job_body();
        HelloWorld hello_world_output;
        EXPECT_EQ(JsonStringToMessage(job_body_output, &hello_world_output),
                  OkStatus());
        EXPECT_EQ(hello_world_output.name(), kHelloWorldName);
        EXPECT_EQ(hello_world_output.id(), kHelloWorldId);
        EXPECT_EQ(hello_world_output.created_time(),
                  kHelloWorldProtoCreatedTime);

        EXPECT_EQ(job_output.job_status(), JobStatus::JOB_STATUS_CREATED);
        EXPECT_EQ(job_output.created_time(), created_time);
        EXPECT_EQ(job_output.updated_time(), updated_time);
        EXPECT_EQ(job_output.retry_count(), retry_count);

        EXPECT_EQ(get_next_job_context.response->receipt_info(),
                  kQueueMessageReceiptInfo);

        finish_called_ = true;
      };

  job_client_provider_->GetNextJob(get_next_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       GetNextJobWithGetDatabaseItemFailureAfterReachesRetryLimit) {
  job_client_options_->read_job_max_retry_count = 3;
  job_client_options_->read_job_retry_interval = std::chrono::milliseconds(101);
  job_client_provider_ = make_unique<MockJobClientProviderWithOverrides>(
      job_client_options_, queue_client_provider_,
      nosql_database_client_provider_, io_async_executor_);
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  ON_CALL(*queue_client_provider_, GetTopMessage)
      .WillByDefault([](auto& get_top_message_context) {
        get_top_message_context.response = make_shared<GetTopMessageResponse>();
        auto message_body = JobMessageBody(kJobId, kServerJobId);
        get_top_message_context.response->set_message_body(
            message_body.ToJsonString());
        get_top_message_context.response->set_receipt_info(
            kQueueMessageReceiptInfo);
        get_top_message_context.result = SuccessExecutionResult();
        get_top_message_context.Finish();
      });

  auto created_time = TimeUtil::GetCurrentTime();
  auto updated_time = created_time;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetNextJob(
                  kJobsTableName, kJobId, kServerJobId)))
      .Times(job_client_options_->read_job_max_retry_count)
      .WillRepeatedly([](auto& context) {
        context.result = FailureExecutionResult(
            SC_NO_SQL_DATABASE_PROVIDER_RECORD_NOT_FOUND);
        context.Finish();
      });

  get_next_job_context_.callback =
      [this](AsyncContext<GetNextJobRequest, GetNextJobResponse>&
                 get_next_job_context) {
        EXPECT_EQ(get_next_job_context.response->job().job_id(), kJobId);
        EXPECT_EQ(get_next_job_context.response->receipt_info(),
                  kQueueMessageReceiptInfo);
        EXPECT_SUCCESS(get_next_job_context.result);
        finish_called_ = true;
      };

  job_client_provider_->GetNextJob(get_next_job_context_);

  WaitUntil([this]() { return finish_called_.load(); }, seconds(30));
}

TEST_F(JobClientProviderTest, GetNextJobWithInvalidDatabaseItemFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*queue_client_provider_, GetTopMessage)
      .WillOnce([](auto& get_top_message_context) {
        get_top_message_context.response = make_shared<GetTopMessageResponse>();
        auto message_body = JobMessageBody(kJobId, kServerJobId);
        get_top_message_context.response->set_message_body(
            message_body.ToJsonString());
        get_top_message_context.response->set_receipt_info(
            kQueueMessageReceiptInfo);
        get_top_message_context.result = SuccessExecutionResult();
        get_top_message_context.Finish();
      });

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetNextJob(
                  kJobsTableName, kJobId, kServerJobId)))
      .WillOnce([](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  get_next_job_context_.callback =
      [this](AsyncContext<GetNextJobRequest, GetNextJobResponse>&
                 get_next_job_context) {
        EXPECT_THAT(get_next_job_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_JOB_ITEM)));
        finish_called_ = true;
      };

  job_client_provider_->GetNextJob(get_next_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

MATCHER_P2(HasGetDatabaseItemParamsForGetJobById, table_name, job_id, "") {
  return ExplainMatchResult(Eq(table_name),
                            arg.request->mutable_key()->table_name(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_id),
                            arg.request->mutable_key()
                                ->mutable_partition_key()
                                ->value_string(),
                            result_listener);
}

TEST_F(JobClientProviderTest, GetJobById) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = TimeUtil::GetCurrentTime();
  auto updated_time = created_time;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = created_time + TimeUtil::SecondsToDuration(10);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  get_job_by_id_context_.request->set_job_id(kJobId);
  get_job_by_id_context_.callback =
      [this, &created_time, &updated_time,
       &retry_count](AsyncContext<GetJobByIdRequest, GetJobByIdResponse>&
                         get_job_by_id_context) {
        EXPECT_SUCCESS(get_job_by_id_context.result);
        auto job_output = get_job_by_id_context.response->job();
        EXPECT_EQ(job_output.job_id(), kJobId);

        auto job_body_output = job_output.job_body();
        HelloWorld hello_world_output;
        EXPECT_EQ(JsonStringToMessage(job_body_output, &hello_world_output),
                  OkStatus());
        EXPECT_EQ(hello_world_output.name(), kHelloWorldName);
        EXPECT_EQ(hello_world_output.id(), kHelloWorldId);
        EXPECT_EQ(hello_world_output.created_time(),
                  kHelloWorldProtoCreatedTime);

        EXPECT_EQ(job_output.job_status(), JobStatus::JOB_STATUS_CREATED);
        EXPECT_EQ(job_output.created_time(), created_time);
        EXPECT_EQ(job_output.updated_time(), updated_time);
        EXPECT_EQ(job_output.retry_count(), retry_count);

        finish_called_ = true;
      };

  job_client_provider_->GetJobById(get_job_by_id_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, GetJobByIdWithMissingJobIdFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  get_job_by_id_context_.callback =
      [this](AsyncContext<GetJobByIdRequest, GetJobByIdResponse>&
                 get_job_by_id_context) {
        EXPECT_THAT(get_job_by_id_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_MISSING_JOB_ID)));
        finish_called_ = true;
      };

  job_client_provider_->GetJobById(get_job_by_id_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, GetJobByIdWithGetDatabaseItemFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([](auto& get_database_item_context) {
        get_database_item_context.result = FailureExecutionResult(
            SC_NO_SQL_DATABASE_PROVIDER_UNRETRIABLE_ERROR);
        get_database_item_context.Finish();
      });

  get_job_by_id_context_.request->set_job_id(kJobId);
  get_job_by_id_context_.callback =
      [this](AsyncContext<GetJobByIdRequest, GetJobByIdResponse>&
                 get_job_by_id_context) {
        EXPECT_THAT(get_job_by_id_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_NO_SQL_DATABASE_PROVIDER_UNRETRIABLE_ERROR)));
        finish_called_ = true;
      };

  job_client_provider_->GetJobById(get_job_by_id_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, GetJobByIdWithInvalidDatabaseItemFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  get_job_by_id_context_.request->set_job_id(kJobId);
  get_job_by_id_context_.callback =
      [this](AsyncContext<GetJobByIdRequest, GetJobByIdResponse>&
                 get_job_by_id_context) {
        EXPECT_THAT(get_job_by_id_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_JOB_ITEM)));
        finish_called_ = true;
      };

  job_client_provider_->GetJobById(get_job_by_id_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

MATCHER_P3(HasUpsertItemParamsForJobBodyUpdates, table_name, encoded_job_body,
           job_updated_time_default_value, "") {
  return ExplainMatchResult(Eq(table_name),
                            arg.request->mutable_key()->table_name(),
                            result_listener) &&
         ExplainMatchResult(Eq(encoded_job_body),
                            arg.request->new_attributes(0).value_json_string(),
                            result_listener) &&
         ExplainMatchResult(Ne(job_updated_time_default_value),
                            arg.request->new_attributes(1).value_string(),
                            result_listener);
}

TEST_F(JobClientProviderTest, UpdateJobBodySuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  google::protobuf::Timestamp job_updated_time_in_request;
  EXPECT_CALL(*nosql_database_client_provider_,
              UpsertDatabaseItem(HasUpsertItemParamsForJobBodyUpdates(
                  kJobsTableName, job_body, kDefaultTimestampValueInString)))
      .WillOnce(
          [&job_updated_time_in_request](auto& upsert_database_item_context) {
            auto updated_time_in_string =
                upsert_database_item_context.request->new_attributes(1)
                    .value_string();
            TimeUtil::FromString(updated_time_in_string,
                                 &job_updated_time_in_request);
            upsert_database_item_context.response =
                make_shared<UpsertDatabaseItemResponse>();
            upsert_database_item_context.result = SuccessExecutionResult();
            upsert_database_item_context.Finish();
          });

  update_job_body_context_.request->set_job_id(kJobId);
  *update_job_body_context_.request->mutable_job_body() = job_body;
  *update_job_body_context_.request->mutable_most_recent_updated_time() =
      kLastUpdatedTime;
  update_job_body_context_.callback =
      [this, &job_updated_time_in_request](
          AsyncContext<UpdateJobBodyRequest, UpdateJobBodyResponse>&
              update_job_body_context) {
        EXPECT_SUCCESS(update_job_body_context.result);
        EXPECT_EQ(update_job_body_context.response->updated_time(),
                  job_updated_time_in_request);
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobBody(update_job_body_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobBodyWithMissingJobIdFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  update_job_body_context_.callback =
      [this](AsyncContext<UpdateJobBodyRequest, UpdateJobBodyResponse>&
                 update_job_body_context) {
        EXPECT_THAT(update_job_body_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_MISSING_JOB_ID)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobBody(update_job_body_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobBodyWithGetDatabaseItemFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([](auto& get_database_item_context) {
        get_database_item_context.result = FailureExecutionResult(
            SC_NO_SQL_DATABASE_PROVIDER_JSON_FAILED_TO_PARSE);
        get_database_item_context.Finish();
      });

  update_job_body_context_.request->set_job_id(kJobId);
  *update_job_body_context_.request->mutable_job_body() =
      CreateHelloWorldProtoAsJsonString();
  update_job_body_context_.callback =
      [this](AsyncContext<UpdateJobBodyRequest, UpdateJobBodyResponse>&
                 update_job_body_context) {
        EXPECT_THAT(update_job_body_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_NO_SQL_DATABASE_PROVIDER_JSON_FAILED_TO_PARSE)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobBody(update_job_body_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobBodyWithInvalidDatabaseItemFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  update_job_body_context_.request->set_job_id(kJobId);
  *update_job_body_context_.request->mutable_job_body() =
      CreateHelloWorldProtoAsJsonString();
  update_job_body_context_.callback =
      [this](AsyncContext<UpdateJobBodyRequest, UpdateJobBodyResponse>&
                 update_job_body_context) {
        EXPECT_THAT(update_job_body_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_JOB_ITEM)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobBody(update_job_body_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobBodyWithRequestConflictsFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  update_job_body_context_.request->set_job_id(kJobId);
  *update_job_body_context_.request->mutable_job_body() = job_body;
  *update_job_body_context_.request->mutable_most_recent_updated_time() =
      kStaleUpdatedTime;
  update_job_body_context_.callback =
      [this](AsyncContext<UpdateJobBodyRequest, UpdateJobBodyResponse>&
                 update_job_body_context) {
        EXPECT_THAT(update_job_body_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_UPDATION_CONFLICT)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobBody(update_job_body_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobBodyWithUpsertDatabaseItemFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  EXPECT_CALL(*nosql_database_client_provider_,
              UpsertDatabaseItem(HasUpsertItemParamsForJobBodyUpdates(
                  kJobsTableName, job_body, kDefaultTimestampValueInString)))
      .WillOnce([](auto& upsert_database_item_context) {
        upsert_database_item_context.response =
            make_shared<UpsertDatabaseItemResponse>();
        upsert_database_item_context.result = FailureExecutionResult(
            SC_NO_SQL_DATABASE_PROVIDER_RECORD_CORRUPTED);
        upsert_database_item_context.Finish();
      });

  update_job_body_context_.request->set_job_id(kJobId);
  *update_job_body_context_.request->mutable_job_body() = job_body;
  *update_job_body_context_.request->mutable_most_recent_updated_time() =
      kLastUpdatedTime;
  update_job_body_context_.callback =
      [this](AsyncContext<UpdateJobBodyRequest, UpdateJobBodyResponse>&
                 update_job_body_context) {
        EXPECT_THAT(update_job_body_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_NO_SQL_DATABASE_PROVIDER_RECORD_CORRUPTED)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobBody(update_job_body_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

MATCHER_P(HasReceiptInfo, receipt_info, "") {
  return ExplainMatchResult(Eq(receipt_info), arg.request->receipt_info(),
                            result_listener);
}

MATCHER_P5(HasUpsertItemParamsForUpdateJobToFinishState, table_name, job_id,
           job_status, job_updated_time_default_value, retry_count, "") {
  return ExplainMatchResult(Eq(table_name),
                            arg.request->mutable_key()->table_name(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_id),
                            arg.request->mutable_key()
                                ->mutable_partition_key()
                                ->value_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_status),
                            arg.request->new_attributes(0).value_string(),
                            result_listener) &&
         ExplainMatchResult(Ne(job_updated_time_default_value),
                            arg.request->new_attributes(1).value_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(retry_count),
                            arg.request->new_attributes(2).value_int(),
                            result_listener);
}

TEST_F(JobClientProviderTest, UpdateJobStatusWithJobDeletionSuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  google::protobuf::Timestamp job_updated_time_in_request;
  EXPECT_CALL(
      *nosql_database_client_provider_,
      UpsertDatabaseItem(HasUpsertItemParamsForUpdateJobToFinishState(
          kJobsTableName, kJobId, JobStatus_Name(JobStatus::JOB_STATUS_SUCCESS),
          kDefaultTimestampValueInString, retry_count)))
      .WillOnce(
          [&job_updated_time_in_request](auto& upsert_database_item_context) {
            auto updated_time_in_string =
                upsert_database_item_context.request->new_attributes(1)
                    .value_string();
            TimeUtil::FromString(updated_time_in_string,
                                 &job_updated_time_in_request);
            upsert_database_item_context.response =
                make_shared<UpsertDatabaseItemResponse>();
            upsert_database_item_context.result = SuccessExecutionResult();
            upsert_database_item_context.Finish();
          });

  EXPECT_CALL(*queue_client_provider_,
              DeleteMessage(HasReceiptInfo(kQueueMessageReceiptInfo)))
      .WillOnce([](auto& delete_message_context) {
        delete_message_context.response = make_shared<DeleteMessageResponse>();
        delete_message_context.result = SuccessExecutionResult();
        delete_message_context.Finish();
      });

  *update_job_status_context_.request->mutable_job_id() = kJobId;
  auto updated_job_status = JobStatus::JOB_STATUS_SUCCESS;
  update_job_status_context_.request->set_job_status(updated_job_status);
  *update_job_status_context_.request->mutable_most_recent_updated_time() =
      kLastUpdatedTime;
  *update_job_status_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_status_context_.callback =
      [this, &updated_job_status, &job_updated_time_in_request, &retry_count](
          AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>&
              update_job_status_context) {
        EXPECT_SUCCESS(update_job_status_context.result);
        EXPECT_EQ(update_job_status_context.response->job_status(),
                  updated_job_status);
        EXPECT_EQ(update_job_status_context.response->updated_time(),
                  job_updated_time_in_request);
        EXPECT_EQ(update_job_status_context.response->retry_count(),
                  retry_count);
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobStatus(update_job_status_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

MATCHER_P6(HasUpsertItemParamsForUpdateJobStatusToProcessingState, table_name,
           job_id, job_status, job_updated_time_default_value, retry_count,
           job_start_processing_time_default_value, "") {
  return ExplainMatchResult(Eq(table_name),
                            arg.request->mutable_key()->table_name(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_id),
                            arg.request->mutable_key()
                                ->mutable_partition_key()
                                ->value_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_status),
                            arg.request->new_attributes(0).value_string(),
                            result_listener) &&
         ExplainMatchResult(Ne(job_updated_time_default_value),
                            arg.request->new_attributes(1).value_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(retry_count),
                            arg.request->new_attributes(2).value_int(),
                            result_listener) &&
         ExplainMatchResult(Ne(job_start_processing_time_default_value),
                            arg.request->new_attributes(3).value_string(),
                            result_listener);
}

TEST_F(JobClientProviderTest, UpdateJobStatusWithProcessingSuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  google::protobuf::Timestamp job_updated_time_in_request;
  EXPECT_CALL(
      *nosql_database_client_provider_,
      UpsertDatabaseItem(HasUpsertItemParamsForUpdateJobStatusToProcessingState(
          kJobsTableName, kJobId,
          JobStatus_Name(JobStatus::JOB_STATUS_PROCESSING),
          kDefaultTimestampValueInString, retry_count + 1,
          kDefaultTimestampValueInString)))
      .WillOnce(
          [&job_updated_time_in_request](auto& upsert_database_item_context) {
            auto updated_time_in_string =
                upsert_database_item_context.request->new_attributes(1)
                    .value_string();
            TimeUtil::FromString(updated_time_in_string,
                                 &job_updated_time_in_request);
            upsert_database_item_context.response =
                make_shared<UpsertDatabaseItemResponse>();
            upsert_database_item_context.result = SuccessExecutionResult();
            upsert_database_item_context.Finish();
          });

  *update_job_status_context_.request->mutable_job_id() = kJobId;
  auto updated_job_status = JobStatus::JOB_STATUS_PROCESSING;
  update_job_status_context_.request->set_job_status(updated_job_status);
  *update_job_status_context_.request->mutable_most_recent_updated_time() =
      kLastUpdatedTime;
  update_job_status_context_.callback =
      [this, &updated_job_status, &job_updated_time_in_request, &retry_count](
          AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>&
              update_job_status_context) {
        EXPECT_SUCCESS(update_job_status_context.result);
        EXPECT_EQ(update_job_status_context.response->job_status(),
                  updated_job_status);
        EXPECT_EQ(update_job_status_context.response->updated_time(),
                  job_updated_time_in_request);
        EXPECT_EQ(update_job_status_context.response->retry_count(),
                  retry_count + 1);
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobStatus(update_job_status_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

MATCHER_P5(HasUpsertItemParamsForUpdateJobStatusToCreatedState, table_name,
           job_id, job_status, job_updated_time_default_value, retry_count,
           "") {
  return ExplainMatchResult(Eq(table_name),
                            arg.request->mutable_key()->table_name(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_id),
                            arg.request->mutable_key()
                                ->mutable_partition_key()
                                ->value_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(job_status),
                            arg.request->new_attributes(0).value_string(),
                            result_listener) &&
         ExplainMatchResult(Ne(job_updated_time_default_value),
                            arg.request->new_attributes(1).value_string(),
                            result_listener) &&
         ExplainMatchResult(Eq(retry_count),
                            arg.request->new_attributes(2).value_int(),
                            result_listener);
}

TEST_F(JobClientProviderTest, UpdateJobStatusWithCreatedSuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_PROCESSING;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = kLastUpdatedTime;
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  google::protobuf::Timestamp job_updated_time_in_request;
  EXPECT_CALL(
      *nosql_database_client_provider_,
      UpsertDatabaseItem(HasUpsertItemParamsForUpdateJobStatusToCreatedState(
          kJobsTableName, kJobId, JobStatus_Name(JobStatus::JOB_STATUS_CREATED),
          kDefaultTimestampValueInString, retry_count + 1)))
      .WillOnce(
          [&job_updated_time_in_request](auto& upsert_database_item_context) {
            auto updated_time_in_string =
                upsert_database_item_context.request->new_attributes(1)
                    .value_string();
            TimeUtil::FromString(updated_time_in_string,
                                 &job_updated_time_in_request);
            upsert_database_item_context.response =
                make_shared<UpsertDatabaseItemResponse>();
            upsert_database_item_context.result = SuccessExecutionResult();
            upsert_database_item_context.Finish();
          });

  *update_job_status_context_.request->mutable_job_id() = kJobId;
  auto updated_job_status = JobStatus::JOB_STATUS_CREATED;
  update_job_status_context_.request->set_job_status(updated_job_status);
  *update_job_status_context_.request->mutable_most_recent_updated_time() =
      kLastUpdatedTime;
  *update_job_status_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_status_context_.callback =
      [this, &updated_job_status, &job_updated_time_in_request, &retry_count](
          AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>&
              update_job_status_context) {
        EXPECT_SUCCESS(update_job_status_context.result);
        EXPECT_EQ(update_job_status_context.response->job_status(),
                  updated_job_status);
        EXPECT_EQ(update_job_status_context.response->updated_time(),
                  job_updated_time_in_request);
        EXPECT_EQ(update_job_status_context.response->retry_count(),
                  retry_count + 1);
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobStatus(update_job_status_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobStatusWithMissingJobIdFailed) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  update_job_status_context_.request->set_job_status(
      JobStatus::JOB_STATUS_FAILURE);
  update_job_status_context_.callback =
      [this](AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>&
                 update_job_status_context) {
        EXPECT_THAT(update_job_status_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_MISSING_JOB_ID)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobStatus(update_job_status_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       UpdateJobStatusWithJobStatusSuccessMissingReceiptInfoFailed) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  *update_job_status_context_.request->mutable_job_id() = kJobId;
  update_job_status_context_.request->set_job_status(
      JobStatus::JOB_STATUS_SUCCESS);
  *update_job_status_context_.request->mutable_most_recent_updated_time() =
      kLastUpdatedTime;
  update_job_status_context_.callback =
      [this](AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>&
                 update_job_status_context) {
        EXPECT_THAT(update_job_status_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_RECEIPT_INFO)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobStatus(update_job_status_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobStatusWithDeleteMessageFailed) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  EXPECT_CALL(
      *nosql_database_client_provider_,
      UpsertDatabaseItem(HasUpsertItemParamsForUpdateJobToFinishState(
          kJobsTableName, kJobId, JobStatus_Name(JobStatus::JOB_STATUS_FAILURE),
          kDefaultTimestampValueInString, retry_count)))
      .WillOnce([](auto& upsert_database_item_context) {
        upsert_database_item_context.response =
            make_shared<UpsertDatabaseItemResponse>();
        upsert_database_item_context.result = SuccessExecutionResult();
        upsert_database_item_context.Finish();
      });

  EXPECT_CALL(*queue_client_provider_, DeleteMessage)
      .WillOnce([](auto& delete_message_context) {
        delete_message_context.result =
            FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT);
        delete_message_context.Finish();
      });

  *update_job_status_context_.request->mutable_job_id() = kJobId;
  update_job_status_context_.request->set_job_status(
      JobStatus::JOB_STATUS_FAILURE);
  *update_job_status_context_.request->mutable_most_recent_updated_time() =
      kLastUpdatedTime;
  *update_job_status_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_status_context_.callback =
      [this](AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>&
                 update_job_status_context) {
        EXPECT_THAT(update_job_status_context.result,
                    ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobStatus(update_job_status_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobStatusWithUpsertDatabaseItemFailed) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  EXPECT_CALL(*nosql_database_client_provider_, UpsertDatabaseItem)
      .WillOnce([](auto& upsert_database_item_context) {
        upsert_database_item_context.result =
            FailureExecutionResult(SC_NO_SQL_DATABASE_PROVIDER_TABLE_NOT_FOUND);
        upsert_database_item_context.Finish();
      });

  *update_job_status_context_.request->mutable_job_id() = kJobId;
  update_job_status_context_.request->set_job_status(
      JobStatus::JOB_STATUS_SUCCESS);
  *update_job_status_context_.request->mutable_most_recent_updated_time() =
      kLastUpdatedTime;
  *update_job_status_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_status_context_.callback =
      [this](AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>&
                 update_job_status_context) {
        EXPECT_THAT(update_job_status_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_NO_SQL_DATABASE_PROVIDER_TABLE_NOT_FOUND)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobStatus(update_job_status_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobStatusWithInvalidJobStatusFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  *update_job_status_context_.request->mutable_job_id() = kJobId;
  update_job_status_context_.request->set_job_status(
      JobStatus::JOB_STATUS_UNKNOWN);
  *update_job_status_context_.request->mutable_most_recent_updated_time() =
      kLastUpdatedTime;
  *update_job_status_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_status_context_.callback =
      [this](AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>&
                 update_job_status_context) {
        EXPECT_THAT(update_job_status_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_JOB_STATUS)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobStatus(update_job_status_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, UpdateJobStatusWithRequestConflictsFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  *update_job_status_context_.request->mutable_job_id() = kJobId;
  update_job_status_context_.request->set_job_status(
      JobStatus::JOB_STATUS_PROCESSING);
  *update_job_status_context_.request->mutable_most_recent_updated_time() =
      kStaleUpdatedTime;
  *update_job_status_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_status_context_.callback =
      [this](AsyncContext<UpdateJobStatusRequest, UpdateJobStatusResponse>&
                 update_job_status_context) {
        EXPECT_THAT(update_job_status_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_UPDATION_CONFLICT)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobStatus(update_job_status_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

MATCHER_P2(HasMessageVisibilityTimeoutParams, receipt_info,
           message_visibility_timeout_in_seconds, "") {
  return ExplainMatchResult(Eq(receipt_info), arg.request->receipt_info(),
                            result_listener) &&
         ExplainMatchResult(Eq(message_visibility_timeout_in_seconds),
                            arg.request->message_visibility_timeout(),
                            result_listener);
}

TEST_F(JobClientProviderTest, UpdateJobVisibilityTimeoutSuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*queue_client_provider_,
              UpdateMessageVisibilityTimeout(HasMessageVisibilityTimeoutParams(
                  kQueueMessageReceiptInfo, kUpdatedVisibilityTimeout)))
      .WillOnce([](auto& update_message_visibility_timeout_context) {
        update_message_visibility_timeout_context.response =
            make_shared<UpdateMessageVisibilityTimeoutResponse>();
        update_message_visibility_timeout_context.result =
            SuccessExecutionResult();
        update_message_visibility_timeout_context.Finish();
      });

  *update_job_visibility_timeout_context_.request->mutable_job_id() = kJobId;
  *update_job_visibility_timeout_context_.request
       ->mutable_duration_to_update() = kUpdatedVisibilityTimeout;
  *update_job_visibility_timeout_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_visibility_timeout_context_.callback =
      [this](AsyncContext<UpdateJobVisibilityTimeoutRequest,
                          UpdateJobVisibilityTimeoutResponse>&
                 update_job_visibility_timeout_context) {
        EXPECT_SUCCESS(update_job_visibility_timeout_context.result);
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobVisibilityTimeout(
      update_job_visibility_timeout_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       UpdateJobVisibilityTimeoutWithMissingJobIdFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  *update_job_visibility_timeout_context_.request
       ->mutable_duration_to_update() = kUpdatedVisibilityTimeout;
  *update_job_visibility_timeout_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_visibility_timeout_context_.callback =
      [this](AsyncContext<UpdateJobVisibilityTimeoutRequest,
                          UpdateJobVisibilityTimeoutResponse>&
                 update_job_visibility_timeout_context) {
        EXPECT_THAT(update_job_visibility_timeout_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_MISSING_JOB_ID)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobVisibilityTimeout(
      update_job_visibility_timeout_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       UpdateJobVisibilityTimeoutWithMissingReceiptInfoFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  *update_job_visibility_timeout_context_.request->mutable_job_id() = kJobId;
  *update_job_visibility_timeout_context_.request
       ->mutable_duration_to_update() = kUpdatedVisibilityTimeout;
  update_job_visibility_timeout_context_.callback =
      [this](AsyncContext<UpdateJobVisibilityTimeoutRequest,
                          UpdateJobVisibilityTimeoutResponse>&
                 update_job_visibility_timeout_context) {
        EXPECT_THAT(update_job_visibility_timeout_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_RECEIPT_INFO)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobVisibilityTimeout(
      update_job_visibility_timeout_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

INSTANTIATE_TEST_SUITE_P(InvalidDurations, JobClientProviderTest,
                         testing::Values(kExceededVisibilityTimeout,
                                         kNegativeVisibilityTimeout));

TEST_P(JobClientProviderTest,
       UpdateJobVisibilityTimeoutWithInvalidDurationFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  *update_job_visibility_timeout_context_.request->mutable_job_id() = kJobId;
  *update_job_visibility_timeout_context_.request
       ->mutable_duration_to_update() = GetParam();
  *update_job_visibility_timeout_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_visibility_timeout_context_.callback =
      [this](AsyncContext<UpdateJobVisibilityTimeoutRequest,
                          UpdateJobVisibilityTimeoutResponse>&
                 update_job_visibility_timeout_context) {
        EXPECT_THAT(update_job_visibility_timeout_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_DURATION)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobVisibilityTimeout(
      update_job_visibility_timeout_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       UpdateJobVisibilityTimeoutWithUpdateMessageVisibilityTimeoutFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*queue_client_provider_, UpdateMessageVisibilityTimeout)
      .WillOnce([](auto& update_message_visibility_timeout_context) {
        update_message_visibility_timeout_context.result =
            FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT);
        update_message_visibility_timeout_context.Finish();
      });

  *update_job_visibility_timeout_context_.request->mutable_job_id() = kJobId;
  *update_job_visibility_timeout_context_.request
       ->mutable_duration_to_update() = kUpdatedVisibilityTimeout;
  *update_job_visibility_timeout_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  update_job_visibility_timeout_context_.callback =
      [this](AsyncContext<UpdateJobVisibilityTimeoutRequest,
                          UpdateJobVisibilityTimeoutResponse>&
                 update_job_visibility_timeout_context) {
        EXPECT_THAT(update_job_visibility_timeout_context.result,
                    ResultIs(FailureExecutionResult(SC_CPIO_INVALID_ARGUMENT)));
        finish_called_ = true;
      };

  job_client_provider_->UpdateJobVisibilityTimeout(
      update_job_visibility_timeout_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       DeleteOrphanedJobMessageWithJobEntryNotFoundSuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([](auto& get_database_item_context) {
        get_database_item_context.result = FailureExecutionResult(
            SC_NO_SQL_DATABASE_PROVIDER_RECORD_NOT_FOUND);
        get_database_item_context.Finish();
      });

  EXPECT_CALL(*queue_client_provider_,
              DeleteMessage(HasReceiptInfo(kQueueMessageReceiptInfo)))
      .WillOnce([](auto& delete_message_context) {
        delete_message_context.response = make_shared<DeleteMessageResponse>();
        delete_message_context.result = SuccessExecutionResult();
        delete_message_context.Finish();
      });

  *delete_orphaned_job_context_.request->mutable_job_id() = kJobId;
  *delete_orphaned_job_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  delete_orphaned_job_context_.callback =
      [this](AsyncContext<DeleteOrphanedJobMessageRequest,
                          DeleteOrphanedJobMessageResponse>&
                 delete_orphaned_job_context) {
        EXPECT_SUCCESS(delete_orphaned_job_context.result);
        finish_called_ = true;
      };

  job_client_provider_->DeleteOrphanedJobMessage(delete_orphaned_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       DeleteOrphanedJobMessageWithFinishedStateSuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_SUCCESS;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  EXPECT_CALL(*queue_client_provider_,
              DeleteMessage(HasReceiptInfo(kQueueMessageReceiptInfo)))
      .WillOnce([](auto& delete_message_context) {
        delete_message_context.response = make_shared<DeleteMessageResponse>();
        delete_message_context.result = SuccessExecutionResult();
        delete_message_context.Finish();
      });

  *delete_orphaned_job_context_.request->mutable_job_id() = kJobId;
  *delete_orphaned_job_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  delete_orphaned_job_context_.callback =
      [this](AsyncContext<DeleteOrphanedJobMessageRequest,
                          DeleteOrphanedJobMessageResponse>&
                 delete_orphaned_job_context) {
        EXPECT_SUCCESS(delete_orphaned_job_context.result);
        finish_called_ = true;
      };

  job_client_provider_->DeleteOrphanedJobMessage(delete_orphaned_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, DeleteOrphanedJobMessageWithMissingJobIdFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  *delete_orphaned_job_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  delete_orphaned_job_context_.callback =
      [this](AsyncContext<DeleteOrphanedJobMessageRequest,
                          DeleteOrphanedJobMessageResponse>&
                 delete_orphaned_job_context) {
        EXPECT_THAT(delete_orphaned_job_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_MISSING_JOB_ID)));
        finish_called_ = true;
      };

  job_client_provider_->DeleteOrphanedJobMessage(delete_orphaned_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       DeleteOrphanedJobMessageWithMissingReceiptInfoFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  *delete_orphaned_job_context_.request->mutable_job_id() = kJobId;
  delete_orphaned_job_context_.callback =
      [this](AsyncContext<DeleteOrphanedJobMessageRequest,
                          DeleteOrphanedJobMessageResponse>&
                 delete_orphaned_job_context) {
        EXPECT_THAT(delete_orphaned_job_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_RECEIPT_INFO)));
        finish_called_ = true;
      };

  job_client_provider_->DeleteOrphanedJobMessage(delete_orphaned_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, DeleteOrphanedJobMessageWithGetJobEntryFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([](auto& get_database_item_context) {
        get_database_item_context.result = FailureExecutionResult(
            SC_NO_SQL_DATABASE_PROVIDER_RECORD_CORRUPTED);
        get_database_item_context.Finish();
      });

  *delete_orphaned_job_context_.request->mutable_job_id() = kJobId;
  *delete_orphaned_job_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  delete_orphaned_job_context_.callback =
      [this](AsyncContext<DeleteOrphanedJobMessageRequest,
                          DeleteOrphanedJobMessageResponse>&
                 delete_orphaned_job_context) {
        EXPECT_THAT(delete_orphaned_job_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_NO_SQL_DATABASE_PROVIDER_RECORD_CORRUPTED)));
        finish_called_ = true;
      };

  job_client_provider_->DeleteOrphanedJobMessage(delete_orphaned_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       DeleteOrphanedJobMessageWithConvertJobEntryFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  *delete_orphaned_job_context_.request->mutable_job_id() = kJobId;
  *delete_orphaned_job_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  delete_orphaned_job_context_.callback =
      [this](AsyncContext<DeleteOrphanedJobMessageRequest,
                          DeleteOrphanedJobMessageResponse>&
                 delete_orphaned_job_context) {
        EXPECT_THAT(delete_orphaned_job_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_JOB_ITEM)));
        finish_called_ = true;
      };

  job_client_provider_->DeleteOrphanedJobMessage(delete_orphaned_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       DeleteOrphanedJobMessageWithDeleteMessageFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_SUCCESS;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  EXPECT_CALL(*queue_client_provider_,
              DeleteMessage(HasReceiptInfo(kQueueMessageReceiptInfo)))
      .WillOnce([](auto& delete_message_context) {
        delete_message_context.result =
            FailureExecutionResult(SC_CPIO_REQUEST_TIMEOUT);
        delete_message_context.Finish();
      });

  *delete_orphaned_job_context_.request->mutable_job_id() = kJobId;
  *delete_orphaned_job_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  delete_orphaned_job_context_.callback =
      [this](AsyncContext<DeleteOrphanedJobMessageRequest,
                          DeleteOrphanedJobMessageResponse>&
                 delete_orphaned_job_context) {
        EXPECT_THAT(delete_orphaned_job_context.result,
                    ResultIs(FailureExecutionResult(SC_CPIO_REQUEST_TIMEOUT)));
        finish_called_ = true;
      };

  job_client_provider_->DeleteOrphanedJobMessage(delete_orphaned_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, DeleteOrphanedJobMessageWithUnknownStateSuccess) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_UNKNOWN;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  EXPECT_CALL(*queue_client_provider_,
              DeleteMessage(HasReceiptInfo(kQueueMessageReceiptInfo)))
      .WillOnce([](auto& delete_message_context) {
        delete_message_context.response = make_shared<DeleteMessageResponse>();
        delete_message_context.result = SuccessExecutionResult();
        delete_message_context.Finish();
      });

  *delete_orphaned_job_context_.request->mutable_job_id() = kJobId;
  *delete_orphaned_job_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  delete_orphaned_job_context_.callback =
      [this](AsyncContext<DeleteOrphanedJobMessageRequest,
                          DeleteOrphanedJobMessageResponse>&
                 delete_orphaned_job_context) {
        EXPECT_SUCCESS(delete_orphaned_job_context.result);
        finish_called_ = true;
      };

  job_client_provider_->DeleteOrphanedJobMessage(delete_orphaned_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest,
       DeleteOrphanedJobMessageWithUnfinishedStateFailure) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  auto created_time = kCreatedTime;
  auto updated_time = kLastUpdatedTime;
  auto job_body = CreateHelloWorldProtoAsJsonString();
  auto job_status = JobStatus::JOB_STATUS_CREATED;
  auto retry_count = kDefaultRetryCount;
  auto processing_started_time = TimeUtil::SecondsToTimestamp(0);
  auto item =
      CreateJobAsDatabaseItem(job_body, job_status, created_time, updated_time,
                              retry_count, processing_started_time);

  EXPECT_CALL(*nosql_database_client_provider_,
              GetDatabaseItem(HasGetDatabaseItemParamsForGetJobById(
                  kJobsTableName, kJobId)))
      .WillOnce([&item](auto& get_database_item_context) {
        get_database_item_context.response =
            make_shared<GetDatabaseItemResponse>();
        *get_database_item_context.response->mutable_item() = item;
        get_database_item_context.result = SuccessExecutionResult();
        get_database_item_context.Finish();
      });

  *delete_orphaned_job_context_.request->mutable_job_id() = kJobId;
  *delete_orphaned_job_context_.request->mutable_receipt_info() =
      kQueueMessageReceiptInfo;
  delete_orphaned_job_context_.callback =
      [this](AsyncContext<DeleteOrphanedJobMessageRequest,
                          DeleteOrphanedJobMessageResponse>&
                 delete_orphaned_job_context) {
        EXPECT_THAT(delete_orphaned_job_context.result,
                    ResultIs(FailureExecutionResult(
                        SC_JOB_CLIENT_PROVIDER_INVALID_JOB_STATUS)));
        finish_called_ = true;
      };

  job_client_provider_->DeleteOrphanedJobMessage(delete_orphaned_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

TEST_F(JobClientProviderTest, PutJobSuccessWithTtl) {
  EXPECT_SUCCESS(job_client_provider_->Init());
  EXPECT_SUCCESS(job_client_provider_->Run());

  string server_job_id;
  EXPECT_CALL(*queue_client_provider_, EnqueueMessage)
      .WillOnce([&server_job_id](auto& enqueue_message_context) {
        enqueue_message_context.response =
            make_shared<EnqueueMessageResponse>();
        auto job_message_body =
            JobMessageBody(enqueue_message_context.request->message_body());
        EXPECT_EQ(job_message_body.job_id, kJobId);
        server_job_id = job_message_body.server_job_id;
        enqueue_message_context.result = SuccessExecutionResult();
        enqueue_message_context.Finish();
      });

  string job_body = CreateHelloWorldProtoAsJsonString();
  google::protobuf::Timestamp job_created_time_in_request;
  EXPECT_CALL(*nosql_database_client_provider_,
              CreateDatabaseItem(HasCreateItemParamsForJobCreations(
                  kJobsTableName, kJobId, job_body,
                  JobStatus_Name(JobStatus::JOB_STATUS_CREATED),
                  kDefaultTimestampValueInString,
                  kDefaultTimestampValueInString, kDefaultRetryCount)))
      .WillOnce([&job_created_time_in_request](
                    auto& create_database_item_context) {
        EXPECT_EQ(create_database_item_context.request->ttl().seconds(), 1);
        auto created_time_in_string =
            create_database_item_context.request->attributes(3).value_string();
        TimeUtil::FromString(created_time_in_string,
                             &job_created_time_in_request);
        create_database_item_context.response =
            make_shared<CreateDatabaseItemResponse>();
        create_database_item_context.result = SuccessExecutionResult();
        create_database_item_context.Finish();
      });

  put_job_context_.request->set_job_id(kJobId);
  *put_job_context_.request->mutable_job_body() = job_body;
  *put_job_context_.request->mutable_ttl() = TimeUtil::SecondsToDuration(1);
  Job job_output;
  put_job_context_.callback =
      [this, &server_job_id, &job_created_time_in_request](
          AsyncContext<PutJobRequest, PutJobResponse>& put_job_context) {
        EXPECT_SUCCESS(put_job_context.result);
        auto job_output = put_job_context.response->job();

        EXPECT_EQ(job_output.job_id(), kJobId);
        EXPECT_EQ(job_output.server_job_id(), server_job_id);

        auto job_body_output = job_output.job_body();
        HelloWorld hello_world_output;
        EXPECT_EQ(JsonStringToMessage(job_body_output, &hello_world_output),
                  OkStatus());
        EXPECT_EQ(hello_world_output.name(), kHelloWorldName);
        EXPECT_EQ(hello_world_output.id(), kHelloWorldId);
        EXPECT_EQ(hello_world_output.created_time(),
                  kHelloWorldProtoCreatedTime);

        EXPECT_EQ(job_output.job_status(), JobStatus::JOB_STATUS_CREATED);
        EXPECT_EQ(job_output.created_time(), job_created_time_in_request);
        EXPECT_EQ(job_output.updated_time(), job_created_time_in_request);
        EXPECT_EQ(job_output.retry_count(), kDefaultRetryCount);
        EXPECT_EQ(job_output.processing_started_time(), kDefaultTimestamp);
        finish_called_ = true;
      };

  job_client_provider_->PutJob(put_job_context_);

  WaitUntil([this]() { return finish_called_.load(); });
}

}  // namespace google::scp::cpio::client_providers::job_client::test
