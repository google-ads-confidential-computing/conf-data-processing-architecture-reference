/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.scp.coordinator.keymanagement.keyhosting.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyset.KeySetsConfig;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create an instance.
 *
 * @param name the name of the key set.
 * @param count the number of keys created for each rotation.
 * @param validityInDays the validity of the keys in number of days.
 * @param overlapPeriodDays used to create overlapping keys
 */
public record KeySetConfig(
    String name, int count, int validityInDays, int overlapPeriodDays, int backfillDays) {

  private static final Logger logger = LoggerFactory.getLogger(KeySetConfig.class);
  private static final int DEFAULT_OVERLAP_PERIOD_DAYS = 0;
  private static final int DEFAULT_BACKFILL_DAYS = 0;
  private static final String PKS_CONFIG_READ_ERROR = "configReadError";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static final String KEY_SETS_CONFIG_ENV_VAR = "KEY_SETS_CONFIG";

  public static ImmutableMap<String, KeySetConfig> buildKeySetConfigMap(
      String keySetConfigJson, LogMetricHelper logHelper) {
    try {
      var keySetsConfig = OBJECT_MAPPER.readValue(keySetConfigJson, KeySetsConfig.class);
      ImmutableMap.Builder<String, KeySetConfig> configBuilder = ImmutableMap.builder();
      for (var keySet : keySetsConfig.keySets()) {
        try {
          // Only care about name, count, validityInDays, overlapPeriodDays, backfillDays
          configBuilder.put(
              keySet.name(),
              new KeySetConfig(
                  keySet.name(),
                  keySet.count(),
                  keySet.validityInDays(),
                  keySet.overlapPeriodDays().orElse(DEFAULT_OVERLAP_PERIOD_DAYS),
                  keySet.backfillDays().orElse(DEFAULT_BACKFILL_DAYS)));
        } catch (NoSuchElementException e) {
          logger.error(
              logHelper.format(
                  PKS_CONFIG_READ_ERROR,
                  ImmutableMap.of("keySet", keySet.name(), "errorReason", e.getMessage())));
        }
      }
      return configBuilder.build();
    } catch (Exception e) {
      logger.error(
          logHelper.format(
              PKS_CONFIG_READ_ERROR,
              ImmutableMap.of("keySet", "all", "errorReason", e.getMessage())));
      return ImmutableMap.of();
    }
  }
}
