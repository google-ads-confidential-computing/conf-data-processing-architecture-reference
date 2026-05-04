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
package com.google.scp.coordinator.keymanagement.keyhosting.service.gcp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.coordinator.keymanagement.keyhosting.common.KeySetConfig.KEY_SETS_CONFIG_ENV_VAR;
import static com.google.scp.coordinator.keymanagement.keyhosting.service.gcp.GcpKeyServiceModule.READ_STALENESS_SEC_ENV_VAR;
import static com.google.scp.coordinator.keymanagement.shared.util.EnvironmentVariables.ENVIRONMENT_ENV_VAR;
import static com.google.scp.coordinator.keymanagement.shared.util.EnvironmentVariables.PROJECT_ID_ENV_VAR;
import static com.google.scp.coordinator.keymanagement.shared.util.EnvironmentVariables.SPANNER_DATABASE_ENV_VAR;
import static com.google.scp.coordinator.keymanagement.shared.util.EnvironmentVariables.SPANNER_INSTANCE_ENV_VAR;
import static com.google.scp.shared.api.model.Code.INVALID_ARGUMENT;
import static com.google.scp.shared.api.model.Code.NOT_FOUND;
import static com.google.scp.shared.testutils.gcp.CloudFunctionEmulatorContainer.startContainerAndConnectToSpannerWithEnvs;
import static java.time.Duration.ofDays;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;

import com.google.acai.Acai;
import com.google.cloud.spanner.DatabaseClient;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbConfig;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbTestModule;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetActiveEncryptionKeysResponseProto.GetActiveEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetKeysetMetadataResponseProto.GetKeysetMetadataResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyProto;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.testutils.gcp.CloudFunctionEmulatorContainer;
import com.google.scp.shared.testutils.gcp.SpannerEmulatorContainer;
import java.io.IOException;
import java.util.Optional;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@RunWith(JUnit4.class)
public final class PrivateKeyServiceTest {

  private static final String SET_NAME = "test-set-name";
  private static final String SET_NAME_2 = "test-set-name-2";
  private static final Logger logger = LoggerFactory.getLogger(PrivateKeyServiceTest.class);
  private static final ImmutableList<EncryptionKey> TEST_KEYS =
      ImmutableList.of(
          createKey(SET_NAME, -7),
          createKey(SET_NAME, -14),
          createKey(SET_NAME, -21),
          createKey(SET_NAME_2, -7),
          createKey(SET_NAME_2, 0),
          createKey(SET_NAME_2, 7));

  @Rule public final Acai acai = new Acai(TestModule.class);

  @Inject private DatabaseClient dbClient;
  @Inject private KeyDb keyDb;
  @Inject private CloudFunctionEmulatorContainer container;

  @Before
  public void setUp() throws Exception {
    keyDb.createKeys(TEST_KEYS);
    // Sleeping for 2s due to the read staleness configured below.
    Thread.sleep(2000);
  }

  @Test
  public void v1betaGetEncryptionKey_existingKey_returnsExpected() throws Exception {
    String endpoint = String.format("/v1beta/encryptionKeys/%s", TEST_KEYS.getFirst().getKeyId());
    EncryptionKeyProto.EncryptionKey key = getEncryptionKey(endpoint);
    assertThat(key.getName()).endsWith(TEST_KEYS.getFirst().getKeyId());
  }

