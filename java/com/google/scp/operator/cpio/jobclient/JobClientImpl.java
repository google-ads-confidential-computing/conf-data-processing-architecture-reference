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

package com.google.scp.operator.cpio.jobclient;

import static com.google.scp.operator.shared.model.BackendModelUtil.toJobKeyString;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.ENABLE_LEGACY_METRICS;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.ENABLE_REMOTE_METRIC_AGGREGATION;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.JOB_COMPLETION_NOTIFICATIONS_TOPIC_ID;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.NOTIFICATIONS_TOPIC_ID;
import static java.util.function.Predicate.not;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.operator.cpio.jobclient.JobHandlerModule.JobClientJobValidatorsBinding;
import com.google.scp.operator.cpio.jobclient.model.ErrorReason;
import com.google.scp.operator.cpio.jobclient.model.GetJobRequest;
import com.google.scp.operator.cpio.jobclient.model.Job;
import com.google.scp.operator.cpio.jobclient.model.JobResult;
import com.google.scp.operator.cpio.jobclient.model.JobRetryRequest;
import com.google.scp.operator.cpio.lifecycleclient.LifecycleClient;
import com.google.scp.operator.cpio.lifecycleclient.LifecycleClient.LifecycleClientException;
import com.google.scp.operator.cpio.metricclient.MetricClient;
import com.google.scp.operator.cpio.metricclient.MetricClient.MetricClientException;
import com.google.scp.operator.cpio.metricclient.model.Annotations.LegacyMetricClient;
import com.google.scp.operator.cpio.metricclient.model.CustomMetric;
import com.google.scp.operator.cpio.metricclient.model.MetricType;
import com.google.scp.operator.cpio.notificationclient.NotificationClient;
import com.google.scp.operator.cpio.notificationclient.NotificationClient.NotificationClientException;
import com.google.scp.operator.cpio.notificationclient.model.PublishMessageRequest;
import com.google.scp.operator.protos.shared.backend.CreateJobRequestProto.CreateJobRequest;
import com.google.scp.operator.protos.shared.backend.ErrorSummaryProto.ErrorSummary;
import com.google.scp.operator.protos.shared.backend.JobKeyProto.JobKey;
import com.google.scp.operator.protos.shared.backend.JobStatusProto.JobStatus;
import com.google.scp.operator.protos.shared.backend.RequestInfoProto.RequestInfo;
import com.google.scp.operator.protos.shared.backend.ResultInfoProto.ResultInfo;
import com.google.scp.operator.protos.shared.backend.ReturnCodeProto.ReturnCode;
import com.google.scp.operator.protos.shared.backend.jobnotification.JobNotificationEvent;
import com.google.scp.operator.protos.shared.backend.jobqueue.JobQueueProto.JobQueueItem;
import com.google.scp.operator.protos.shared.backend.metadatadb.JobMetadataProto.JobMetadata;
import com.google.scp.operator.shared.dao.jobqueue.common.JobQueue;
import com.google.scp.operator.shared.dao.jobqueue.common.JobQueue.JobQueueException;
import com.google.scp.operator.shared.dao.metadatadb.common.JobMetadataDb;
import com.google.scp.operator.shared.dao.metadatadb.common.JobMetadataDb.JobMetadataConflictException;
import com.google.scp.operator.shared.dao.metadatadb.common.JobMetadataDb.JobMetadataDbException;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.ParameterClient.ParameterClientException;
import com.google.scp.shared.proto.ProtoUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

/**
 * Job client for getting jobs from {@code JobQueue} and update job status in {@code JobMetadataDb}.
 *
 * <p>Job client gets jobs from {@code JobQueue}, and caches the jobs in-memory. Once the processing
 * of the job is finished, the worker can request the job client to mark the job as completed in
 * {@code JobQueue} and {@code JobMetadataDb}.
 */
@NotThreadSafe
public final class JobClientImpl implements JobClient {

  // TODO: change to cloud logger
  private static final Logger logger = Logger.getLogger(JobClientImpl.class.getName());

