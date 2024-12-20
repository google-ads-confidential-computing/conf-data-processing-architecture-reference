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

import static com.google.scp.shared.clients.configclient.model.WorkerParameter.ENABLE_METRIC_AGGREGATION;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.opentelemetry.detectors.GCPResource;
import com.google.cloud.opentelemetry.metric.GoogleCloudMetricExporter;
import com.google.cloud.opentelemetry.metric.MetricConfiguration;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.operator.cpio.metricclient.MetricClient;
import com.google.scp.operator.cpio.metricclient.MetricModule;
import com.google.scp.operator.cpio.metricclient.gcp.Annotations.EnableMetricAggregation;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.ParameterClient.ParameterClientException;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpProjectId;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class GcpMetricModule extends MetricModule {

  @Override
  public Class<? extends MetricClient> getMetricClientImpl() {
    return GcpMetricClient.class;
  }

  @Provides
  @EnableMetricAggregation
  public Boolean provideEnableMetricAggregation(ParameterClient parameterClient)
      throws ParameterClientException {
    Optional<String> enableMetricAggregationParam =
        parameterClient.getParameter(ENABLE_METRIC_AGGREGATION.name());
    return enableMetricAggregationParam.map(Boolean::valueOf).orElse(false);
  }

  @Provides
  @Singleton
  MetricServiceClient provideMetricServiceClient() throws IOException {
    return MetricServiceClient.create();
  }

  @Provides
  @Singleton
  MetricExporter provideOpenTelemetryMetricExporter(
      @GcpProjectId String gcpProjectId, GoogleCredentials credentials) throws IOException {
    return GoogleCloudMetricExporter.createWithConfiguration(
        MetricConfiguration.builder()
            .setProjectId(gcpProjectId)
            .setCredentials(credentials)
            .setPrefix("custom.custom.googleapis.com")
            .build());
  }

  @Provides
  @Singleton
  Meter provideOpenTelemetryMeter(MetricExporter metricExporter) throws IOException {
    Resource gcpResource = Resource.create(new GCPResource().getAttributes());
    PeriodicMetricReader metricReader =
        PeriodicMetricReader.builder(metricExporter)
            .setInterval(java.time.Duration.ofSeconds(30))
            .build();
    SdkMeterProvider sdkMeterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(metricReader)
            .setResource(Resource.create(gcpResource.getAttributes()))
            .build();
    return sdkMeterProvider
        // This Instrumentation Scope Info will be added to the metric labels.
        .meterBuilder("scp-operator-metric-client")
        // This Instrumentation Scope Version will be added to the metric labels.
        .setInstrumentationVersion("1.0.0")
        .build();
  }
}
