/*
 * Copyright 2024 Google LLC
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
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.CacheControlMaximum;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.CacheRefreshInMinutes;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.EnableCache;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeyLimit;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigAllowedMigrators;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbConfig;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbModule;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines dependencies for GCP implementation of KeyService. It can be used for both Public Key and
 * Private Key Cloud Functions.
 */
public final class GcpKeyServiceModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(GcpKeyServiceModule.class);
  private static final String KEY_LIMIT_ENV_VAR = "KEY_LIMIT";
  private static final String READ_STALENESS_SEC_ENV_VAR = "READ_STALENESS_SEC";
  private static final String SPANNER_INSTANCE_ENV_VAR = "SPANNER_INSTANCE";
  private static final String SPANNER_DATABASE_ENV_VAR = "SPANNER_DATABASE";
  private static final String SPANNER_ENDPOINT = "SPANNER_ENDPOINT";
  private static final String PROJECT_ID_ENV_VAR = "PROJECT_ID";
  private static final String CACHE_CONTROL_MAXIMUM_ENV_VAR = "CACHE_CONTROL_MAXIMUM";
  private static final String ENABLE_CACHE_ENV_VAR = "ENABLE_CACHE";
  private static final String CACHE_REFRESH_ENV_VAR = "CACHE_REFRESH_IN_MINUTES";
  private static final String KEY_SETS_VENDING_CONFIG_ENV_VAR = "KEY_SETS_VENDING_CONFIG";
  private static final String VENDING_CONFIG_ALLOWED_MIGRATORS = "allowed_migrators";

  /** Returns KeyLimit as Integer from environment variables. Default value of 5 */
  private Integer getKeyLimit() {
    Map<String, String> env = System.getenv();
    return Integer.valueOf(env.getOrDefault(KEY_LIMIT_ENV_VAR, "5"));
  }

  /** Returns ReadStalenessSeconds as Integer from environment variables. Default value of 0 */
  private Integer getReadStalenessSeconds() {
    Map<String, String> env = System.getenv();
    return Integer.valueOf(env.getOrDefault(READ_STALENESS_SEC_ENV_VAR, "0"));
  }

  /**
   * Returns CACHE_CONTROL_MAXIMUM as long from environment var. This value should reflect the
   * KeyRotationInterval time. Default value of 7 days in seconds.
   */
  private static Long getCacheControlMaximum() {
    Map<String, String> env = System.getenv();
    return Long.valueOf(env.getOrDefault(CACHE_CONTROL_MAXIMUM_ENV_VAR, "604800"));
  }

  /** Returns ENABLE_CACHE value from environment variables. Default of false. */
  private static Boolean getEnableCache() {
    Map<String, String> env = System.getenv();
    return Boolean.valueOf(env.getOrDefault(ENABLE_CACHE_ENV_VAR, "false"));
  }

  /** Returns CACHE_REFRESH_IN_MINUTES value from environment variables. Default of 90. */
  private static Integer getCacheRefreshInMinutes() {
    Map<String, String> env = System.getenv();
    return Integer.valueOf(env.getOrDefault(CACHE_REFRESH_ENV_VAR, "90"));
  }

  /**
   * Returns the set of consumers allowed to use key migration data. A consumer is designated by
   * either individual set names or caller email identities.
   */
  private static ImmutableSet<String> getKeySetsVendingConfigAllowedMigrators() {
    ImmutableSet.Builder<String> configBuilder = ImmutableSet.builder();
    Optional.ofNullable(System.getenv(KEY_SETS_VENDING_CONFIG_ENV_VAR))
        .ifPresent(
            configString -> {
              try {
                JsonNode configNode = parseJson(configString);
                JsonNode allowedMigratorsNode =
                    getField(configNode, VENDING_CONFIG_ALLOWED_MIGRATORS);
                if (!allowedMigratorsNode.isArray()) {
                  logger.info(
                      "Vending migration key data disabled. Misconfigured allowed migrators list.");
                  return;
                }
                for (JsonNode allowedMigratorNode : allowedMigratorsNode) {
                  configBuilder.add(allowedMigratorNode.asText());
                }
              } catch (RuntimeException e) {
                logger.warn("Vending migration key data disabled. {}", e.getMessage());
              }
            });
    var config = configBuilder.build();
    logger.info("Allowed migrators vending list: {}.", config);
    return config;
  }

  @Override
  protected void configure() {
    Map<String, String> env = System.getenv();
    String spannerInstanceId = env.getOrDefault(SPANNER_INSTANCE_ENV_VAR, "keydbinstance");
    String spannerDatabaseId = env.getOrDefault(SPANNER_DATABASE_ENV_VAR, "keydb");
    String projectId = env.getOrDefault(PROJECT_ID_ENV_VAR, "adhcloud-tp1");
    String spannerEndpoint = env.get(SPANNER_ENDPOINT);

    // Service layer bindings
    bind(Long.class).annotatedWith(CacheControlMaximum.class).toInstance(getCacheControlMaximum());

    // Business layer bindings
    bind(Integer.class).annotatedWith(KeyLimit.class).toInstance(getKeyLimit());
    bind(Boolean.class).annotatedWith(EnableCache.class).toInstance(getEnableCache());
    bind(Integer.class)
        .annotatedWith(CacheRefreshInMinutes.class)
        .toInstance(getCacheRefreshInMinutes());
    bind(new TypeLiteral<ImmutableSet<String>>() {})
        .annotatedWith(KeySetsVendingConfigAllowedMigrators.class)
        .toInstance(getKeySetsVendingConfigAllowedMigrators());

    // Data layer bindings
    SpannerKeyDbConfig config =
        SpannerKeyDbConfig.builder()
            .setGcpProjectId(projectId)
            .setSpannerInstanceId(spannerInstanceId)
            .setSpannerDbName(spannerDatabaseId)
            .setReadStalenessSeconds(getReadStalenessSeconds())
            .setEndpointUrl(Optional.ofNullable(spannerEndpoint))
            .build();
    bind(SpannerKeyDbConfig.class).toInstance(config);
    install(new SpannerKeyDbModule());
  }
}
