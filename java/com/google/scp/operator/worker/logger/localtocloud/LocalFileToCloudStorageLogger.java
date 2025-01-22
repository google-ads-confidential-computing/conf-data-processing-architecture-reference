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

package com.google.scp.operator.worker.logger.localtocloud;

import static com.google.scp.operator.cpio.blobstorageclient.BlobStorageClient.getDataLocation;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;
import com.google.scp.operator.cpio.blobstorageclient.BlobStorageClient;
import com.google.scp.operator.cpio.blobstorageclient.BlobStorageClient.BlobStorageClientException;
import com.google.scp.operator.cpio.blobstorageclient.model.DataLocation;
import com.google.scp.operator.cpio.jobclient.model.Job;
import com.google.scp.operator.shared.model.BackendModelUtil;
import com.google.scp.operator.worker.logger.ResultLogger;
import com.google.scp.operator.worker.model.Fact;
import com.google.scp.operator.worker.writer.LocalResultFileWriter;
import com.google.scp.operator.worker.writer.LocalResultFileWriter.FileWriteException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Implementation of {@code ResultLogger} that writes a local file then uploads it to cloud storage.
 */
public final class LocalFileToCloudStorageLogger implements ResultLogger {

  private final LocalResultFileWriter localResultFileWriter;
  private final BlobStorageClient blobStorageClient;
  private final Path workingDirectory;

  @Inject
  LocalFileToCloudStorageLogger(
      LocalResultFileWriter localResultFileWriter,
      BlobStorageClient blobStorageClient,
      @ResultWorkingDirectory Path workingDirectory) {
    this.localResultFileWriter = localResultFileWriter;
    this.blobStorageClient = blobStorageClient;
    this.workingDirectory = workingDirectory;
  }

  /** Write the results to a local file then write that local file to cloud storage */
  @Override
  public DataLocation logResults(Stream<Fact> results, Job ctx) throws ResultLogException {
    return logResults(results, ctx, false);
  }

  /** Write the results to a local file then write that local file to cloud storage. */
  @Override
  public DataLocation logResults(Stream<Fact> results, Job ctx, boolean useJobAccountIdentity)
      throws ResultLogException {
    String localFileName = getLocalFileName(ctx);
    Path localResultsFilePath =
        workingDirectory
            .getFileSystem()
            .getPath(Paths.get(workingDirectory.toString(), localFileName).toString());
    try {
      // Create the working directory if it doesn't exist
      Files.createDirectories(workingDirectory);

      // Write the results to a local file.
      localResultFileWriter.writeLocalFile(results, localResultsFilePath);

      // Write the local file to cloud storage
      DataLocation resultLocation =
          getDataLocation(
              ctx.requestInfo().getOutputDataBucketName(),
              ctx.requestInfo().getOutputDataBlobPrefix());

      if (useJobAccountIdentity) {
        blobStorageClient.putBlob(
            resultLocation,
            localResultsFilePath,
            Optional.of(ctx.requestInfo().getAccountIdentity()));
      } else {
        blobStorageClient.putBlob(resultLocation, localResultsFilePath);
      }

      return resultLocation;
    } catch (IOException | FileWriteException | BlobStorageClientException e) {
      throw new ResultLogException(e);
    } finally {
      // Delete the local file since it is no longer needed.
      try {
        Files.deleteIfExists(localResultsFilePath);
      } catch (IOException e) {
        throw new ResultLogException(e);
      }
    }
  }

  /**
   * The local file name has a random UUID in it to prevent cases where an item is processed twice
   * by the same worker and clobbers other files being written.
   */
  private String getLocalFileName(Job ctx) {
    return "job-"
        + BackendModelUtil.toJobKeyString(ctx.jobKey())
        + "-"
        + UUID.randomUUID()
        + localResultFileWriter.getFileExtension();
  }

  /**
   * Annotation for the {@link Path} the worker will use to temporarily store results files before
   * persisting them elsewhere.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface ResultWorkingDirectory {}
}