  @Test
  public void v1betaGetEncryptionKey_nonExistingKey_returnsNotFound() throws Exception {
    String endpoint = "/v1beta/encryptionKeys/does-not-exist-key";
    HttpResponse response = getHttpResponse(endpoint);
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(NOT_FOUND.getHttpStatusCode());
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_specificSetName_returnsExpected() throws Exception {
    var now = now();
    var start = now.minus(1, DAYS).minusMillis(10000).toEpochMilli();
    var end = now.plusMillis(10000).toEpochMilli();
    getActiveKeysValidation(SET_NAME_2, start, end, 1);
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_largeTimeRange_returnsAllKeys() throws Exception {
    var now = now();
    var start = now.minus(25, DAYS).toEpochMilli();
    var end = now.plus(1, DAYS).toEpochMilli();
    getActiveKeysValidation(SET_NAME, start, end, 3);
  }

  private void getActiveKeysValidation(String setName, long start, long end, int expected)
      throws Exception {
    String endpoint =
        String.format(
            "/v1beta/sets/%s/activeKeys?startEpochMillis=%d&endEpochMillis=%d",
            setName, start, end);

    // When
    GetActiveEncryptionKeysResponse keys = getActiveEncryptionKeys(endpoint);

    // Then
    assertThat(keys.getKeysList()).hasSize(expected);
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_missingStartParam_returnsExpectedError()
      throws Exception {
    var now = now();
    var start = now.minusMillis(10000).toEpochMilli();
    String endpoint =
        String.format("/v1beta/sets/%s/activeKeys?startEpochMillis=%d", SET_NAME_2, start);
    invalidArgumentTest(endpoint);
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_missingEndParam_returnsExpectedError()
      throws Exception {
    var now = now();
    var end = now.plusMillis(10000).toEpochMilli();
    String endpoint =
        String.format("/v1beta/sets/%s/activeKeys?endEpochMillis=%d", SET_NAME_2, end);
    invalidArgumentTest(endpoint);
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_endTimeInPast_returnsNothing() throws Exception {
    var now = now();
    var start = now.toEpochMilli();
    var end = now.minus(1, DAYS).toEpochMilli();
    var endpoint =
        String.format(
            "/v1beta/sets/%s/activeKeys?startEpochMillis=%d&endEpochMillis=%d",
            SET_NAME_2, start, end);
    invalidArgumentTest(endpoint);
  }

  @Test
  public void v1betaGetKeysetMetadata_noOverlap_returnsExpected() throws Exception {
    getActiveKeysetMetadataOverlapValidation("noOverlap", 5, 10);
  }

  @Test
  public void v1betaGetKeysetMetadata_overlap_returnsExpected() throws Exception {
    getActiveKeysetMetadataOverlapValidation("overlap", 4, 2);
  }

  @Test
  public void v1betaGetKeysetMetadata_backfill_returnsExpected() throws Exception {
    String endpoint = String.format("/v1beta/sets/%s/keysetMetadata", "backfill");
    assertThat(getMetadata(endpoint).getBackfillDays()).isEqualTo(3);
  }

  private void getActiveKeysetMetadataOverlapValidation(String setName, int keyCount, int cadence)
      throws Exception {
    String endpoint = String.format("/v1beta/sets/%s/keysetMetadata", setName);
    var metadata = getMetadata(endpoint);
    assertThat(metadata.getActiveKeyCount()).isEqualTo(keyCount);
    assertThat(metadata.getActiveKeyCadenceDays()).isEqualTo(cadence);
  }

  private void invalidArgumentTest(String endpoint) throws Exception {
    HttpResponse response = getHttpResponse(endpoint);
    assertThat(response.getStatusLine().getStatusCode())
        .isEqualTo(INVALID_ARGUMENT.getHttpStatusCode());
  }

  private EncryptionKeyProto.EncryptionKey getEncryptionKey(String endpoint) throws IOException {
    String body = getHttpBody(endpoint);
    EncryptionKeyProto.EncryptionKey.Builder keysBuilder =
        EncryptionKeyProto.EncryptionKey.newBuilder();
    JsonFormat.parser().merge(body, keysBuilder);
    return keysBuilder.build();
  }

  private GetActiveEncryptionKeysResponse getActiveEncryptionKeys(String endpoint)
      throws IOException {
    String body = getHttpBody(endpoint);
    GetActiveEncryptionKeysResponse.Builder keysBuilder =
        GetActiveEncryptionKeysResponse.newBuilder();
    JsonFormat.parser().merge(body, keysBuilder);
    return keysBuilder.build();
  }

  private GetKeysetMetadataResponse getMetadata(String endpoint) throws IOException {
    String body = getHttpBody(endpoint);
    var metadataBuilder = GetKeysetMetadataResponse.newBuilder();
    JsonFormat.parser().merge(body, metadataBuilder);
    return metadataBuilder.build();
  }

  private String getHttpBody(String endpoint) throws IOException {
    HttpClientBuilder builder =
        HttpClients.custom()
            .setDefaultRequestConfig(
                // Prevent HTTP client library from stripping empty path params.
                RequestConfig.custom().setNormalizeUri(false).build());
    try (CloseableHttpClient client = builder.build()) {
      var response =
          client.execute(
              new HttpGet(String.format("http://%s%s", container.getEmulatorEndpoint(), endpoint)));
      return EntityUtils.toString(response.getEntity());
    }
  }

  private HttpResponse getHttpResponse(String endpoint) throws IOException {
    HttpClientBuilder builder =
        HttpClients.custom()
            .setDefaultRequestConfig(
                // Prevent HTTP client library from stripping empty path params.
                RequestConfig.custom().setNormalizeUri(false).build());
    try (CloseableHttpClient client = builder.build()) {
      return client.execute(
          new HttpGet(String.format("http://%s%s", container.getEmulatorEndpoint(), endpoint)));
    }
  }

  private static EncryptionKey createKey(String setName, int expiryPlusDays) {
    var builder = FakeEncryptionKey.createEncryptionKeyBuilder(setName);
    var activationEpochMilli = now().plus(ofDays(expiryPlusDays - 1)).toEpochMilli();
    var expirationEpochMilli = now().plus(ofDays(expiryPlusDays)).toEpochMilli();
    return builder
        .setActivationTime(activationEpochMilli)
        .setExpirationTime(expirationEpochMilli)
        .build();
  }

  private static final class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new SpannerKeyDbTestModule());
    }

    private static String keySetConfigEnvVar() {
      return """
      {
        "key_sets": [
          {
            "name": "noOverlap",
            "tink_template": "test_template1",
            "count": 5,
            "validity_in_days": 10,
            "ttl_in_days": 7
          },
          {
            "name": "overlap",
            "tink_template": "test_template1",
            "count": 1,
            "validity_in_days": 8,
            "ttl_in_days": 7,
            "overlap_period_days": 6
          },
          {
            "name": "backfill",
            "tink_template": "test_template1",
            "count": 5,
            "validity_in_days": 10,
            "ttl_in_days": 7,
            "backfill_days": 3
          }
        ]
      }
      """;
    }

    @Singleton
    @Provides
    CloudFunctionEmulatorContainer start(
        KeyDb keyDb,
        SpannerKeyDbConfig keyDbConfig,
        SpannerEmulatorContainer spannerEmulatorContainer) {
      Preconditions.checkNotNull(keyDb, "Key database is required to start key database.");
      var container =
          startContainerAndConnectToSpannerWithEnvs(
              spannerEmulatorContainer,
              Optional.of(
                  ImmutableMap.of(
                      SPANNER_INSTANCE_ENV_VAR, keyDbConfig.spannerInstanceId(),
                      SPANNER_DATABASE_ENV_VAR, keyDbConfig.spannerDbName(),
                      PROJECT_ID_ENV_VAR, keyDbConfig.gcpProjectId(),
                      ENVIRONMENT_ENV_VAR, "test-environment",
                      KEY_SETS_CONFIG_ENV_VAR, keySetConfigEnvVar(),
                      READ_STALENESS_SEC_ENV_VAR, "2")),
              "PrivateKeyService_deploy.jar",
              "java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp/",
              "com.google.scp.coordinator.keymanagement.keyhosting.service.gcp.PrivateKeyService");
      container.followOutput(new Slf4jLogConsumer(logger).withSeparateOutputStreams());
      return container;
    }
  }
}
