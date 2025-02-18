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

import com.google.inject.Inject;

public final class LogMetricHelper {

  public final String metricNameSpace;

  @Inject
  public LogMetricHelper(String metricNameSpace) throws IllegalArgumentException {
    if (metricNameSpace == null) {
      throw new IllegalArgumentException("Metric name space cannot be null");
    } else {
      this.metricNameSpace = metricNameSpace;
    }
  }

  /** Get a log line denoting a counter metric */
  public String format(String metricName, String label, String value) {
    String metricLabel = String.format("\"%s\":\"%s\"", label, value);
    var log = createSb(metricName, metricLabel);
    log.append("}");
    return log.toString();
  }

  /** Get a log line denoting a counter metric that includes two labels */
  public String format(
      String metricName, String label1, String value1, String label2, String value2) {
    String metricLabel = String.format("\"%s\":\"%s\"", label1, value1);
    String otherLabel = String.format("\"%s\":\"%s\"", label2, value2);
    var log = createSb(metricName, metricLabel);
    log.append(String.format(",%s", otherLabel));
    log.append("}");
    return log.toString();
  }

  /** Get a log line denoting a counter metric that includes setName */
  public String format(String metricName, String setName, String label, String value) {
    return format(metricName, label, value, "setName", setName);
  }

  private StringBuilder createSb(String metricName, String metricLabel) {
    StringBuilder log = new StringBuilder();
    log.append("{");
    String metricFullName =
        metricNameSpace.isEmpty()
            ? metricName
            : String.format("%s/%s", metricNameSpace, metricName);
    log.append(String.format("\"metricName\":\"%s\"", metricFullName));
    log.append(String.format(",%s", metricLabel));
    return log;
  }
}
