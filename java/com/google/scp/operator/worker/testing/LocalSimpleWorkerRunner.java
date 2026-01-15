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
package com.google.scp.operator.worker.testing;

import com.beust.jcommander.JCommander;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ServiceManager;
import com.google.scp.operator.worker.SimpleWorker;
import com.google.scp.operator.worker.SimpleWorkerArgs;
import com.google.scp.operator.worker.SimpleWorkerModule;
import com.google.scp.operator.worker.logger.ResultLogger.ResultLogException;
import com.google.scp.operator.worker.logger.inmemory.InMemoryResultLogger;
import com.google.scp.operator.worker.logger.inmemory.MaterializedResults;
import com.google.scp.operator.worker.model.Fact;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

/**
 * LocalSimpleWorkerRunner is a wrapper on SimpleWorker to provide necessary helper methods used in
 * tests and on demand dev tools.
 */
public final class LocalSimpleWorkerRunner {
  private final SimpleWorkerArgs simpleWorkerArgs = new SimpleWorkerArgs();
  private SimpleWorker worker;
  private ServiceManager serviceManager;

  private LocalSimpleWorkerRunner(String[] args) {
    updateArgs(args);
  }

  /**
   * Creates an aggregation worker runner from a path where runner stores the worker results. By
   * default all the input and output are expected under the rootDir. To specify or override the
   * Path of each file, call {@link #updateArgs(String[])} with related {@link SimpleWorkerArgs}
   * flags to update.
   *
   * @param rootDir Path that stores the worker results.
   */
  public static LocalSimpleWorkerRunner create(Path rootDir) {
    String[] args =
        new String[] {
          "--job_client",
          "LOCAL_FILE",
          "--lifecycle_client",
          "LOCAL",
          "--blob_storage_client",
          "LOCAL_FS_CLIENT",
          "--result_logger",
          "IN_MEMORY",
          "--decryption_key_service",
          "LOCAL_FILE_DECRYPTION_KEY_SERVICE",
          "--local_file_decryption_key_path",
          rootDir.resolve("hybrid.key").toAbsolutePath().toString(),
          "--result_working_directory_path",
          rootDir.toAbsolutePath().toString(),
          "--local_file_single_puller_path",
          rootDir.resolve("reports.avro").toAbsolutePath().toString(),
          "--local_file_job_info_path",
          rootDir.resolve("results.json").toAbsolutePath().toString(),
          "--simulation_inputs",
        };
    return new LocalSimpleWorkerRunner(args);
  }

  public LocalSimpleWorkerRunner updateArgs(String[] newArgs) {
    JCommander.newBuilder().addObject(simpleWorkerArgs).build().parse(newArgs);
    SimpleWorkerModule guiceModule = new SimpleWorkerModule(simpleWorkerArgs);
    worker = SimpleWorker.fromModule(guiceModule);
    serviceManager = worker.createServiceManager();
    return this;
  }

  public void run() {
    serviceManager.startAsync();
    serviceManager.awaitStopped();
  }

  public ImmutableList<Fact> waitForAggregation() throws ResultLogException, TimeoutException {
    InMemoryResultLogger logger = worker.getInjector().getInstance(InMemoryResultLogger.class);
    MaterializedResults results = null;
    boolean loggerTriggered = false;
    if (logger.hasLogged()) {
      loggerTriggered = true;
      results = logger.getMaterializedResults();
    }
    if (results == null) {
      // Worker hasn't completed after polling.
      if (loggerTriggered) {
        throw new ResultLogException(
            new IllegalStateException(
                "MaterializedAggregations is null. Maybe results did not get logged."));
      }
      throw new TimeoutException("logResults is never called. Worker timed out.");
    }
    return results.getMaterializedFacts();
  }
}
