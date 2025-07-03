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

package com.google.scp.operator.cpio.metricclient.gcp;

import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.operator.cpio.metricclient.MetricClient;
import com.google.scp.operator.cpio.metricclient.model.Annotations.LegacyMetricClient;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpInstanceId;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpProjectId;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpZone;
import java.io.IOException;
import java.util.Optional;

/**
 * This is a temporary module provides legacy metric client, which use MetricServiceClient to send
 * monitoring data to GCM. The legacy metric client is to support dual write during OpenTelemetry
 * migration. Once the migration is done, this module will be removed.
 */
@Singleton
public class GcpLegacyMetricModule extends AbstractModule {

  @Provides
  @LegacyMetricClient
  public MetricClient provideLegacyMetricClient(
      ParameterClient parameterClient,
      @GcpProjectId String projectId,
      @GcpInstanceId String instanceId,
      @GcpZone String zone)
      throws IOException {
    return new GcpMetricClient(
        MetricServiceClient.create(),
        Optional.empty(),
        parameterClient,
        projectId,
        instanceId,
        zone,
        /* enableRemoteMetricAggregation */ false);
  }
}
