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
package com.google.scp.operator.cpio.metricclient;

import static com.google.scp.shared.clients.configclient.model.WorkerParameter.ENABLE_REMOTE_METRIC_AGGREGATION;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.METRIC_EXPORTER_INTERVAL_IN_MILLIS;
import static com.google.scp.shared.clients.configclient.model.WorkerParameter.OPENTELEMETRY_COLLECTOR_ADDRESS;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.operator.cpio.metricclient.model.Annotations.EnableRemoteMetricAggregation;
import com.google.scp.operator.cpio.metricclient.model.Annotations.MetricExporterIntervalInMillis;
import com.google.scp.operator.cpio.metricclient.model.Annotations.OtlpCollectorAddress;
import com.google.scp.operator.cpio.metricclient.model.Annotations.OtlpCollectorExporter;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.ParameterClient.ParameterClientException;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.io.IOException;
import java.util.Optional;

/** Guice module for implementation of {@code MetricClient}. */
public abstract class MetricModule extends AbstractModule {

  /** Returns a {@code Class} object for the {@code MetricClient} implementation. */
  public abstract Class<? extends MetricClient> getMetricClientImpl();

  /**
   * Arbitrary Guice configurations that can be done by the implementing class to support
   * dependencies that are specific to that implementation.
   */
  public void customConfigure() {}

  /**
   * Configures injected dependencies for this module. Includes a binding for {@code MetricClient}
   * in addition to any bindings in the {@code customConfigure} method.
   */
  @Override
  protected final void configure() {
    bind(MetricClient.class).to(getMetricClientImpl());
    customConfigure();
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
}