  // Defined by the privacy budgeting manager:
  // https://docs.google.com/document/d/1Cv1BgSB1vK5KysTOap46BDJeYEUyMfo_NHdp7kdv6J4/edit?resourcekey=0-wsNR34Ax0mfvdO4wSeOGgg#bookmark=id.ah724n4y05sm
  /** Fixed reporting window interval, set to 1hr. */
  static final Duration REPORTING_WINDOW_FIXED_LENGTH = Duration.of(1, ChronoUnit.HOURS);

  /** Namespace used to track job validation failures. */
  static final String METRIC_NAMESPACE = "scp/jobclient";

  static final String NEW_METRIC_NAMESPACE = "scp/jobclient/metrics";

  private final JobQueue jobQueue;
  private final JobMetadataDb metadataDb;
  private final JobPullBackoff pullBackoff;
  ImmutableList<JobValidator> jobValidators;
  private final LifecycleClient lifecycleClient;
  private final MetricClient metricClient;
  private final MetricClient legacyMetricClient;
  private final boolean enableRemoteAggregationMetrics;
  private final boolean enableLegacyMetrics;
  private final Optional<NotificationClient> notificationClient;
  private final ParameterClient parameterClient;
  private boolean pollForJob = false;
  private Clock clock;

  // TODO: we can also put the JobQueueItem in the job to avoid keeping a cache,
  // sub-modules (JobQueue and JobMetadataDb) are thread-safe, removing job cache
  // makes this class thread-safe.
  private ConcurrentHashMap<String, JobQueueItem> cache = new ConcurrentHashMap<>();

  /** Creates a new instance of the {@code JobClientImpl} class. */
  @Inject
  JobClientImpl(
      JobQueue jobQueue,
      JobMetadataDb metadataDb,
      JobPullBackoff pullBackoff,
      @JobClientJobValidatorsBinding ImmutableList<JobValidator> jobValidators,
      MetricClient metricClient,
      LifecycleClient lifecycleClient,
      Optional<NotificationClient> notificationClient,
      ParameterClient parameterClient,
      Clock clock,
      @LegacyMetricClient MetricClient legacyMetricClient)
      throws ParameterClientException {
    this.jobQueue = jobQueue;
    this.metadataDb = metadataDb;
    this.pullBackoff = pullBackoff;
    this.jobValidators = jobValidators;
    this.metricClient = metricClient;
    this.lifecycleClient = lifecycleClient;
    this.notificationClient = notificationClient;
    this.parameterClient = parameterClient;
    this.clock = clock;

    this.enableLegacyMetrics =
        Boolean.valueOf(parameterClient.getParameter(ENABLE_LEGACY_METRICS.name()).orElse("true"));
    this.enableRemoteAggregationMetrics =
        Boolean.valueOf(
            parameterClient.getParameter(ENABLE_REMOTE_METRIC_AGGREGATION.name()).orElse("false"));

    this.legacyMetricClient = legacyMetricClient;

    startJobProcessingExtender();
  }

