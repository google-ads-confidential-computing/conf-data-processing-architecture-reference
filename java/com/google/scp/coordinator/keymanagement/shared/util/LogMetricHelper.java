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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LogMetricHelper {

  private final String metricNameSpace;

  // TODO(b/420995280): Remove @Inject annotation as the metricNamespace is not being provided
  // anywhere.
  @Inject
  public LogMetricHelper(String metricNameSpace) throws IllegalArgumentException {
    if (metricNameSpace == null) {
      throw new IllegalArgumentException("Metric name space cannot be null");
    } else {
      this.metricNameSpace = metricNameSpace;
    }
  }

  public String format(String metricName, ImmutableMap<String, String> labels) {
    var metricNameLabel = ImmutableMap.of("metricName", getFullMetricName(metricName));
    return "{"
        + Stream.concat(metricNameLabel.entrySet().stream(), labels.entrySet().stream())
            .map(entry -> String.format("\"%s\":\"%s\"", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(","))
        + "}";
  }

  private String getFullMetricName(String metricName) {
    return metricNameSpace.isEmpty()
        ? metricName
        : String.format("%s/%s", metricNameSpace, metricName);
  }
}
