/*
 * Copyright 2026 Google LLC
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
package com.google.scp.operator.cpio.cryptoclient.model;

import com.google.scp.operator.cpio.cryptoclient.HybridEncryptionKeyService.KeyFetchException;
import com.google.scp.operator.cpio.metricclient.model.CustomMetric;
import com.google.scp.operator.cpio.metricclient.model.MetricType;

/** Utility class to construct key fetching error metrics for cryptoclient. */
public class MetricUtils {
  public static final String METRIC_NAMESPACE = "scp/cryptoclient/metrics";
  public static final String ENCRYPTION_KEY_FETCHING_ERROR_RATE_METRIC_NAME =
      "EncryptionKeyFetchingErrorRate";

  public static CustomMetric ConstructEncryptionKeyFetchingErrorRateMetric(
      KeyFetchException exception) {
    return CustomMetric.builder()
        .setNameSpace(METRIC_NAMESPACE)
        .setName(ENCRYPTION_KEY_FETCHING_ERROR_RATE_METRIC_NAME)
        .setValue(1.0)
        .setUnit("Count")
        .setMetricType(MetricType.DOUBLE_COUNTER)
        .addLabel("ErrorReason", exception.getReason().toString())
        .build();
  }
}
