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

package com.google.scp.coordinator.keymanagement.keyhosting.service.gcp;

import static com.google.scp.shared.gcp.util.JsonHelper.getField;
import static com.google.scp.shared.gcp.util.JsonHelper.parseJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyset.KeySetsConfig;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetConfigMap;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigAllowedMigrators;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigCacheUsers;
import com.google.scp.coordinator.keymanagement.keyhosting.common.KeySetConfig;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines dependencies for GCP implementation of KeyService. This should only be used for Private
 * Key Cloud Runs.
 */
public final class GcpPrivateKeyServiceModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(GcpPrivateKeyServiceModule.class);
  private static final int DEFAULT_OVERLAP_PERIOD_DAYS = 0;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String PKS_CONFIG_READ_ERROR = "configReadError";
  private static final String KEY_SETS_CONFIG_ENV_VAR = "KEY_SETS_CONFIG";
  private static final String KEY_SETS_VENDING_CONFIG_ENV_VAR = "KEY_SETS_VENDING_CONFIG";
  private static final String VENDING_CONFIG_ALLOWED_MIGRATORS = "allowed_migrators";
  private static final String VENDING_CONFIG_CACHE_USERS = "cache_users";

  private static ImmutableSet<String> parseConfigString(
      String configString, String fieldName, LogMetricHelper logHelper) {
    ImmutableSet.Builder<String> configBuilder = ImmutableSet.builder();
    try {
      JsonNode configNode = parseJson(configString);
      JsonNode arrayNode = getField(configNode, fieldName);
      if (!arrayNode.isArray()) {
        logger.warn(
            logHelper.format(fieldName, ImmutableMap.of("errorReason", "json node not an array")));
        return ImmutableSet.of();
      }
      for (JsonNode node : arrayNode) {
        configBuilder.add(node.asText());
      }
    } catch (RuntimeException e) {
      logger.warn(logHelper.format(fieldName, ImmutableMap.of("errorReason", e.getMessage())));
    }
    return configBuilder.build();
  }

  /**
   * Returns the set of consumers allowed to use key migration data. A consumer is designated by
   * either individual set names or caller email identities.
   */
  private static ImmutableSet<String> getKeySetsVendingConfigInfo(
      String fieldName, LogMetricHelper logHelper) {
    return Optional.ofNullable(System.getenv(KEY_SETS_VENDING_CONFIG_ENV_VAR))
        .map(configString -> parseConfigString(configString, fieldName, logHelper))
        .orElse(ImmutableSet.of());
  }

  @Provides
  @Singleton
  @KeySetConfigMap
  ImmutableMap<String, KeySetConfig> readKeySetsConfig() {
    var logHelper = new LogMetricHelper("private_ks_module");
    try {
      var keySetsJson = System.getenv(KEY_SETS_CONFIG_ENV_VAR);
      var keySetsConfig = OBJECT_MAPPER.readValue(keySetsJson, KeySetsConfig.class);

      logger.info("Private KS keyset configs: {}.", keySetsConfig);
      ImmutableMap.Builder<String, KeySetConfig> configBuilder = ImmutableMap.builder();
      for (var keySet : keySetsConfig.keySets()) {
        try {
          // Only care about name, count, validityInDays, overlapPeriodDays
          configBuilder.put(
              keySet.name(),
              KeySetConfig.create(
                  keySet.name(),
                  keySet.count().orElseThrow(),
                  keySet.validityInDays().orElseThrow(),
                  keySet.overlapPeriodDays().orElse(DEFAULT_OVERLAP_PERIOD_DAYS)));
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

  @Override
  protected void configure() {
    var logHelper = new LogMetricHelper("key_service/private_ks_module");
    var allowedMigrators = getKeySetsVendingConfigInfo(VENDING_CONFIG_ALLOWED_MIGRATORS, logHelper);
    logger.info("Allowed migrators vending list: {}.", allowedMigrators);
    bind(new TypeLiteral<ImmutableSet<String>>() {})
        .annotatedWith(KeySetsVendingConfigAllowedMigrators.class)
        .toInstance(allowedMigrators);

    var cacheUsers = getKeySetsVendingConfigInfo(VENDING_CONFIG_CACHE_USERS, logHelper);
    logger.info("Allowed cache users list: {}.", cacheUsers);
    bind(new TypeLiteral<ImmutableSet<String>>() {})
        .annotatedWith(KeySetsVendingConfigCacheUsers.class)
        .toInstance(cacheUsers);
  }
}
