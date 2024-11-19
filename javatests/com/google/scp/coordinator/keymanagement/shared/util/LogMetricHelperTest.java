/*
 * Copyright 2024 Google LLC
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

package com.google.scp.coordinator.keymanagement.shared.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LogMetricHelperTest {

  @Test
  public void verifyLogIsCorrect() {
    LogMetricHelper logMetricHelper = new LogMetricHelper("test-namespace");
    String test1 = logMetricHelper.format("test/metric1", "testField1", "testValue1");
    String result1 =
        "{\"metricName\":\"test-namespace/test/metric1\",\"testField1\":\"testValue1\"}";
    assertThat(test1.equals(result1));
  }

  @Test
  public void verifyLogIsCorrectWithEmptyNameSpace() {
    LogMetricHelper logMetricHelper = new LogMetricHelper("");
    String test1 = logMetricHelper.format("test/metric1", "testField1", "testValue1");
    String result1 = "{\"metricName\":\"test/metric1\",\"testField1\":\"testValue1\"}";
    assertThat(test1.equals(result1));
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> new LogMetricHelper(null));

    assertThat(ex.getMessage()).isEqualTo("Metric name space cannot be null");
  }
}