  @Override
  public Optional<Job> getJob(GetJobRequest getJobRequest) throws JobClientException {
    pollForJob = true;
    Optional<JobQueueItem> queueItem = Optional.empty();
    Optional<JobMetadata> metadata = Optional.empty();
    Optional<Job> job = Optional.empty();
    try {
      while (pollForJob) {
        if (lifecycleClient.handleScaleInLifecycleAction()) {
          // Adding some sleep to give the instance some time to terminate.
          Thread.sleep(5000L);
          return Optional.empty();
        }
        queueItem = jobQueue.receiveJob();
        if (queueItem.isEmpty()) {
          pollForJob = pullBackoff.get();
          continue;
        }

        metadata = getJobMetadata(queueItem.get());
        job =
            metadata.isPresent()
                ? Optional.of(buildJob(queueItem.get(), metadata.get()))
                : Optional.empty();

        // Delete job queue message if there is a server job id mismatch since the JobMetadata entry
        // for the request job id corresponds to a different queue message.
        if (metadata.isPresent()) {
          if (!metadata.get().getServerJobId().isEmpty()
              && !queueItem.get().getServerJobId().isEmpty()
              && !metadata.get().getServerJobId().equals(queueItem.get().getServerJobId())) {
            logger.info(
                String.format(
                    "Deleting job queue message because of server job id mismatch. Server job id"
                        + " from metadata db: %s. Server job id from job queue: %s.",
                    metadata.get().getServerJobId(), queueItem.get().getServerJobId()));
            jobQueue.acknowledgeJobCompletion(queueItem.get());
            continue;
          }
        }

        String jobKey = queueItem.get().getJobKeyString();
        Optional<JobValidator> failedCheck = performInitialChecks(job, jobKey);
        if (failedCheck.isPresent()) {
          if (failedCheck.get().reportValidationError()) {
            reportFailedCheck(
                job.get(),
                failedCheck.get().getValidationErrorMessage(),
                failedCheck.get().getValidationErrorReturnCode());
          }

          try {
            JobResult.Builder jobResultBuilder =
                JobResult.builder()
                    .setJobKey(job.get().jobKey())
                    .setResultInfo(metadata.get().getResultInfo());
            var getTopicIdFunc = getJobRequest.getJobCompletionNotificationTopicIdFunc();
            if (getTopicIdFunc.isPresent()) {
              String topicId = getTopicIdFunc.get().apply(job.get());
              jobResultBuilder.setTopicId(Optional.of(topicId));
            }
            sendJobCompletedNotification(jobResultBuilder.build());
          } catch (Exception e) {
            logger.warning(String.format("Could not send job completion notification: %s", e));
          }

          jobQueue.acknowledgeJobCompletion(queueItem.get());

          try {
            if (enableLegacyMetrics) {
              CustomMetric metric =
                  CustomMetric.builder()
                      .setNameSpace(METRIC_NAMESPACE)
                      .setName("JobValidationFailure")
                      .setValue(1.0)
                      .setUnit("Count")
                      .setMetricType(MetricType.DOUBLE_GAUGE)
                      .addLabel("Validator", failedCheck.get().getClass().getSimpleName())
                      .build();
              legacyMetricClient.recordMetric(metric);
            }
            if (enableRemoteAggregationMetrics) {
              // A dual write for same data point to two metrics due to the change of Metric type.
              // TODO: Remove the metric above once the new metric is stable.
              CustomMetric newValidationFailureMetric =
                  CustomMetric.builder()
                      .setNameSpace(NEW_METRIC_NAMESPACE)
                      .setName("JobValidationFailureCounter")
                      .setValue(1.0)
                      .setUnit("Count")
                      .setMetricType(MetricType.DOUBLE_COUNTER)
                      .addLabel("Validator", failedCheck.get().getClass().getSimpleName())
                      .build();
              metricClient.recordMetric(newValidationFailureMetric);
            }
          } catch (MetricClientException e) {
            logger.warning(String.format("Could not record JobValidationFailure metric.\n%s", e));
          }
          continue;
        }

        if (isDuplicateJob(job)) {
          logger.info("Skip processing for duplicate job: " + job.get().jobKey());
          continue;
        }

        pollForJob = false;
      }

      if (queueItem.isEmpty()) {
        // Pull backoff depleted.
        return Optional.empty();
      }

      Timestamp processingStartTime = ProtoUtil.toProtoTimestamp(Instant.now(clock));
      metadataDb.updateJobMetadata(
          metadata.get().toBuilder()
              .setJobStatus(JobStatus.IN_PROGRESS)
              .setRequestProcessingStartedAt(processingStartTime)
              .setNumAttempts(metadata.get().getNumAttempts() + 1)
              .build());
      // Cache job in memory, to be able to retrieve the queue item when job completes.
      cache.put(
          toJobKeyString(metadata.get().getJobKey()),
          queueItem.get().toBuilder().setJobProcessingStartTime(processingStartTime).build());

      logger.info(
          String.format(
              "Successfully pulled a job from sqs and ddb, job_id=%s.",
              toJobKeyString(metadata.get().getJobKey())));

      return job;
    } catch (JobQueueException
        | JobMetadataDbException
        | JobMetadataConflictException
        | LifecycleClientException
        | InterruptedException e) {
      logger.log(Level.SEVERE, "Failed to pull new job from job queue.", e);
      recordJobClientError(ErrorReason.JOB_PULL_FAILED);
      throw new JobClientException(e, ErrorReason.JOB_PULL_FAILED);
    }
  }

