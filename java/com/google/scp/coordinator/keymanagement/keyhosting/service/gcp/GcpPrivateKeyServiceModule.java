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
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigAllowedMigrators;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines dependencies for GCP implementation of KeyService. This should only be used for Private
 * Key Cloud Runs.
 */
public final class GcpPrivateKeyServiceModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(GcpPrivateKeyServiceModule.class);
  private static final String KEY_SETS_VENDING_CONFIG_ENV_VAR = "KEY_SETS_VENDING_CONFIG";
  private static final String VENDING_CONFIG_ALLOWED_MIGRATORS = "allowed_migrators";

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
    bind(new TypeLiteral<ImmutableSet<String>>() {})
        .annotatedWith(KeySetsVendingConfigAllowedMigrators.class)
        .toInstance(getKeySetsVendingConfigAllowedMigrators());
  }
}
