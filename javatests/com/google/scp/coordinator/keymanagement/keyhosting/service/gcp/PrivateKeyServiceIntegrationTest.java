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

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey.withActivationAndExpirationTimes;
import static com.google.scp.coordinator.keymanagement.testutils.gcp.SpannerKeyDbTestUtil.SPANNER_KEY_TABLE_NAME;
import static com.google.scp.shared.api.model.Code.NOT_FOUND;
import static com.google.scp.shared.api.model.Code.OK;
import static com.google.scp.shared.testutils.common.HttpRequestUtil.executeRequestWithRetry;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;

import com.google.acai.Acai;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.coordinator.keymanagement.shared.dao.common.Annotations.KeyDbClient;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDb;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PrivateKeyServiceCloudFunctionContainer;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetActiveEncryptionKeysResponseProto.GetActiveEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.testutils.gcp.GcpMultiCoordinatorTestEnvModule;
import com.google.scp.protos.shared.api.v1.ErrorResponseProto.ErrorResponse;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.util.ErrorUtil;
import com.google.scp.shared.testutils.gcp.CloudFunctionEmulatorContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for GCP encryption key service cloud function */
@RunWith(JUnit4.class)
public final class PrivateKeyServiceIntegrationTest {
  @Rule public Acai acai = new Acai(GcpMultiCoordinatorTestEnvModule.class);

  private static final HttpClient client = HttpClient.newHttpClient();

  // GetEncryptionKeys v1alpha
  private static final String correctPathAlpha = "/v1alpha/encryptionKeys/";
  private static final String incorrectPathAlpha = "/v1alpha/wrongPath/";

  // GetEncryptionKeys v1beta
  private static final String correctPathBeta = "/v1beta/encryptionKeys/";
  private static final String incorrectPathBeta = "/v1beta/wrongPath/";

  // GetActiveKeys v1beta
  private static final String CORRECT_ACTIVE_KEYS =
      "/v1beta/sets/%s/activeKeys?startEpochMillis=%d&endEpochMillis=%d";
  private static final String INCORRECT_ACTIVE_KEYS =
      "/v1beta/sets/%s/activeKey?startEpochMillis=%d&endEpochMillis=%d";

  // GetKeysetMetadata v1beta
  private static final String CORRECT_KEY_METADATA = "/v1beta/sets/%s/keysetMetadata";
  private static final String INCORRECT_KEY_METADATA = "/v1beta/sets/%s/keysetMeta";

  @Inject @KeyDbClient private DatabaseClient dbClient;
  @Inject private SpannerKeyDb keyDb;

  @Inject @PrivateKeyServiceCloudFunctionContainer
  private CloudFunctionEmulatorContainer functionContainer;

  @After
  public void deleteTable() {
    dbClient.write(ImmutableList.of(Mutation.delete(SPANNER_KEY_TABLE_NAME, KeySet.all())));
  }

  // GetEncryptionKeys v1alpha & v1beta
  @Test(timeout = 25_000)
  public void getEncryptionKeys_alphaSuccess() throws ServiceException {
    getEncryptionKeysSuccess(correctPathAlpha);
  }

  @Test(timeout = 25_000)
  public void getEncryptionKeys_betaSuccess() throws ServiceException {
    getEncryptionKeysSuccess(correctPathBeta);
  }

  private void getEncryptionKeysSuccess(String path) throws ServiceException {
    EncryptionKey encryptionKey = FakeEncryptionKey.create();

    keyDb.createKey(encryptionKey);
    HttpRequest getRequest =
        HttpRequest.newBuilder().uri(getFunctionUri(path + encryptionKey.getKeyId())).GET().build();

    HttpResponse<String> httpResponse = executeRequestWithRetry(client, getRequest);

    assertThat(httpResponse.statusCode()).isEqualTo(OK.getHttpStatusCode());
    // Cannot map to abstract autovalue class, so the body String will be parsed instead
    String response = httpResponse.body();
    assertThat(response).contains("\"name\":\"encryptionKeys/");
    assertThat(response).contains("\"keyMaterial\":\"" + encryptionKey.getJsonEncodedKeyset());
  }

  @Test(timeout = 25_000)
  public void getEncryptionKeys_alphaNotFoundTest() {
    getEncryptionKeysNotFound(correctPathAlpha);
  }

  @Test(timeout = 25_000)
  public void getEncryptionKeys_betaNotFoundTest() {
    getEncryptionKeysNotFound(correctPathBeta);
  }

  public void getEncryptionKeysNotFound(String path) {
    HttpRequest getRequest =
        HttpRequest.newBuilder().uri(getFunctionUri(path + "invalid")).GET().build();
    HttpResponse<String> httpResponse = executeRequestWithRetry(client, getRequest);

    assertThat(httpResponse.statusCode()).isEqualTo(NOT_FOUND.getHttpStatusCode());

    ErrorResponse response = ErrorUtil.parseErrorResponse(httpResponse.body());
    assertThat(response.getCode()).isEqualTo(NOT_FOUND.getRpcStatusCode());
    assertThat(response.getMessage()).contains("Unable to find item with keyId");
    assertThat(response.getDetailsList().toString()).contains("MISSING_KEY");
  }

  @Test(timeout = 25_000)
  public void getEncryptionKeys_alphaWrongPath() {
    getEncryptionKeysWrongPath(incorrectPathAlpha);
  }

  @Test(timeout = 25_000)
  public void getEncryptionKeys_betaWrongPath() {
    getEncryptionKeysWrongPath(incorrectPathBeta);
  }

  public void getEncryptionKeysWrongPath(String path) {
    HttpRequest getRequest = HttpRequest.newBuilder().uri(getFunctionUri(path)).GET().build();

    HttpResponse<String> httpResponse = executeRequestWithRetry(client, getRequest);
    verifyNotFoundResponse(httpResponse);
  }