  private Optional<JobMetadata> getJobMetadata(JobQueueItem queueItem)
      throws JobMetadataDbException, InterruptedException {
    Optional<JobMetadata> metadata;
    // retry to account for delay in writing to DB
    int maxRetries = 6;
    for (int retryCount = 0; retryCount <= maxRetries; retryCount++) {
      metadata = metadataDb.getJobMetadata(queueItem.getJobKeyString());
      if (metadata.isPresent()) {
        return metadata;
      } else if (retryCount < maxRetries) {
        Thread.sleep((long) (1000L * Math.pow(2, retryCount + 1)));
      }
    }
    return Optional.empty();
  }

  @Override
  public void returnJobForRetry(JobRetryRequest jobRetryRequest) throws JobClientException {
    try {
      // Verify the JobKey is in the cache.
      if (!cache.containsKey(toJobKeyString(jobRetryRequest.getJobKey()))) {
        recordJobClientError(ErrorReason.JOB_RECEIPT_HANDLE_NOT_FOUND);
        throw new JobClientException(
            String.format(
                "Job cannot be released. In-memory cache does not contain job key '%s'",
                toJobKeyString(jobRetryRequest.getJobKey())),
            ErrorReason.JOB_RECEIPT_HANDLE_NOT_FOUND);
      }
      // Make sure the current metadata entry for the job is in the IN_PROGRESS state.
      Optional<JobMetadata> currentMetadata =
          metadataDb.getJobMetadata(jobRetryRequest.getJobKey().getJobRequestId());
      if (currentMetadata.isEmpty()) {
        recordJobClientError(ErrorReason.JOB_METADATA_NOT_FOUND);
        throw new JobClientException(
            String.format(
                "Job cannot be released. Metadata entry for job '%s' was not found.",
                toJobKeyString(jobRetryRequest.getJobKey())),
            ErrorReason.JOB_METADATA_NOT_FOUND);
      }
      if (currentMetadata.get().getJobStatus() != JobStatus.IN_PROGRESS) {
        throw new JobClientException(
            String.format(
                "Job cannot be released. Metadata entry for job '%s' indicates job is in status %s,"
                    + " but expected to be IN_PROGRESS.",
                toJobKeyString(jobRetryRequest.getJobKey()), currentMetadata.get().getJobStatus()),
            ErrorReason.WRONG_JOB_STATUS);
      }
      // If delay is present, verify that it is range [0:10] minutes.
      if (jobRetryRequest.getDelay().isPresent()) {
        if (jobRetryRequest.getDelay().get().isNegative()
            || jobRetryRequest.getDelay().get().getSeconds() > 600) {
          recordJobClientError(ErrorReason.JOB_DELAY_OUT_OF_RANGE);
          throw new JobClientException(
              String.format(
                  "Job cannot be released. Duration for job %s must be between zero and 10"
                      + " minutes.",
                  toJobKeyString(jobRetryRequest.getJobKey())),
              ErrorReason.JOB_RECEIPT_HANDLE_NOT_FOUND);
        }
      }

      // Reset the job in the metadata db to RECEIVED and update ResultInfo if provided.
      JobMetadata.Builder builder = currentMetadata.get().toBuilder();
      builder.setJobStatus(JobStatus.RECEIVED);
      if (jobRetryRequest.getResultInfo().isPresent()) {
        builder.setResultInfo(jobRetryRequest.getResultInfo().get());
      }
      JobMetadata updatedMetadata = builder.build();
      metadataDb.updateJobMetadata(updatedMetadata);

      // Modify the remaining processing time
      Duration delay =
          jobRetryRequest.getDelay().isPresent()
              ? jobRetryRequest.getDelay().get()
              : Duration.ofSeconds(0);
      jobQueue.modifyJobProcessingTime(
          cache.get(toJobKeyString(jobRetryRequest.getJobKey())), delay);

      // Remove cache entry for the job so it does not continue to be extended.
      cache.remove(toJobKeyString(jobRetryRequest.getJobKey()));

      logger.info(
          String.format(
              "Successfully released job %s back to the queue for retry.",
              toJobKeyString(jobRetryRequest.getJobKey())));
    } catch (JobQueueException | JobMetadataDbException | JobMetadataConflictException e) {
      logger.log(
          Level.SEVERE,
          String.format("Failed to release job '%s'.", toJobKeyString(jobRetryRequest.getJobKey())),
          e);
      recordJobClientError(ErrorReason.RETURN_JOB_FOR_RETRY_FAILED);
      throw new JobClientException(e, ErrorReason.RETURN_JOB_FOR_RETRY_FAILED);
    }
  }

