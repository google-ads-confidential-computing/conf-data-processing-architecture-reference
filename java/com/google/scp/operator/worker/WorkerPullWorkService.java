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

package com.google.scp.operator.worker;

import static com.google.scp.shared.clients.configclient.model.WorkerParameter.CUSTOMER_TOPIC_ID_1;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.CUSTOMER_TOPIC_ID_2;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.scp.operator.cpio.jobclient.JobClient;
import com.google.scp.operator.cpio.jobclient.model.GetJobRequest;
import com.google.scp.operator.cpio.jobclient.model.Job;
import com.google.scp.operator.cpio.jobclient.model.JobResult;
import com.google.scp.operator.cpio.jobclient.model.JobRetryRequest;
import com.google.scp.operator.cpio.jobclient.model.WorkgroupAllocationFuncResponse;
import com.google.scp.operator.cpio.metricclient.MetricClient;
import com.google.scp.operator.cpio.metricclient.model.Annotations.EnableRemoteMetricAggregation;
import com.google.scp.operator.cpio.metricclient.model.CustomMetric;
import com.google.scp.operator.cpio.metricclient.model.MetricType;
import com.google.scp.operator.worker.Annotations.BenchmarkMode;
import com.google.scp.operator.worker.perf.StopwatchExporter;
import com.google.scp.operator.worker.perf.StopwatchRegistry;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.ParameterClient.ParameterClientException;
import com.google.scp.shared.enums.JobType;
import java.util.Optional;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Guava service for repeatedly pulling from the pubsub and processing the request */
final class WorkerPullWorkService extends AbstractExecutionThreadService {
  public static final String METRIC_NAMESPACE = "scp/workerpullworkservice";

  private static final Logger logger = LoggerFactory.getLogger(WorkerPullWorkService.class);

  private final JobClient jobClient;
  private final ParameterClient parameterClient;
  private final MetricClient metricClient;
  private final boolean enableRemoteAggregationMetrics;
  private final JobProcessor jobProcessor;
  private final StopwatchRegistry stopwatchRegistry;
  private final StopwatchExporter stopwatchExporter;
  private final boolean benchmarkMode;
  private final ImmutableMap<String, String> jobTopicIdMap;

  // Tracks whether the service should be pulling more jobs. Once the shutdown of the service is
  // initiated, this is switched to false.
  private volatile boolean moreNewRequests;

  @Inject
  WorkerPullWorkService(
      JobClient jobClient,
      ParameterClient parameterClient,
      MetricClient metricClient,
      JobProcessor jobProcessor,
      StopwatchRegistry stopwatchRegistry,
      StopwatchExporter stopwatchExporter,
      @BenchmarkMode boolean benchmarkMode,
      @EnableRemoteMetricAggregation boolean enableRemoteMetricAggregation)
      throws ParameterClientException {
    this.jobClient = jobClient;
    this.parameterClient = parameterClient;
    this.metricClient = metricClient;
    this.jobProcessor = jobProcessor;
    this.moreNewRequests = true;
    this.stopwatchRegistry = stopwatchRegistry;
    this.stopwatchExporter = stopwatchExporter;
    this.benchmarkMode = benchmarkMode;
    String firstCustomerTopicId =
        parameterClient.getParameter(CUSTOMER_TOPIC_ID_1.name()).orElse("");
    String secondCustomerTopicId =
        parameterClient.getParameter(CUSTOMER_TOPIC_ID_2.name()).orElse("");
    this.jobTopicIdMap =
        ImmutableMap.<String, String>builder()
            .put(JobType.MRP.name(), firstCustomerTopicId)
            .put(JobType.CoPla.name(), secondCustomerTopicId)
            .build();
    this.enableRemoteAggregationMetrics = enableRemoteMetricAggregation;
  }

  @Override
  protected void run() {
    logger.info("SCP simple worker started");

    while (moreNewRequests) {
      Optional<Job> job = Optional.empty();
      try {
        GetJobRequest request =
            GetJobRequest.builder()
                .setJobCompletionNotificationTopicIdFunc(
                    (Job jobInQueue) -> {
                      return getTopicIdByJobType(jobInQueue);
                    })
                .setWorkgroupAllocationFunc(
                    (Job jobInQueue) ->
                        WorkgroupAllocationFuncResponse.builder()
                            .setWorkgroupId(
                                jobInQueue
                                    .requestInfo()
                                    .getJobParametersOrDefault("targetWorkgroup", ""))
                            .build())
                .build();
        job = jobClient.getJob(request);
        if (enableRemoteAggregationMetrics) {
          CustomMetric metrics =
              CustomMetric.builder()
                  .setNameSpace(METRIC_NAMESPACE)
                  .setName("TryPullJobCounter")
                  .setValue(1.0)
                  .setUnit("Count")
                  .setMetricType(MetricType.DOUBLE_COUNTER)
                  .build();
          metricClient.recordMetric(metrics);
        }

        if (job.isEmpty()) {
          logger.info("No job pulled.");

          // If the jobhandler could not pull any new jobs, stop polling.
          // Note that jobhandler has an internal backoff mechanism.
          moreNewRequests = false;
          continue;
        }

        logger.info("Item pulled");

        Stopwatch processingStopwatch = null;
        if (benchmarkMode) {
          stopwatchRegistry.cleanupStopwatches();
          processingStopwatch =
              stopwatchRegistry.createStopwatch(
                  "job-processing-" + job.get().jobKey().getJobRequestId());
          processingStopwatch.start();
        }

        JobResult jobResult = jobProcessor.process(job.get());

        String topicId = getTopicIdByJobType(job.get());
        if (topicId.isEmpty()) {
          jobClient.markJobCompleted(jobResult);
        } else {
          jobClient.markJobCompleted(
              jobResult.toBuilder().setTopicId(Optional.of(topicId)).build());
        }

        if (benchmarkMode) {
          processingStopwatch.stop();
          stopwatchExporter.export(stopwatchRegistry);
        }
      } catch (Exception e) {
        // Simply log exceptions that occur so that the worker doesn't crash
        logger.error("Exception occurred in worker", e);
        try {
          if (job.isPresent()) {
            JobRetryRequest jobRetryRequest =
                JobRetryRequest.builder().setJobKey(job.get().jobKey()).build();
            jobClient.returnJobForRetry(jobRetryRequest);
            logger.info("Job " + job.get().jobKey().toString() + " returned for retry.");
          }
        } catch (Exception e2) {
          logger.error("Exception occurred returning job for retry.", e2);
        }
      }
    }
  }

  private String getTopicIdByJobType(Job job) {
    String jobType = job.requestInfo().getJobParametersMap().get(JobType.class.getSimpleName());
    return jobTopicIdMap.getOrDefault(jobType, "");
  }

  @Override
  protected void triggerShutdown() {
    moreNewRequests = false;
  }
}
