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

package com.google.scp.operator.shared.dao.metadatadb.testing;

import static com.google.cmrt.sdk.job_service.v1.JobStatus.JOB_STATUS_CREATED;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.cmrt.sdk.job_service.v1.Job;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.scp.operator.protos.shared.backend.ErrorCountProto.ErrorCount;
import com.google.scp.operator.protos.shared.backend.JobErrorCategoryProto.JobErrorCategory;
import com.google.scp.operator.protos.shared.backend.RequestInfoProto.RequestInfo;
import com.google.scp.operator.protos.shared.backend.metadatadb.JobMetadataProto.JobMetadata;
import com.google.scp.shared.proto.ProtoUtil;
import java.time.Instant;
import java.util.UUID;

/** Provides fake Job related objects for testing. */
public final class JobGenerator {

  private static final String DATA_HANDLE = "dataHandle";
  private static final String DATA_HANDLE_BUCKET = "bucket";
  private static final String POSTBACK_URL = "http://postback.com";
  private static final String ACCOUNT_IDENTITY = "service-account@testing.com";
  private static final String ATTRIBUTION_REPORT_TO = "foo.com";
  private static final Integer DEBUG_PRIVACY_BUDGET_LIMIT = 5;
  private static final Instant REQUEST_RECEIVED_AT = Instant.parse("2019-10-01T08:25:24.00Z");
  private static final Instant REQUEST_UPDATED_AT = Instant.parse("2019-10-01T08:29:24.00Z");

  private static final String JOB_PARAM_ATTRIBUTION_REPORT_TO = "attribution_report_to";
  private static final String JOB_PARAM_DEBUG_PRIVACY_BUDGET_LIMIT = "debug_privacy_budget_limit";

  private static final com.google.scp.operator.protos.shared.backend.JobStatusProto.JobStatus
      JOB_STATUS_NEW_IMAGE_SHARED =
          com.google.scp.operator.protos.shared.backend.JobStatusProto.JobStatus.IN_PROGRESS;
  private static final com.google.scp.operator.protos.shared.backend.JobStatusProto.JobStatus
      JOB_STATUS_OLD_IMAGE_SHARED =
          com.google.scp.operator.protos.shared.backend.JobStatusProto.JobStatus.RECEIVED;
  private static final ImmutableList<Instant> REPORTING_WINDOWS =
      ImmutableList.of(
          Instant.parse("2019-09-01T02:00:00.00Z"), Instant.parse("2019-09-01T02:30:00.00Z"));
  private static final int RECORD_VERSION_NEW_IMAGE = 2;
  private static final int RECORD_VERSION_OLD_IMAGE = 1;
  private static final ImmutableList<ErrorCount> ERROR_COUNTS =
      ImmutableList.of(
          ErrorCount.newBuilder()
              .setCategory(JobErrorCategory.DECRYPTION_ERROR.name())
              .setCount(5L)
              .setDescription("Decryption error.")
              .build(),
          ErrorCount.newBuilder()
              .setCategory(JobErrorCategory.GENERAL_ERROR.name())
              .setCount(12L)
              .setDescription("General error.")
              .build(),
          ErrorCount.newBuilder()
              .setCategory(JobErrorCategory.NUM_REPORTS_WITH_ERRORS.name())
              .setCount(17L)
              .setDescription("Total number of reports with error.")
              .build());
  private static final com.google.scp.operator.protos.shared.backend.ErrorSummaryProto.ErrorSummary
      ERROR_SUMMARY_SHARED =
          com.google.scp.operator.protos.shared.backend.ErrorSummaryProto.ErrorSummary.newBuilder()
              .addAllErrorCounts(ERROR_COUNTS)
              .build();

  private static final com.google.scp.operator.protos.shared.backend.ResultInfoProto.ResultInfo
      RESULT_INFO_SHARED =
          com.google.scp.operator.protos.shared.backend.ResultInfoProto.ResultInfo.newBuilder()
              .setErrorSummary(ERROR_SUMMARY_SHARED)
              .setFinishedAt(ProtoUtil.toProtoTimestamp(Instant.parse("2019-10-01T13:25:24.00Z")))
              .setReturnCode(
                  com.google.scp.operator.protos.shared.backend.ReturnCodeProto.ReturnCode.SUCCESS
                      .name())
              .setReturnMessage("Aggregation job successfully processed")
              .putAllResultMetadata(
                  ImmutableMap.of("number of files processed", "30", "job duration", "4.5 hours"))
              .build();

  private static final Instant CREATED_TIME = Instant.parse("2023-10-01T08:25:24.00Z");
  private static final Instant UPDATED_TIME = Instant.parse("2023-10-01T08:29:56.00Z");

