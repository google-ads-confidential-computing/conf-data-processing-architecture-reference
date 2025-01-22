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

import static com.google.scp.operator.protos.shared.backend.JobErrorCategoryProto.JobErrorCategory.DECRYPTION_ERROR;

import com.google.scp.operator.worker.decryption.RecordDecrypter;
import com.google.scp.operator.worker.decryption.RecordDecrypter.DecryptionException;
import com.google.scp.operator.worker.model.DecryptionResult;
import com.google.scp.operator.worker.model.EncryptedReport;
import com.google.scp.operator.worker.model.ErrorMessage;
import com.google.scp.operator.worker.model.Report;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decrypts and Deserializes reports for aggregation. */
public final class ReportDecrypter {

  private final RecordDecrypter recordDecrypter;

  private static final Logger logger = LoggerFactory.getLogger(ReportDecrypter.class);

  @Inject
  public ReportDecrypter(RecordDecrypter recordDecrypter) {
    this.recordDecrypter = recordDecrypter;
  }

  /**
   * Decrypts and deserializes a report.
   *
   * <p>Performs decryption and deserialization. The result is a DecryptionResult which contains
   * either the decrypted report or errors that came up in decryption which can be summarized and
   * provided to requesters as debug information.
   */
  public DecryptionResult decrypt(EncryptedReport encryptedReport) {
    try {
      // Decrypt the report
      Report report = recordDecrypter.decryptSingleReport(encryptedReport);
      return DecryptionResult.builder().setReport(report).build();
    } catch (DecryptionException e) {
      logger.error("Report Decryption Failure", e);
      String detailedErrorMessage = String.format("Report Decryption Failure, cause: %s", e);
      return DecryptionResult.builder()
          .addErrorMessage(
              ErrorMessage.builder()
                  .setCategory(DECRYPTION_ERROR.name())
                  .setDetailedErrorMessage(detailedErrorMessage)
                  .build())
          .build();
    }
  }
}
