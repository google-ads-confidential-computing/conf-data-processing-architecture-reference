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

package com.google.scp.operator.worker.logger.inmemory;

import com.google.scp.operator.cpio.blobstorageclient.model.DataLocation;
import com.google.scp.operator.cpio.blobstorageclient.model.DataLocation.BlobStoreDataLocation;
import com.google.scp.operator.cpio.jobclient.model.Job;
import com.google.scp.operator.worker.logger.ResultLogger;
import com.google.scp.operator.worker.model.Fact;
import java.util.stream.Stream;

/**
 * {@link ResultLogger} implementation to materialized and store aggregation results in memory for
 * testing.
 */
public final class InMemoryResultLogger implements ResultLogger {

  private MaterializedResults materializedResults;
  private boolean shouldThrow;
  private volatile boolean hasLogged;

  InMemoryResultLogger() {
    materializedResults = null;
    shouldThrow = false;
    hasLogged = false;
  }

  public synchronized boolean hasLogged() {
    return hasLogged;
  }

  @Override
  public DataLocation logResults(Stream<Fact> results, Job unused) throws ResultLogException {
    hasLogged = true;

    if (shouldThrow) {
      throw new ResultLogException(new IllegalStateException("Was set to throw"));
    }

    materializedResults = MaterializedResults.of(results);

    System.out.println("Materialized results: " + materializedResults);
    return DataLocation.ofBlobStoreDataLocation(BlobStoreDataLocation.create("", ""));
  }

  @Override
  public DataLocation logResults(Stream<Fact> results, Job unused, boolean useJobAccountIdentity)
      throws ResultLogException {
    return logResults(results, unused);
  }

  /**
   * Gets materialized results as an ImmutableList of {@link Fact}
   *
   * @throws ResultLogException if results were not logged prior to calling this method.
   */
  public MaterializedResults getMaterializedResults() throws ResultLogException {
    if (materializedResults == null) {
      throw new ResultLogException(
          new IllegalStateException(
              "MaterializedAggregations is null. Maybe results did not get logged."));
    }

    return materializedResults;
  }

  public void setShouldThrow(boolean shouldThrow) {
    this.shouldThrow = shouldThrow;
  }
}
