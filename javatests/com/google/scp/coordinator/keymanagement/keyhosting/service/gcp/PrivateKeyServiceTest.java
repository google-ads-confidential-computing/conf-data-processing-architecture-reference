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
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.ListRecentEncryptionKeysResponseProto.ListRecentEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyProto;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.testutils.gcp.CloudFunctionEmulatorContainer;
import com.google.scp.shared.testutils.gcp.SpannerEmulatorContainer;
import java.io.IOException;
import java.time.Duration;
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

  private static final Logger logger = LoggerFactory.getLogger(PrivateKeyServiceTest.class);
  private static final ImmutableList<EncryptionKey> TEST_KEYS =
      ImmutableList.of(
          createKey(null, -7),
          createKey(null, -14),
          createKey("test-set", -7),
          createKey("test-set", -14),
          createKey("test-set", -21),
          createKey("test-set-2", -7),
          createKey("test-set-2", 0),
          createKey("test-set-2", 7));

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
  public void v1alphaGetEncryptionKey_existingKey_returnsExpected() throws Exception {
    getExistingKeyTest("v1alpha");
  }

  @Test
  public void v1betaGetEncryptionKey_existingKey_returnsExpected() throws Exception {
    getExistingKeyTest("v1beta");
  }

  private void getExistingKeyTest(String version) throws Exception {
    String endpoint =
        String.format("/%s/encryptionKeys/%s", version, TEST_KEYS.getFirst().getKeyId());
    EncryptionKeyProto.EncryptionKey key = getEncryptionKey(endpoint);
    assertThat(key.getName()).endsWith(TEST_KEYS.getFirst().getKeyId());
  }

  @Test
  public void v1alphaGetEncryptionKey_nonExistingKey_returnsNotFound() throws Exception {
    getNonExistingKeyTest("v1alpha");
  }

  @Test
  public void v1betaGetEncryptionKey_nonExistingKey_returnsNotFound() throws Exception {
    getNonExistingKeyTest("v1beta");
  }

  private void getNonExistingKeyTest(String version) throws Exception {
    String endpoint = String.format("/%s/encryptionKeys/does-not-exist-key", version);
    HttpResponse response = getHttpResponse(endpoint);
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(NOT_FOUND.getHttpStatusCode());
  }

  @Test
  public void v1betaListRecentEncryptionKeys_emptySetName_returnsExpected() throws Exception {
    listRecentEncryptionKeysTest("", ofDays(7), 0);
    listRecentEncryptionKeysTest("", ofDays(8), 1);
    listRecentEncryptionKeysTest("", ofDays(15), 2);
  }

  @Test
  public void v1betaListRecentEncryptionKeys_specificSetName_returnsExpected() throws Exception {
    listRecentEncryptionKeysTest("test-set", ofDays(7), 0);
    listRecentEncryptionKeysTest("test-set", ofDays(8), 1);
    listRecentEncryptionKeysTest("test-set", ofDays(15), 2);
    listRecentEncryptionKeysTest("test-set", ofDays(22), 3);
  }

  @Test
  public void v1betaListRecentEncryptionKeys_setName2_returnsExpected() throws Exception {
    listRecentEncryptionKeysTest("test-set-2", ofDays(1), 2);
    listRecentEncryptionKeysTest("test-set-2", ofDays(8), 3);
  }

  @Test
  public void v1betaListRecentEncryptionKeys_badKeySet_returnsNothing() throws Exception {
    listRecentEncryptionKeysTest("does-not-exist-test-set", ofDays(25), 0);
  }

  private void listRecentEncryptionKeysTest(String setName, Duration expiry, int expectedSize)
      throws Exception {
    var daysInSecs = expiry.toSeconds();
    String endpoint =
        String.format(
            "/v1beta/sets/%s/encryptionKeys:recent?maxAgeSeconds=%s", setName, daysInSecs);
    assertThat(listRecentEncryptionKeys(endpoint).getKeysList()).hasSize(expectedSize);
  }

  @Test
  public void v1betaListRecentEncryptionKeys_missingSetName_returnsExpectedError()
      throws Exception {
    invalidArgumentTest("/v1beta/sets//encryptionKeys:recent");
  }

  @Test
  public void v1betaListRecentEncryptionKeys_missingMaxAgeSeconds_returnsExpectedError()
      throws Exception {
    invalidArgumentTest("/v1beta/sets/test-set/encryptionKeys:recent");
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_specificSetName_returnsExpected() throws Exception {
    var now = now();
    var start = now.minus(1, DAYS).minusMillis(10000).toEpochMilli();
    var end = now.plusMillis(10000).toEpochMilli();
    getActiveKeysValidation("test-set-2", start, end, 1);
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_largeTimeRange_returnsAllKeys() throws Exception {
    var now = now();
    var start = now.minus(15, DAYS).toEpochMilli();
    var end = now.plus(10, DAYS).toEpochMilli();
    getActiveKeysValidation("test-set-2", start, end, 3);
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_largeTimeRange_returnsAllEmptySet() throws Exception {
    var now = now();
    var start = now.minus(25, DAYS).toEpochMilli();
    var end = now.plusMillis(10000).toEpochMilli();
    getActiveKeysValidation("", start, end, 2);
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
        String.format("/v1beta/sets/test-set-2/activeKeys?startEpochMillis=%d", start);
    invalidArgumentTest(endpoint);
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_missingEndParam_returnsExpectedError()
      throws Exception {
    var now = now();
    var end = now.plusMillis(10000).toEpochMilli();
    String endpoint = String.format("/v1beta/sets/test-set-2/activeKeys?endEpochMillis=%d", end);
    invalidArgumentTest(endpoint);
  }

  @Test
  public void v1betaGetActiveEncryptionKeys_endTimeInPast_returnsNothing() throws Exception {
    var now = now();
    var start = now.toEpochMilli();
    var end = now.minus(1, DAYS).toEpochMilli();
    var endpoint =
        String.format(
            "/v1beta/sets/test-set-2/activeKeys?startEpochMillis=%d&endEpochMillis=%d", start, end);
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

  private ListRecentEncryptionKeysResponse listRecentEncryptionKeys(String endpoint)
      throws IOException {
    String body = getHttpBody(endpoint);
    ListRecentEncryptionKeysResponse.Builder keysBuilder =
        ListRecentEncryptionKeysResponse.newBuilder();
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
    var builder = FakeEncryptionKey.create().toBuilder();
    if (setName != null) {
      builder.setSetName(setName);
    }
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
            "count": 5,
            "validity_in_days": 10
          },
          {
            "name": "overlap",
            "count": 1,
            "validity_in_days": 8,
            "overlap_period_days": 6
          },
          {
            "name": "backfill",
            "count": 5,
            "validity_in_days": 10,
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
                      "SPANNER_INSTANCE", keyDbConfig.spannerInstanceId(),
                      "SPANNER_DATABASE", keyDbConfig.spannerDbName(),
                      "PROJECT_ID", keyDbConfig.gcpProjectId(),
                      "KEY_SETS_CONFIG", keySetConfigEnvVar(),
                      "READ_STALENESS_SEC", "2")),
              "PrivateKeyService_deploy.jar",
              "java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp/",
              "com.google.scp.coordinator.keymanagement.keyhosting.service.gcp.PrivateKeyService");
      container.followOutput(new Slf4jLogConsumer(logger).withSeparateOutputStreams());
      return container;
    }
  }
}
