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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.scp.operator.cpio.metricclient.MetricClient.MetricClientException;
import com.google.scp.operator.cpio.metricclient.model.CustomMetric;
import com.google.scp.operator.cpio.metricclient.model.MetricType;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.ParameterClient.ParameterClientException;
import io.opentelemetry.api.metrics.Meter;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GcpMetricClientTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  GcpMetricClient metricClient;

  @Mock private MetricServiceClient metricServiceClient;

  @Mock private ParameterClient parameterClient;

  @Mock private Meter meter;

  private static final String PROJECT_ID = "testProject123";
  private static final String ENV_NAME = "test-env";

  @Before
  public void setUp() throws IOException, ParameterClientException {
    parameterClient = Mockito.mock((ParameterClient.class));
    metricServiceClient = Mockito.mock((MetricServiceClient.class));
    meter = Mockito.mock((Meter.class));
    when(parameterClient.getEnvironmentName()).thenReturn(Optional.of(ENV_NAME));
    metricClient =
        new GcpMetricClient(
            metricServiceClient,
            Optional.of(meter),
            parameterClient,
            PROJECT_ID,
            "testInstance123",
            "testZone123",
            false);
  }

  @Test
  public void testRecordMetric_UseMetricServiceClient_DefaultType()
      throws ParameterClientException, MetricClientException {
    // arrange
    CustomMetric metric =
        CustomMetric.builder()
            .setName("testMetric")
            .setNameSpace("scp/test")
            .setUnit("Double")
            .setValue(1.2)
            .build();

    // act
    metricClient.recordMetric(metric);
    // verify
    verify(parameterClient, times(1)).getEnvironmentName();
  }

  @Test
  public void testRecordMetric_UseMetricServiceClient_GaugeType()
      throws ParameterClientException, MetricClientException {
    // arrange
    CustomMetric metric =
        CustomMetric.builder()
            .setName("testMetric")
            .setNameSpace("scp/test")
            .setUnit("Double")
            .setValue(1.2)
            .setMetricType(MetricType.DOUBLE_GAUGE)
            .build();

    // act
    metricClient.recordMetric(metric);
    // verify
    verify(parameterClient, times(1)).getEnvironmentName();
  }

  @Test
  public void testRecordMetric_UseMetricServiceClient_CounterType()
      throws ParameterClientException {
    // arrange
    CustomMetric metric =
        CustomMetric.builder()
            .setName("testMetric")
            .setNameSpace("scp/test")
            .setUnit("Double")
            .setValue(1.2)
            .setMetricType(MetricType.DOUBLE_COUNTER)
            .build();

    assertThrows(MetricClientException.class, () -> metricClient.recordMetric(metric));
  }

  @Test
  public void testRecordMetric_UseMetricServiceClient_HistogramType()
      throws ParameterClientException {
    // arrange
    CustomMetric metric =
        CustomMetric.builder()
            .setName("testMetric")
            .setNameSpace("scp/test")
            .setUnit("Double")
            .setValue(1.2)
            .setMetricType(MetricType.HISTOGRAM)
            .build();

    assertThrows(MetricClientException.class, () -> metricClient.recordMetric(metric));
  }

  @Test
  public void testRecordMetric_UseMetricServiceClient_UnknownType()
      throws ParameterClientException {
    // arrange
    CustomMetric metric =
        CustomMetric.builder()
            .setName("testMetric")
            .setNameSpace("scp/test")
            .setUnit("Double")
            .setValue(1.2)
            .setMetricType(MetricType.UNKNOWN)
            .build();

    assertThrows(MetricClientException.class, () -> metricClient.recordMetric(metric));
  }

  @Test
  public void testRecordMetric_UseOpenTelemetry_DefaultType() throws ParameterClientException {
    // arrange
    metricClient =
        new GcpMetricClient(
            metricServiceClient,
            Optional.of(meter),
            parameterClient,
            PROJECT_ID,
            "testInstance123",
            "testZone123",
            true);
    var argument = ArgumentCaptor.forClass(String.class);
    CustomMetric metric =
        CustomMetric.builder()
            .setName("testMetric")
            .setNameSpace("scp/test")
            .setUnit("Double")
            .setValue(1.2)
            .build();
    // act
    try {
      metricClient.recordMetric(metric);
    } catch (Exception e) {
      // verify
      verify(parameterClient, times(1)).getEnvironmentName();
      verify(meter, times(1)).gaugeBuilder(argument.capture());
      assertThat(argument.getValue()).isEqualTo("scp/test/test-env/testmetric");
    }
  }

  @Test
  public void testRecordMetric_UseOpenTelemetry_GaugeType() throws ParameterClientException {
    // arrange
    metricClient =
        new GcpMetricClient(
            metricServiceClient,
            Optional.of(meter),
            parameterClient,
            PROJECT_ID,
            "testInstance123",
            "testZone123",
            true);
    var argument = ArgumentCaptor.forClass(String.class);
    CustomMetric metric =
        CustomMetric.builder()
            .setName("testMetric")
            .setNameSpace("scp/test")
            .setUnit("Double")
            .setValue(1.2)
            .setMetricType(MetricType.DOUBLE_GAUGE)
            .build();
    // act
    try {
      metricClient.recordMetric(metric);
    } catch (Exception e) {
      // verify
      verify(parameterClient, times(1)).getEnvironmentName();
      verify(meter, times(1)).gaugeBuilder(argument.capture());
      assertThat(argument.getValue()).isEqualTo("scp/test/test-env/testmetric");
    }
  }

  @Test
  public void testRecordMetric_UseOpenTelemetry_SumType() throws ParameterClientException {
    // arrange
    metricClient =
        new GcpMetricClient(
            metricServiceClient,
            Optional.of(meter),
            parameterClient,
            PROJECT_ID,
            "testInstance123",
            "testZone123",
            true);
    var argument = ArgumentCaptor.forClass(String.class);
    CustomMetric metric =
        CustomMetric.builder()
            .setName("testMetric")
            .setNameSpace("scp/test")
            .setUnit("Double")
            .setValue(1.2)
            .setMetricType(MetricType.DOUBLE_COUNTER)
            .build();
    // act
    try {
      metricClient.recordMetric(metric);
    } catch (Exception e) {
      // verify
      verify(parameterClient, times(1)).getEnvironmentName();
      verify(meter, times(1)).counterBuilder(argument.capture());
      assertThat(argument.getValue()).isEqualTo("scp/test/test-env/testmetric");
    }
  }

  @Test
  public void testRecordMetric_UseOpenTelemetry_HistogramType() throws ParameterClientException {
    // arrange
    metricClient =
        new GcpMetricClient(
            metricServiceClient,
            Optional.of(meter),
            parameterClient,
            PROJECT_ID,
            "testInstance123",
            "testZone123",
            true);
    var argument = ArgumentCaptor.forClass(String.class);
    CustomMetric metric =
        CustomMetric.builder()
            .setName("testMetric")
            .setNameSpace("scp/test")
            .setUnit("Double")
            .setValue(1.2)
            .setMetricType(MetricType.HISTOGRAM)
            .build();
    // act
    try {
      metricClient.recordMetric(metric);
    } catch (Exception e) {
      // verify
      verify(parameterClient, times(1)).getEnvironmentName();
      verify(meter, times(1)).histogramBuilder(argument.capture());
      assertThat(argument.getValue()).isEqualTo("scp/test/test-env/testmetric");
    }
  }
}