  @Override
  public void markJobCompleted(JobResult jobResult) throws JobClientException {
    String jobKey = toJobKeyString(jobResult.jobKey());
    try {
      if (!cache.containsKey(jobKey)) {
        recordJobClientError(ErrorReason.JOB_RECEIPT_HANDLE_NOT_FOUND);
        throw new JobClientException(
            String.format("In-memory cache does not contain job key '%s'", jobKey),
            ErrorReason.JOB_RECEIPT_HANDLE_NOT_FOUND);
      }

      // get the current metadata entry to make sure job is in the IN_PROGRESS state before marking
      // completion
      Optional<JobMetadata> currentMetadata = metadataDb.getJobMetadata(jobKey);

      if (currentMetadata.isEmpty()) {
        recordJobClientError(ErrorReason.JOB_METADATA_NOT_FOUND);
        throw new JobClientException(
            String.format(
                "Metadata entry for job '%s' was not found, " + "cannot mark job as completed.",
                jobKey),
            ErrorReason.JOB_METADATA_NOT_FOUND);
      }

      if (currentMetadata.get().getJobStatus() != JobStatus.IN_PROGRESS) {
        throw new JobClientException(
            String.format(
                "Metadata entry for job '%s' indicates job is in status %s, "
                    + "but expected to be IN_PROGRESS.",
                jobKey, currentMetadata.get().getJobStatus()),
            ErrorReason.WRONG_JOB_STATUS);
      }

      // Publish a notification of job completion.
      sendJobCompletedNotification(jobResult);

      // Update the metadata db with the result
      JobMetadata updatedMetadata =
          currentMetadata.get().toBuilder()
              .setJobStatus(JobStatus.FINISHED)
              .setResultInfo(jobResult.resultInfo())
              .build();
      metadataDb.updateJobMetadata(updatedMetadata);

      // Acknowledge that the job has been completed to the queue
      jobQueue.acknowledgeJobCompletion(cache.get(jobKey));

      // Remove cache entry for the job once it is successfully marked as completed.
      cache.remove(jobKey);

      logger.info(String.format("Successfully marked job %s as completed.", jobKey));
    } catch (JobQueueException | JobMetadataDbException | JobMetadataConflictException e) {
      logger.log(Level.SEVERE, String.format("Failed to mark job '%s' as completed.", jobKey), e);
      recordJobClientError(ErrorReason.JOB_MARK_COMPLETION_FAILED);
      throw new JobClientException(e, ErrorReason.JOB_MARK_COMPLETION_FAILED);
    }
  }

