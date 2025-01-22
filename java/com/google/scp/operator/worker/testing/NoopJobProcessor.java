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

import com.google.scp.operator.cpio.jobclient.model.Job;
import com.google.scp.operator.cpio.jobclient.model.JobResult;
import com.google.scp.operator.worker.JobProcessor;
import java.util.Optional;

/** Work processor that does nothing except capture the last job provided to it */
public final class NoopJobProcessor implements JobProcessor {

  private Optional<Job> lastProcessed = Optional.empty();
  private JobResult jobResultToReturn;
  private boolean shouldThrowException;

  NoopJobProcessor() {
    jobResultToReturn = null;
    shouldThrowException = false;
  }

  @Override
  public JobResult process(Job job) throws AggregationJobProcessException {
    if (shouldThrowException) {
      throw new AggregationJobProcessException(new IllegalStateException("Was set to throw"));
    }
    lastProcessed = Optional.of(job);
    return jobResultToReturn;
  }

  public Optional<Job> getLastProcessed() {
    return lastProcessed;
  }

  public void setJobResultToReturn(JobResult jobResultToReturn) {
    this.jobResultToReturn = jobResultToReturn;
  }

  public void setShouldThrowException(boolean shouldThrowException) {
    this.shouldThrowException = shouldThrowException;
  }
}