  /** Creates an instance of the {@code JobMetadata} class with fake values. */
  public static JobMetadata createFakeJobMetadata(String requestId) {
    com.google.scp.operator.protos.shared.backend.JobKeyProto.JobKey jobKey =
        com.google.scp.operator.protos.shared.backend.JobKeyProto.JobKey.newBuilder()
            .setJobRequestId(requestId)
            .build();

    com.google.scp.operator.protos.shared.backend.CreateJobRequestProto.CreateJobRequest
        createJobRequest = createFakeCreateJobRequestShared(requestId);

    RequestInfo requestInfo = createFakeRequestInfo(requestId);

    JobMetadata jobMetadata =
        JobMetadata.newBuilder()
            .setCreateJobRequest(createJobRequest)
            .setJobStatus(JOB_STATUS_NEW_IMAGE_SHARED)
            .setRequestReceivedAt(ProtoUtil.toProtoTimestamp(REQUEST_RECEIVED_AT))
            .setRequestUpdatedAt(ProtoUtil.toProtoTimestamp(REQUEST_UPDATED_AT))
            .setNumAttempts(0)
            .setJobKey(jobKey)
            .setRecordVersion(RECORD_VERSION_NEW_IMAGE)
            .setRequestInfo(requestInfo)
            .setResultInfo(RESULT_INFO_SHARED)
            .build();

    return jobMetadata;
  }

  /** Creates an instance of the {@code CreateJobRequest} class with fake values. */
  public static com.google.scp.operator.protos.shared.backend.CreateJobRequestProto.CreateJobRequest
      createFakeCreateJobRequestShared(String requestId) {
    com.google.scp.operator.protos.shared.backend.CreateJobRequestProto.CreateJobRequest
        createJobRequest =
            com.google.scp.operator.protos.shared.backend.CreateJobRequestProto.CreateJobRequest
                .newBuilder()
                .setJobRequestId(requestId)
                .setInputDataBlobPrefix(DATA_HANDLE)
                .setInputDataBucketName(DATA_HANDLE_BUCKET)
                .setOutputDataBlobPrefix(DATA_HANDLE)
                .setOutputDataBucketName(DATA_HANDLE_BUCKET)
                .setPostbackUrl(POSTBACK_URL)
                .setAttributionReportTo(ATTRIBUTION_REPORT_TO)
                .setDebugPrivacyBudgetLimit(DEBUG_PRIVACY_BUDGET_LIMIT)
                .putAllJobParameters(
                    ImmutableMap.of(
                        JOB_PARAM_ATTRIBUTION_REPORT_TO,
                        ATTRIBUTION_REPORT_TO,
                        JOB_PARAM_DEBUG_PRIVACY_BUDGET_LIMIT,
                        DEBUG_PRIVACY_BUDGET_LIMIT.toString()))
                .build();

    return createJobRequest;
  }

  public static RequestInfo createFakeRequestInfo(String requestId) {
    RequestInfo requestInfo =
        RequestInfo.newBuilder()
            .setJobRequestId(requestId)
            .setInputDataBlobPrefix(DATA_HANDLE)
            .setInputDataBucketName(DATA_HANDLE_BUCKET)
            .setOutputDataBlobPrefix(DATA_HANDLE)
            .setOutputDataBucketName(DATA_HANDLE_BUCKET)
            .setPostbackUrl(POSTBACK_URL)
            .putAllJobParameters(
                ImmutableMap.of(
                    JOB_PARAM_ATTRIBUTION_REPORT_TO,
                    ATTRIBUTION_REPORT_TO,
                    JOB_PARAM_DEBUG_PRIVACY_BUDGET_LIMIT,
                    DEBUG_PRIVACY_BUDGET_LIMIT.toString()))
            .build();
    return requestInfo;
  }

  public static RequestInfo createFakeRequestInfoWithAccountIdentity(String requestId) {
    RequestInfo requestInfo =
        createFakeRequestInfo(requestId).toBuilder().setAccountIdentity(ACCOUNT_IDENTITY).build();
    return requestInfo;
  }

  /** Creates an instance of the {@code ResultInfo} class with fake values. */
  public static com.google.scp.operator.protos.shared.backend.ResultInfoProto.ResultInfo
      createFakeResultInfoShared() {
    return RESULT_INFO_SHARED;
  }

  /** Creates an instance of the {@code ErrorSummary} class with fake values. */
  public static com.google.scp.operator.protos.shared.backend.ErrorSummaryProto.ErrorSummary
      createFakeErrorSummaryShared() {
    return ERROR_SUMMARY_SHARED;
  }

  /** Creates a list of {@code ErrorCount} instances with fake values. */
  public static ImmutableList<ErrorCount> createFakeErrorCounts() {
    return ERROR_COUNTS;
  }

  /** Creates an instance of the {@code Job} class with fake values. */
  public static Job createFakeJob(String jobId) {
    HelloWorld helloworld = HelloWorld.newBuilder().setName("myname").setId(694324).build();
    String jobBody = new String(helloworld.toByteArray(), UTF_8);
    Job job =
        Job.newBuilder()
            .setJobId(jobId)
            .setServerJobId(UUID.randomUUID().toString())
            .setJobBody(jobBody)
            .setJobStatus(JOB_STATUS_CREATED)
            .setCreatedTime(ProtoUtil.toProtoTimestamp(CREATED_TIME))
            .setUpdatedTime(ProtoUtil.toProtoTimestamp(UPDATED_TIME))
            .setRetryCount(0)
            .build();

    return job;
  }
}