  private void sendJobCompletedNotification(JobResult jobResult) throws JobClientException {
    if (notificationClient.isPresent()) {
      try {
        String jobKey = toJobKeyString(jobResult.jobKey());
        Optional<String> topicIdInJobResult = jobResult.topicId();
        Optional<String> globalTopicId =
            parameterClient.getParameter(NOTIFICATIONS_TOPIC_ID.name());
        Optional<String> internalTopicId =
            parameterClient.getParameter(JOB_COMPLETION_NOTIFICATIONS_TOPIC_ID.name());
        if (!internalTopicId.isPresent()) {
          if (isTopicIdPresent(topicIdInJobResult) || isTopicIdPresent(globalTopicId)) {
            recordJobClientError(ErrorReason.JOB_COMPLETION_NOTIFICATIONS_NOT_ENABLED);
            throw new JobClientException(
                String.format(
                    "Try to send out notification for job completion but it is not enabled. JobKey:"
                        + " '%s'",
                    jobKey),
                ErrorReason.JOB_COMPLETION_NOTIFICATIONS_NOT_ENABLED);
          }
        } else {
          if (isTopicIdPresent(globalTopicId)) {
            publishJobCompletionMessage(jobKey, globalTopicId.get(), internalTopicId.get());
          }
          if (isTopicIdPresent(topicIdInJobResult)) {
            publishJobCompletionMessage(jobKey, topicIdInJobResult.get(), internalTopicId.get());
          }
        }
      } catch (ParameterClientException e) {
        logger.log(Level.SEVERE, "Failed to read parameter.", e);
        throw new JobClientException(e, ErrorReason.UNSPECIFIED_ERROR);
      }
    }
  }

  private void publishJobCompletionMessage(String jobKey, String topicId, String internalTopicId)
      throws JobClientException {
    var jobNotificationEvent =
        JobNotificationEvent.newBuilder()
            .setJobId(jobKey)
            .setJobStatus(JobStatus.FINISHED)
            .setTopicId(topicId)
            .build();
    String messageBody;
    try {
      messageBody = JsonFormat.printer().print(jobNotificationEvent);
    } catch (InvalidProtocolBufferException e) {
      logger.log(
          Level.SEVERE,
          String.format(
              "Failed to parse job notification event %s", jobNotificationEvent.toString()),
          e);
      throw new JobClientException(e, ErrorReason.UNSPECIFIED_ERROR);
    }
    PublishMessageRequest publishMessageRequest =
        PublishMessageRequest.builder()
            .setNotificationTopic(internalTopicId)
            .setMessageBody(messageBody)
            .build();
    try {
      notificationClient.get().publishMessage(publishMessageRequest);
    } catch (NotificationClientException e) {
      logger.log(
          Level.SEVERE, String.format("Failed to publish notification for job %s", jobKey), e);
      throw new JobClientException(e, ErrorReason.JOB_COMPLETION_NOTIFICATION_FAILED);
    }
  }

  private boolean isTopicIdPresent(Optional<String> topicId) {
    return !topicId.filter(not(String::isBlank)).isEmpty();
  }

  @Deprecated
  public void appendJobErrorMessage(JobKey jobKey, String error) throws JobClientException {
    try {
      // Get the current metadata entry to make sure job is in the IN_PROGRESS state before
      // appending error message.
      Optional<JobMetadata> currentMetadata = metadataDb.getJobMetadata(jobKey.getJobRequestId());

      if (currentMetadata.isEmpty()) {
        recordJobClientError(ErrorReason.JOB_METADATA_NOT_FOUND);
        throw new JobClientException(
            String.format(
                "Metadata entry for job '%s' was not found, cannot update error summary.",
                toJobKeyString(jobKey)),
            ErrorReason.JOB_METADATA_NOT_FOUND);
      }

      if (currentMetadata.get().getJobStatus() != JobStatus.IN_PROGRESS) {
        throw new JobClientException(
            String.format(
                "Metadata entry for job '%s' indicates job is in status %s, "
                    + "but expected to be IN_PROGRESS.",
                toJobKeyString(jobKey), currentMetadata.get().getJobStatus()),
            ErrorReason.WRONG_JOB_STATUS);
      }

      ErrorSummary updatedErrorSummary =
          currentMetadata.get().getResultInfo().getErrorSummary().toBuilder()
              .addErrorMessages(error)
              .build();
      ResultInfo updatedResultInfo =
          currentMetadata.get().getResultInfo().toBuilder()
              .setErrorSummary(updatedErrorSummary)
              .setFinishedAt(ProtoUtil.toProtoTimestamp(Instant.now(clock)))
              .build();

      JobMetadata updatedMetadata =
          currentMetadata.get().toBuilder().setResultInfo(updatedResultInfo).build();
      metadataDb.updateJobMetadata(updatedMetadata);

      logger.info(
          String.format("Successfully updated error summary for job '%s'", toJobKeyString(jobKey)));

    } catch (JobMetadataDbException | JobMetadataConflictException e) {
      logger.log(
          Level.SEVERE,
          String.format("Failed to update error summary for job '%s'", toJobKeyString(jobKey)),
          e);
      recordJobClientError(ErrorReason.JOB_ERROR_SUMMARY_UPDATE_FAILED);
      throw new JobClientException(e, ErrorReason.JOB_ERROR_SUMMARY_UPDATE_FAILED);
    }
  }

