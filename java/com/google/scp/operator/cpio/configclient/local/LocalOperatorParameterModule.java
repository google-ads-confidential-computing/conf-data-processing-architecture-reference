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

package com.google.scp.operator.cpio.configclient.local;

import static com.google.scp.operator.cpio.configclient.local.Annotations.MaxJobNumAttemptsParameter;
import static com.google.scp.operator.cpio.configclient.local.Annotations.MaxJobProcessingTimeSecondsParameter;
import static com.google.scp.shared.clients.configclient.local.Annotations.ParameterValues;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Provides;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.ParameterModule;
import com.google.scp.shared.clients.configclient.local.LocalParameterClient;
import com.google.scp.shared.clients.configclient.model.WorkerParameter;

/** Guice module for binding the local parameter client functionality specific to operators */
public final class LocalOperatorParameterModule extends ParameterModule {

  @Override
  public Class<? extends ParameterClient> getParameterClientImpl() {
    return LocalParameterClient.class;
  }

  @Provides
  @ParameterValues
  ImmutableMap<String, String> provideParameterValues(
      @MaxJobNumAttemptsParameter String maxJobNumAttempts,
      @MaxJobProcessingTimeSecondsParameter String maxJobProcessingTimeSeconds) {
    return ImmutableMap.<String, String>builder()
        .put(WorkerParameter.MAX_JOB_NUM_ATTEMPTS.name(), maxJobNumAttempts)
        .put(WorkerParameter.MAX_JOB_PROCESSING_TIME_SECONDS.name(), maxJobProcessingTimeSeconds)
        .build();
  }
}
