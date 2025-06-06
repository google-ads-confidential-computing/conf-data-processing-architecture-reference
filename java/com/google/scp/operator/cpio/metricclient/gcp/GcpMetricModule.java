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

import static com.google.scp.shared.clients.configclient.model.WorkerParameter.ENABLE_NATIVE_METRIC_AGGREGATION;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.ENABLE_REMOTE_METRIC_AGGREGATION;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.METRIC_EXPORTER_INTERVAL_IN_MILLIS;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.OPENTELEMETRY_COLLECTOR_ADDRESS;

import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.opentelemetry.detectors.GCPResource;
import com.google.cloud.opentelemetry.metric.GoogleCloudMetricExporter;
import com.google.cloud.opentelemetry.metric.MetricConfiguration;
import com.google.cloud.opentelemetry.metric.MetricDescriptorStrategy;
import com.google.common.base.Strings;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.operator.cpio.metricclient.MetricClient;
import com.google.scp.operator.cpio.metricclient.MetricClient.MetricClientException;
import com.google.scp.operator.cpio.metricclient.MetricModule;
import com.google.scp.operator.cpio.metricclient.gcp.Annotations.EnableNativeMetricAggregation;
import com.google.scp.operator.cpio.metricclient.gcp.Annotations.EnableRemoteMetricAggregation;
import com.google.scp.operator.cpio.metricclient.gcp.Annotations.MetricExporterIntervalInMillis;
import com.google.scp.operator.cpio.metricclient.gcp.Annotations.OtlpCollectorAddress;
import com.google.scp.operator.cpio.metricclient.gcp.Annotations.OtlpCollectorExporter;
import com.google.scp.operator.cpio.metricclient.gcp.Annotations.OtlpGoogleCloudMetricExporter;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.ParameterClient.ParameterClientException;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpInstanceId;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpProjectId;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpZone;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
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
  @EnableNativeMetricAggregation
  public Boolean provideEnableNativeMetricAggregation(ParameterClient parameterClient)
      throws ParameterClientException {
    Optional<String> enableNativeMetricAggregationParam =
        parameterClient.getParameter(ENABLE_NATIVE_METRIC_AGGREGATION.name());
    return enableNativeMetricAggregationParam.map(Boolean::valueOf).orElse(false);
  }

  @Provides
  @EnableRemoteMetricAggregation
  public Boolean provideEnableRemoteMetricAggregation(ParameterClient parameterClient)
      throws ParameterClientException {
    Optional<String> enableRemoteMetricAggregationParam =
        parameterClient.getParameter(ENABLE_REMOTE_METRIC_AGGREGATION.name());
    return enableRemoteMetricAggregationParam.map(Boolean::valueOf).orElse(false);
  }

  @Provides
  @MetricExporterIntervalInMillis
  public Integer provideMetricExporterIntervalInMillis(ParameterClient parameterClient)
      throws ParameterClientException {
    Optional<String> metricExportInternalParam =
        parameterClient.getParameter(METRIC_EXPORTER_INTERVAL_IN_MILLIS.name());
    return metricExportInternalParam.map(Integer::valueOf).orElse(60000);
  }

  @Provides
  @OtlpCollectorAddress
  public String provideMetricOtlpCollectorAddress(ParameterClient parameterClient)
      throws ParameterClientException {
    Optional<String> endpointParam =
        parameterClient.getParameter(OPENTELEMETRY_COLLECTOR_ADDRESS.name());
    return endpointParam.isPresent()
        ? String.format("http://%s/v1/metrics", endpointParam.get())
        : "";
  }

  @Provides
  @Singleton
  MetricServiceClient provideMetricServiceClient() throws IOException {
    return MetricServiceClient.create();
  }

  @Provides
  @Singleton
  @OtlpGoogleCloudMetricExporter
  MetricExporter provideOpenTelemetryGcpCloudMetricExporter(@GcpProjectId String gcpProjectId)
      throws IOException {
    return GoogleCloudMetricExporter.createWithConfiguration(
        MetricConfiguration.builder()
            .setProjectId(gcpProjectId)
            .setPrefix("custom.googleapis.com")
            .setDescriptorStrategy(MetricDescriptorStrategy.NEVER_SEND)
            .build());
  }

  @Provides
  @Singleton
  @OtlpCollectorExporter
  Optional<MetricExporter> provideOpenTelemetryHttpMetricExporter(
      @OtlpCollectorAddress String otlpCollectorAddress) throws IOException {
    if (Strings.isNullOrEmpty(otlpCollectorAddress)) {
      return Optional.empty();
    } else {
      return Optional.of(
          OtlpHttpMetricExporter.builder().setEndpoint(otlpCollectorAddress).build());
    }
  }

  @Provides
  @Singleton
  Optional<Meter> provideOpenTelemetryMeter(
      @EnableNativeMetricAggregation Boolean enableNativeMetricAggregation,
      @EnableRemoteMetricAggregation Boolean enableRemoteMetricAggregation,
      @MetricExporterIntervalInMillis Integer metricExportInterval,
      @GcpInstanceId String instanceId,
      @GcpZone String zone,
      @OtlpGoogleCloudMetricExporter MetricExporter gcpMetricExporter,
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
    } else if (enableNativeMetricAggregation) {
      metricExporter = Optional.of(gcpMetricExporter);
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