  /**
   * Performs initial checks on the job.
   *
   * <p>Returns an {@code Optional} of {@code JobValidator}, representing the first validation
   * failing.
   */
  Optional<JobValidator> performInitialChecks(Optional<Job> job, String jobKeyString) {
    Optional<JobValidator> failedValidator =
        jobValidators.stream()
            .filter(validator -> !validator.validate(job, jobKeyString))
            .findFirst();
    if (failedValidator.isPresent()) {
      logger.warning(
          String.format(
              "Job '%s' failed validation step '%s'.",
              jobKeyString, failedValidator.get().getDescription()));
    }
    return failedValidator;
  }

  /** Reports failed check to user and marks job as completed. */
  void reportFailedCheck(Job job, String errorMessage, ReturnCode returnCode)
      throws JobClientException {
    try {
      Optional<JobMetadata> jobMetadata = metadataDb.getJobMetadata(toJobKeyString(job.jobKey()));
      if (jobMetadata.isPresent()) {
        // Only updates the return code, errorMessage, and finishedAt time
        ResultInfo existingResultInfo = jobMetadata.get().getResultInfo();
        JobMetadata updatedMetadata =
            jobMetadata.get().toBuilder()
                .setJobStatus(JobStatus.FINISHED)
                .setResultInfo(
                    existingResultInfo.toBuilder()
                        .setReturnCode(returnCode.name())
                        .setReturnMessage(errorMessage)
                        .setFinishedAt(ProtoUtil.toProtoTimestamp(Instant.now(clock))))
                .build();
        metadataDb.updateJobMetadata(updatedMetadata);
      } else {
        logger.log(
            Level.SEVERE,
            String.format(
                "Failed to report failed checks on job '%s' and mark it completed.",
                toJobKeyString(job.jobKey())));
        recordJobClientError(ErrorReason.JOB_MARK_COMPLETION_FAILED);
        throw new JobClientException(
            String.format(
                "Job %s does not exist in the JobMetadata table", toJobKeyString(job.jobKey())),
            ErrorReason.JOB_MARK_COMPLETION_FAILED);
      }

    } catch (JobMetadataDbException | JobMetadataConflictException e) {
      logger.log(
          Level.SEVERE,
          String.format(
              "Failed to report failed checks on job '%s' and mark it completed.",
              toJobKeyString(job.jobKey())),
          e);
      recordJobClientError(ErrorReason.JOB_MARK_COMPLETION_FAILED);
      throw new JobClientException(e, ErrorReason.JOB_MARK_COMPLETION_FAILED);
    }
  }

  /** Returns a new {@code Job} instance. */
  Job buildJob(JobQueueItem jobQueueItem, JobMetadata jobMetadata) throws JobClientException {
    Job.Builder resBuilder =
        Job.builder()
            .setJobKey(jobMetadata.getJobKey())
            .setJobStatus(jobMetadata.getJobStatus())
            .setCreateTime(ProtoUtil.toJavaInstant(jobMetadata.getRequestReceivedAt()))
            .setUpdateTime(ProtoUtil.toJavaInstant(jobMetadata.getRequestUpdatedAt()))
            .setNumAttempts(jobMetadata.getNumAttempts())
            .setJobProcessingTimeout(
                ProtoUtil.toJavaDuration(jobQueueItem.getJobProcessingTimeout()));

    if (jobMetadata.hasRequestProcessingStartedAt()) {
      resBuilder.setProcessingStartTime(
          Optional.of(ProtoUtil.toJavaInstant(jobMetadata.getRequestProcessingStartedAt())));
    }

    if (jobMetadata.hasRequestInfo()) {
      resBuilder.setRequestInfo(jobMetadata.getRequestInfo());
    } else if (jobMetadata.hasCreateJobRequest()) {
      resBuilder.setRequestInfo(convertToRequestInfo(jobMetadata.getCreateJobRequest()));
    } else {
      throw new JobClientException(
          String.format(
              "Missing requestInfo and createJobRequest from JobMetadata for job %s.",
              toJobKeyString(jobMetadata.getJobKey())),
          ErrorReason.JOB_PULL_FAILED);
    }

    return resBuilder.build();
  }

