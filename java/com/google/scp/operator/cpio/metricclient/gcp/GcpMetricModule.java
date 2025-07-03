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
import com.google.cloud.opentelemetry.detectors.GCPResource;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.operator.cpio.metricclient.MetricClient;
import com.google.scp.operator.cpio.metricclient.MetricClient.MetricClientException;
import com.google.scp.operator.cpio.metricclient.MetricModule;
import com.google.scp.operator.cpio.metricclient.model.Annotations.EnableRemoteMetricAggregation;
import com.google.scp.operator.cpio.metricclient.model.Annotations.MetricExporterIntervalInMillis;
import com.google.scp.operator.cpio.metricclient.model.Annotations.OtlpCollectorExporter;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpInstanceId;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpZone;
import io.opentelemetry.api.common.Attributes;
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
  @Singleton
  MetricServiceClient provideMetricServiceClient() throws IOException {
    return MetricServiceClient.create();
  }

  @Provides
  @Singleton
  Optional<Meter> provideOpenTelemetryMeter(
      @EnableRemoteMetricAggregation Boolean enableRemoteMetricAggregation,
      @MetricExporterIntervalInMillis Integer metricExportInterval,
      @GcpInstanceId String instanceId,
      @GcpZone String zone,
      @OtlpCollectorExporter Optional<MetricExporter> collectorExporter)
      throws Exception {
    Optional<MetricExporter> metricExporter = Optional.empty();
    if (enableRemoteMetricAggregation) {
      if (!collectorExporter.isPresent()) {
        throw new MetricClientException(
            "OpenTelemetry Collector Address can not be empty if Remote Metric Aggregation is"
                + " enabled.");
      }
      metricExporter = collectorExporter;
    } else {
      return Optional.empty();
    }
    PeriodicMetricReader metricReader =
        PeriodicMetricReader.builder(metricExporter.get())
            .setInterval(java.time.Duration.ofMillis(metricExportInterval))
            .build();

    Resource customResource =
        Resource.create(
            Attributes.builder()
                .put("gcp.resource_type", "gce_instance")
                .put("gcp.gce_instance.zone", zone)
                .put("gcp.gce_instance.instance_id", instanceId)
                .build());

    Resource gcpResource = Resource.create(new GCPResource().getAttributes()).merge(customResource);

    SdkMeterProvider sdkMeterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(metricReader)
            .setResource(gcpResource)
            .build();
    return Optional.of(
        sdkMeterProvider
            // This Instrumentation Scope Info will be added to the metric labels.
            .meterBuilder("scp-operator-metric-client")
            // This Instrumentation Scope Version will be added to the metric labels.
            .setInstrumentationVersion("1.0.0")
            .build());
  }
}
