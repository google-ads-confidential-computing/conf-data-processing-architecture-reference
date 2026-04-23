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

package com.google.scp.coordinator.keymanagement.testutils.gcp;

import static com.google.scp.coordinator.keymanagement.shared.util.EnvironmentVariables.PROJECT_ID_ENV_VAR;
import static com.google.scp.coordinator.keymanagement.shared.util.EnvironmentVariables.SPANNER_DATABASE_ENV_VAR;
import static com.google.scp.coordinator.keymanagement.shared.util.EnvironmentVariables.SPANNER_INSTANCE_ENV_VAR;

import com.google.common.collect.ImmutableList;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDb;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbConfig;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbTestModule;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;

public class SpannerKeyDbTestUtil {

  public static final String SPANNER_KEY_TABLE_NAME = "KeySets";
  private static final String ENV_VAR_SPANNER_ENDPOINT = "SPANNER_EMULATOR_HOST";
  private static final SpannerKeyDbConfig DEFAULT_DB_CONFIG = SpannerKeyDbTestModule.TEST_DB_CONFIG;

  private SpannerKeyDbTestUtil() {}

  /** Puts itemCount-number of Key items with random values to spannerDb */
  public static void putNItemsRandomValues(SpannerKeyDb spannerKeyDb, String setName, int itemCount)
      throws ServiceException {
    spannerKeyDb.createKeys(
        IntStream.range(0, itemCount)
            .mapToObj(unused -> FakeEncryptionKey.createEncryptionKey(setName))
            .collect(ImmutableList.toImmutableList()));
  }

  /** Inserts the provided key into the provided SpannerKeyDb. */
  public static void putItem(SpannerKeyDb keyDb, EncryptionKey key) throws ServiceException {
    keyDb.createKey(key);
  }

  /**
   * Inserts a single fake key with random key values at the specified expiration time. See {@link
   * FakeEncryptionKey#withExpirationTime(String, Instant)}
   */
  public static void putKeyWithExpiration(
      SpannerKeyDb keyDb, String setName, Instant expirationTime) throws ServiceException {
    putItem(keyDb, FakeEncryptionKey.withExpirationTime(setName, expirationTime));
  }

  public static void putKeyWithNoRotation(SpannerKeyDb keyDb, String setName)
      throws ServiceException {
    putItem(
        keyDb,
        FakeEncryptionKey.createEncryptionKeyBuilder(setName)
            .clearExpirationTime()
            .clearTtlTime()
            .build());
  }

  /**
   * Inserts a single fake key with random key values at the specified activation time. See {@link
   * FakeEncryptionKey#withActivationAndExpirationTimes(String, Instant, Instant)}
   */
  public static void putKeyWithActivationAndExpirationTimes(
      SpannerKeyDb keyDb, String setName, Instant activationTime, Instant expirationTime)
      throws ServiceException {
    putItem(
        keyDb,
        FakeEncryptionKey.withActivationAndExpirationTimes(
            setName, activationTime, expirationTime));
  }

  /**
   * Creates a Spanner Key DB config based on environment variables, falling back to defaults if
   * those aren't available.
   */
  public static SpannerKeyDbConfig getSpannerKeyDbConfig() {
    // Allow configuration of Cloud Functions via parameters passed as environment variables so
    // they can handle different situations (e.g. pointing to different Spanner databases).
    String gcpProjectId =
        System.getenv().getOrDefault(PROJECT_ID_ENV_VAR, DEFAULT_DB_CONFIG.gcpProjectId());
    String spannerInstanceId =
        System.getenv()
            .getOrDefault(SPANNER_INSTANCE_ENV_VAR, DEFAULT_DB_CONFIG.spannerInstanceId());
    String spannerDbName =
        System.getenv().getOrDefault(SPANNER_DATABASE_ENV_VAR, DEFAULT_DB_CONFIG.spannerDbName());
    String endpoint = System.getenv(ENV_VAR_SPANNER_ENDPOINT);

    return SpannerKeyDbConfig.builder()
        .setGcpProjectId(gcpProjectId)
        .setSpannerInstanceId(spannerInstanceId)
        .setSpannerDbName(spannerDbName)
        .setReadStalenessSeconds(0)
        .setEndpointUrl(Optional.ofNullable(endpoint))
        .build();
  }
}
