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

import static com.google.scp.operator.protos.shared.backend.ReturnCodeProto.ReturnCode.INPUT_DATA_READ_FAILED;
import static com.google.scp.operator.protos.shared.backend.ReturnCodeProto.ReturnCode.INTERNAL_ERROR;
import static com.google.scp.operator.protos.shared.backend.ReturnCodeProto.ReturnCode.OUTPUT_DATAWRITE_FAILED;
import static com.google.scp.operator.protos.shared.backend.ReturnCodeProto.ReturnCode.SUCCESS;
import static com.google.scp.operator.protos.shared.backend.ReturnCodeProto.ReturnCode.UNSPECIFIED_ERROR;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.scp.coordinator.privacy.budgeting.model.ConsumePrivacyBudgetRequest;
import com.google.scp.coordinator.privacy.budgeting.model.ConsumePrivacyBudgetResponse;
import com.google.scp.coordinator.privacy.budgeting.model.PrivacyBudgetUnit;
import com.google.scp.operator.cpio.blobstorageclient.BlobStorageClient;
import com.google.scp.operator.cpio.blobstorageclient.model.DataLocation;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.DistributedPrivacyBudgetClient;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.DistributedPrivacyBudgetClient.DistributedPrivacyBudgetClientException;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.DistributedPrivacyBudgetClient.DistributedPrivacyBudgetServiceException;
import com.google.scp.operator.cpio.jobclient.model.Job;
import com.google.scp.operator.cpio.jobclient.model.JobResult;
import com.google.scp.operator.cpio.metricclient.MetricClient;
import com.google.scp.operator.protos.shared.backend.ErrorSummaryProto.ErrorSummary;
import com.google.scp.operator.protos.shared.backend.ResultInfoProto.ResultInfo;
import com.google.scp.operator.worker.logger.ResultLogger;
import com.google.scp.operator.worker.logger.ResultLogger.ResultLogException;
import com.google.scp.operator.worker.model.DecryptionResult;
import com.google.scp.operator.worker.model.EncryptedReport;
import com.google.scp.operator.worker.model.Fact;
import com.google.scp.operator.worker.reader.RecordReader;
import com.google.scp.operator.worker.reader.RecordReader.RecordReadException;
import com.google.scp.operator.worker.reader.RecordReaderFactory;
import com.google.scp.shared.proto.ProtoUtil;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Processor which uses simple in-memory logging. */
public class SimpleProcessor implements JobProcessor {

  public static final String RESULT_SUCCESS_MESSAGE = "Aggregation job successfully processed";
  public static final String INSUFFICIENT_BUDGET_MESSAGE =
      "Insufficient privacy budget. Missing units: ";
  public static final String METRIC_NAMESPACE = "scp/simpleprocessor";
  private static final Logger logger = LoggerFactory.getLogger(SimpleProcessor.class);

  private final RecordReaderFactory recordReaderFactory;
  private final ReportDecrypter reportDecrypter;
  private final ResultLogger resultLogger;
  private final Clock clock;
  private final DistributedPrivacyBudgetClient distributedPrivacyBudgetClient;
  private final MetricClient metricClient;

  @Inject
  SimpleProcessor(
      RecordReaderFactory recordReaderFactory,
      ReportDecrypter reportDecrypter,
      ResultLogger resultLogger,
      Clock clock,
      DistributedPrivacyBudgetClient distributedPrivacyBudgetClient,
      MetricClient metricClient) {
    this.recordReaderFactory = recordReaderFactory;
    this.reportDecrypter = reportDecrypter;
    this.resultLogger = resultLogger;
    this.clock = clock;
    this.distributedPrivacyBudgetClient = distributedPrivacyBudgetClient;
    this.metricClient = metricClient;
  }

