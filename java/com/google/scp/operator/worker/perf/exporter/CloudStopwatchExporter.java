package com.google.scp.operator.worker.perf.exporter;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableList;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;
import com.google.scp.operator.cpio.blobstorageclient.BlobStorageClient;
import com.google.scp.operator.cpio.blobstorageclient.BlobStorageClient.BlobStorageClientException;
import com.google.scp.operator.cpio.blobstorageclient.model.DataLocation;
import com.google.scp.operator.cpio.blobstorageclient.model.DataLocation.BlobStoreDataLocation;
import com.google.scp.operator.worker.perf.StopwatchExporter;
import com.google.scp.operator.worker.perf.StopwatchRegistry;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stopwatch exporter that exports to a plaintext file in a cloud storage bucket depending on the
 * selected blob storage client.
 */
public class CloudStopwatchExporter implements StopwatchExporter {

  private final String exportBucketName;
  private final String keyName;
  private final BlobStorageClient blobStorageClient;
  private Path stopwatchFile;

  @Inject
  CloudStopwatchExporter(
      @StopwatchBucketName String exportBucketName,
      @StopwatchKeyName String keyName,
      BlobStorageClient blobStorageClient) {
    this.exportBucketName = exportBucketName;
    this.keyName = keyName;
    this.blobStorageClient = blobStorageClient;
  }

  @Override
  public void export(StopwatchRegistry stopwatches) throws StopwatchExportException {
    // Forms the file lines as just comma separated key/value pairs, key being the stopwatch name,
    // and the value being the recorded millisecond duration.
    ImmutableList<String> fileLines =
        stopwatches.collectStopwatchTimes().entrySet().stream()
            .map(
                stopwatchEntry ->
                    String.format(
                        "%s,%d", stopwatchEntry.getKey(), stopwatchEntry.getValue().toMillis()))
            .collect(toImmutableList());

    try {
      stopwatchFile =
          Files.createTempFile(/* prefix= */ "stopwatches", /* suffix= */ "txt").toAbsolutePath();
      Files.write(stopwatchFile, fileLines);
    } catch (IOException e) {
      throw new StopwatchExportException(e);
    }

    try {
      DataLocation location =
          DataLocation.ofBlobStoreDataLocation(
              BlobStoreDataLocation.create(exportBucketName, keyName));
      blobStorageClient.putBlob(location, stopwatchFile);
    } catch (BlobStorageClientException e) {
      throw new StopwatchExportException(e);
    }
  }

  /** Annotation for blob store bucket to store stopwatches. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface StopwatchBucketName {}

  /** Annotation for blob store key to store stopwatches. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface StopwatchKeyName {}
}