  @Test(timeout = 25_000)
  public void getEncryptionKeys_alphaWrongMethod() {
    getEncryptionKeysWrongMethod(correctPathAlpha);
  }

  @Test(timeout = 25_000)
  public void getEncryptionKeys_betaWrongMethod() {
    getEncryptionKeysWrongMethod(correctPathBeta);
  }

  public void getEncryptionKeysWrongMethod(String path) {
    HttpRequest getRequest =
        HttpRequest.newBuilder().uri(getFunctionUri(path)).POST(BodyPublishers.noBody()).build();

    HttpResponse<String> httpResponse = executeRequestWithRetry(client, getRequest);
    verifyNotFoundResponse(httpResponse);
  }

  // GetActiveEncryptionKeys v1beta
  @Test(timeout = 25_000)
  public void getActiveKeys_wrongPath() {
    var now = now();
    var start = now.minus(25, DAYS).toEpochMilli();
    var end = now.plusMillis(10000).toEpochMilli();
    String endpoint = String.format(INCORRECT_ACTIVE_KEYS, "valid", start, end);
    HttpRequest getRequest = HttpRequest.newBuilder().uri(getFunctionUri(endpoint)).GET().build();

    HttpResponse<String> httpResponse = executeRequestWithRetry(client, getRequest);
    verifyNotFoundResponse(httpResponse);
  }

  @Test(timeout = 25_000)
  public void getActiveKeys_returnsEmptyListTest() throws IOException {
    var now = now();
    var start = now.minus(25, DAYS).toEpochMilli();
    var end = now.plusMillis(10000).toEpochMilli();
    assertThat(getActiveKeys("set", start, end).getKeysList()).hasSize(0);
  }

  @Test(timeout = 25_000)
  public void getActiveKeys_returnsCorrectKeysTest() throws Exception {
    var now = now();
    var key1 =
        withActivationAndExpirationTimes("different", now.minus(4, DAYS), now.minus(2, DAYS));
    var key2 = withActivationAndExpirationTimes("correct", now.minus(4, DAYS), now.minus(2, DAYS));
    var key3 = withActivationAndExpirationTimes("correct", now.plus(2, DAYS), now.plus(4, DAYS));
    var key4 =
        withActivationAndExpirationTimes("correct", now.minus(30, DAYS), now.minus(28, DAYS));
    keyDb.createKey(key1);
    keyDb.createKey(key2);
    keyDb.createKey(key3);
    keyDb.createKey(key4);

    var start = now.minus(25, DAYS).toEpochMilli();
    var end = now.plusMillis(10000).toEpochMilli();

    var keys = getActiveKeys("correct", start, end).getKeysList();
    assertThat(keys).hasSize(1);
    assertThat(keys.getFirst().getName()).isEqualTo("encryptionKeys/" + key2.getKeyId());
  }

  private GetActiveEncryptionKeysResponse getActiveKeys(String setName, long start, long end)
      throws IOException {
    String endpoint = String.format(CORRECT_ACTIVE_KEYS, setName, start, end);
    HttpRequest getRequest = HttpRequest.newBuilder().uri(getFunctionUri(endpoint)).GET().build();
    HttpResponse<String> httpResponse = executeRequestWithRetry(client, getRequest);

    assertThat(httpResponse.statusCode()).isEqualTo(OK.getHttpStatusCode());

    var builder = GetActiveEncryptionKeysResponse.newBuilder();
    JsonFormat.parser().merge(httpResponse.body(), builder);
    return builder.build();
  }

  // GetKeysetMetadata v1beta
  // TODO(b/444026189): determine how to set environment variable to test this endpoint
  @Test(timeout = 25_000)
  public void getKeysetMetadata_wrongPathTest() {
    String endpoint = String.format(INCORRECT_KEY_METADATA, "overlap");
    HttpRequest getRequest = HttpRequest.newBuilder().uri(getFunctionUri(endpoint)).GET().build();
    HttpResponse<String> httpResponse = executeRequestWithRetry(client, getRequest);
    verifyNotFoundResponse(httpResponse);
  }

  @Test(timeout = 25_000)
  public void getKeysetMetadata_missingSetNameTest() {
    String endpoint = String.format(CORRECT_KEY_METADATA, "not-there");
    HttpRequest getRequest = HttpRequest.newBuilder().uri(getFunctionUri(endpoint)).GET().build();
    HttpResponse<String> httpResponse = executeRequestWithRetry(client, getRequest);

    assertThat(httpResponse.statusCode()).isEqualTo(NOT_FOUND.getHttpStatusCode());
    ErrorResponse response = ErrorUtil.parseErrorResponse(httpResponse.body());
    assertThat(response.getCode()).isEqualTo(NOT_FOUND.getRpcStatusCode());
    assertThat(response.getMessage()).contains("Do not have config");
  }

  private static void verifyNotFoundResponse(HttpResponse<String> httpResponse) {
    assertThat(httpResponse.statusCode()).isEqualTo(NOT_FOUND.getHttpStatusCode());
    assertThat(httpResponse.headers().map().containsKey("cache-control")).isFalse();

    ErrorResponse response = ErrorUtil.parseErrorResponse(httpResponse.body());
    assertThat(response.getCode()).isEqualTo(NOT_FOUND.getRpcStatusCode());
    assertThat(response.getMessage()).contains("Resource not found");
    assertThat(response.getDetailsList().toString()).contains("INVALID_URL_PATH_OR_VARIABLE");
  }

  private URI getFunctionUri(String path) {
    return URI.create("http://" + functionContainer.getEmulatorEndpoint() + path);
  }
}