  @Override
  public JobResult process(Job job) throws AggregationJobProcessException {
    JobResult.Builder jobResultBuilder = JobResult.builder().setJobKey(job.jobKey());

    DataLocation inputDataLocation =
        BlobStorageClient.getDataLocation(
            job.requestInfo().getInputDataBucketName(), job.requestInfo().getInputDataBlobPrefix());
    try (RecordReader recordReader = recordReaderFactory.of(inputDataLocation)) {
      // TODO: Enable it when remote aggregration is enabled.
      // CustomMetric metric =
      //     CustomMetric.builder()
      //         .setNameSpace(METRIC_NAMESPACE)
      //         .setName("ProcessJobCount")
      //         .setValue(1.0)
      //         .setUnit("Count")
      //         .setMetricType(MetricType.DOUBLE_COUNTER)
      //         .build();
      // metricClient.recordMetric(metric);

      Stream<EncryptedReport> encryptedReports =
          recordReader.readEncryptedReports(inputDataLocation);

      // Decrypt encrypted reports
      Stream<DecryptionResult> decryptionResults = encryptedReports.map(reportDecrypter::decrypt);

      // Add results with errors to list to create error summary and aggregate present reports.
      ArrayList<DecryptionResult> resultsWithErrors = new ArrayList<>();
      ArrayList<PrivacyBudgetUnit> privacyBudgetUnits = new ArrayList<>();
      var validatedReports =
          decryptionResults
              .peek(result -> addToErrors(result, resultsWithErrors))
              .map(DecryptionResult::report)
              .filter(Optional::isPresent)
              .peek(
                  result ->
                      privacyBudgetUnits.add(
                          PrivacyBudgetUnit.builder()
                              .privacyBudgetKey(result.get().privacyBudgetKey().key())
                              .reportingWindow(result.get().originalReportTime())
                              .build()))
              .map(Optional::get);

      // Map facts
      Stream<Fact> facts = validatedReports.flatMap(report -> report.facts().stream());
      resultLogger.logResults(facts, job);

      // Create error summary from the list of errors from decryption/validation
      ErrorSummary errorSummary =
          ErrorSummaryAggregator.createErrorSummary(ImmutableList.copyOf(resultsWithErrors));

      return verifyPrivacyBudget(job, ImmutableList.copyOf(privacyBudgetUnits), errorSummary)
          .orElse(
              jobResultBuilder
                  .setResultInfo(
                      ResultInfo.newBuilder()
                          .setReturnCode(SUCCESS.name())
                          .setReturnMessage(RESULT_SUCCESS_MESSAGE)
                          .setErrorSummary(errorSummary)
                          .setFinishedAt(ProtoUtil.toProtoTimestamp(Instant.now(clock)))
                          .build())
                  .build());
    } catch (RecordReadException e) {
      // Error occurred in data read
      logger.error("Exception occurred during input data read. Reporting processing failure.", e);
      return jobResultBuilder
          .setResultInfo(
              ResultInfo.newBuilder()
                  .setReturnCode(INPUT_DATA_READ_FAILED.name())
                  .setReturnMessage(Throwables.getStackTraceAsString(e))
                  .setErrorSummary(ErrorSummary.getDefaultInstance())
                  .setFinishedAt(ProtoUtil.toProtoTimestamp(Instant.now(clock)))
                  .build())
          .build();
    } catch (ResultLogException e) {
      // Error occurred in data write
      logger.error("Exception occurred during result data write. Reporting processing failure.", e);
      return jobResultBuilder
          .setResultInfo(
              ResultInfo.newBuilder()
                  .setReturnCode(OUTPUT_DATAWRITE_FAILED.name())
                  .setReturnMessage(Throwables.getStackTraceAsString(e))
                  .setErrorSummary(ErrorSummary.getDefaultInstance())
                  .setFinishedAt(ProtoUtil.toProtoTimestamp(Instant.now(clock)))
                  .build())
          .build();
    } catch (DistributedPrivacyBudgetServiceException | DistributedPrivacyBudgetClientException e) {
      // Error occurred in PBS client or service
      return jobResultBuilder
          .setResultInfo(
              ResultInfo.newBuilder()
                  .setReturnCode(UNSPECIFIED_ERROR.name())
                  .setReturnMessage(Throwables.getStackTraceAsString(e))
                  .setErrorSummary(ErrorSummary.getDefaultInstance())
                  .setFinishedAt(ProtoUtil.toProtoTimestamp(Instant.now(clock)))
                  .build())
          .build();
    }
    // TODO: Enable it when remote aggregration is enabled.
    // catch (MetricClientException e) {
    //   logger.error("Exception occurred during recording metrics.", e);
    //   return jobResultBuilder
    //       .setResultInfo(
    //           ResultInfo.newBuilder()
    //               .setReturnCode(INTERNAL_ERROR.name())
    //               .setReturnMessage(Throwables.getStackTraceAsString(e))
    //               .setErrorSummary(ErrorSummary.getDefaultInstance())
    //               .setFinishedAt(ProtoUtil.toProtoTimestamp(Instant.now(clock)))
    //               .build())
    //       .build();
    // }
  }

  /**
   * Add reports with errors to {@param resultsWithErrors}.
   *
   * <p>Limit number of errors to 1000.
   */
  private void addToErrors(
      DecryptionResult decryptionResult, ArrayList<DecryptionResult> resultsWithErrors) {
    if (resultsWithErrors.size() > 1000) {
      return;
    }
    if (decryptionResult.report().isEmpty()) {
      resultsWithErrors.add(decryptionResult);
    }
  }

  private Optional<JobResult> verifyPrivacyBudget(
      Job job, ImmutableList<PrivacyBudgetUnit> privacyBudgetUnits, ErrorSummary errorSummary)
      throws DistributedPrivacyBudgetClientException, DistributedPrivacyBudgetServiceException {
    JobResult.Builder jobResultBuilder = JobResult.builder().setJobKey(job.jobKey());

    ImmutableList<PrivacyBudgetUnit> exhaustedPrivacyBudgetUnits = ImmutableList.of();
    try {
      String attributionReportTo =
          job.requestInfo().getJobParameters().get("attribution_report_to");
      ConsumePrivacyBudgetResponse consumePrivacyBudgetResponse =
          this.distributedPrivacyBudgetClient.consumePrivacyBudget(
              ConsumePrivacyBudgetRequest.builder()
                  .privacyBudgetUnits(privacyBudgetUnits)
                  .attributionReportTo(attributionReportTo)
                  .build());
      exhaustedPrivacyBudgetUnits = consumePrivacyBudgetResponse.exhaustedPrivacyBudgetUnits();
    } catch (DistributedPrivacyBudgetClientException | DistributedPrivacyBudgetServiceException e) {
      logger.error("Exception occurred when consuming privacy budget.");
      throw e;
    }

    if (!exhaustedPrivacyBudgetUnits.isEmpty()) {
      // Truncate the message in order to not overflow the result table.
      String messageWithUnits = INSUFFICIENT_BUDGET_MESSAGE + exhaustedPrivacyBudgetUnits;
      String errorMessage =
          messageWithUnits.substring(0, Math.min(1000, messageWithUnits.length()));
      return Optional.of(
          jobResultBuilder
              .setResultInfo(
                  ResultInfo.newBuilder()
                      // TODO(b/218508112): Do not use internal error, have a dedicated code.
                      .setReturnCode(INTERNAL_ERROR.name())
                      .setReturnMessage(errorMessage)
                      .setErrorSummary(errorSummary)
                      .setFinishedAt(ProtoUtil.toProtoTimestamp(Instant.now(clock)))
                      .build())
              .build());
    }
    return Optional.empty();
  }
}