  private void recordJobClientError(ErrorReason errorReason) {
    try {
      if (enableLegacyMetrics) {
        CustomMetric metric =
            CustomMetric.builder()
                .setNameSpace(METRIC_NAMESPACE)
                .setName("JobClientError")
                .setValue(1.0)
                .setUnit("Count")
                .setMetricType(MetricType.DOUBLE_GAUGE)
                .addLabel("ErrorReason", errorReason.toString())
                .build();
        legacyMetricClient.recordMetric(metric);
      }
      if (enableRemoteAggregationMetrics) {
        // A dual write for same data point to two metrics due to the change of Metric type.
        // TODO: Remove the metric above once the new metric is stable.
        CustomMetric newClientErrorMetric =
            CustomMetric.builder()
                .setNameSpace(NEW_METRIC_NAMESPACE)
                .setName("JobClientErrorCounter")
                .setValue(1.0)
                .setUnit("Count")
                .setMetricType(MetricType.DOUBLE_COUNTER)
                .addLabel("ErrorReason", errorReason.toString())
                .build();
        metricClient.recordMetric(newClientErrorMetric);
      }
    } catch (Exception e) {
      logger.warning(String.format("Could not record job client metric.\n%s", e));
    }
  }

  /**
   * Converts between the {@link
   * com.google.scp.operator.protos.shared.backend.CreateJobRequestProto.CreateJobRequest} and
   * {@link com.google.scp.operator.protos.shared.backend.RequestInfoProto.RequestInfo}.
   *
   * <p>TODO: Added for backwards compatibility and to be removed when CreateJobRequest shared model
   * no longer needs to be populated.
   */
  private RequestInfo convertToRequestInfo(CreateJobRequest createJobRequest) {
    return RequestInfo.newBuilder()
        .setJobRequestId(createJobRequest.getJobRequestId())
        .putAllJobParameters(createJobRequest.getJobParameters())
        .setInputDataBucketName(createJobRequest.getInputDataBucketName())
        .setInputDataBlobPrefix(createJobRequest.getInputDataBlobPrefix())
        .setOutputDataBucketName(createJobRequest.getOutputDataBucketName())
        .setOutputDataBlobPrefix(createJobRequest.getOutputDataBlobPrefix())
        .build();
  }

  private void startJobProcessingExtender() {
    JobProcessingExtenderService jobProcessingExtenderService =
        new JobProcessingExtenderService(jobQueue, cache);
    jobProcessingExtenderService.startAsync();
  }

  /** Checks if a job is already being processed and within the job processing timeout. */
  @VisibleForTesting
  boolean isDuplicateJob(Optional<Job> job) {
    if (job.get().processingStartTime().isEmpty()) {
      return false;
    }
    Instant jobStarted = job.get().processingStartTime().get();
    Duration jobProcessingTimeout = job.get().jobProcessingTimeout();
    Instant currentTime = Instant.now(clock);

    logger.info(
        String.format(
            "Received job %s with status %s. The job started at %s, current time is %s, and job"
                + " processing timeout is %d seconds.",
            job.get().jobKey(),
            job.get().jobStatus(),
            jobStarted,
            currentTime,
            jobProcessingTimeout.toSeconds()));

    return job.get().jobStatus() == JobStatus.IN_PROGRESS
        && jobStarted.plus(jobProcessingTimeout).isAfter(currentTime);
  }
}
