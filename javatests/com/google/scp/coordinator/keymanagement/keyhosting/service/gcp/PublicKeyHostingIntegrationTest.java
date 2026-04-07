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
import static com.google.scp.coordinator.keymanagement.testutils.gcp.SpannerKeyDbTestUtil.SPANNER_KEY_TABLE_NAME;
import static com.google.scp.shared.api.model.Code.NOT_FOUND;
import static com.google.scp.shared.api.model.Code.OK;
import static com.google.scp.shared.testutils.common.HttpRequestUtil.executeRequestWithRetry;

import com.google.acai.Acai;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.common.collect.ImmutableList;
import com.google.crypto.tink.BinaryKeysetReader;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.subtle.Base64;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.coordinator.keymanagement.shared.dao.common.Annotations.KeyDbClient;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDb;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PublicKeyCloudFunctionContainer;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetActivePublicKeysResponseProto.GetActivePublicKeysResponse;
import com.google.scp.coordinator.testutils.gcp.GcpMultiCoordinatorTestEnvModule;
import com.google.scp.protos.shared.api.v1.ErrorResponseProto.ErrorResponse;
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

/** Integration tests for GCP public key hosting cloud function */
@RunWith(JUnit4.class)
public final class PublicKeyHostingIntegrationTest {
  @Rule public Acai acai = new Acai(GcpMultiCoordinatorTestEnvModule.class);

  private static final String SET_NAME = "ec";
  private static final String correctBetaPath = "/v1beta/sets/ec/publicKeys";
  private static final String incorrectPath = "/v1beta/sets/ec/wrongPath";

  private static final HttpClient client = HttpClient.newHttpClient();
  @Inject @KeyDbClient private DatabaseClient dbClient;
  @Inject private SpannerKeyDb keyDb;
  @Inject @PublicKeyCloudFunctionContainer private CloudFunctionEmulatorContainer functionContainer;

  @After
  public void deleteTable() {
    dbClient.write(ImmutableList.of(Mutation.delete(SPANNER_KEY_TABLE_NAME, KeySet.all())));
  }

  @Test(timeout = 25_000)
  public void getPublicKeys_success() throws Exception {
    var encryptionKey = FakeEncryptionKey.createEncryptionKey(SET_NAME);
    keyDb.createKey(encryptionKey);

    var httpResponse = makeOkRequest();
    assertThat(httpResponse.headers().allValues("cache-control").size()).isEqualTo(1);
    assertThat(httpResponse.headers().firstValue("cache-control").get()).contains("max-age=");

    GetActivePublicKeysResponse.Builder builder = GetActivePublicKeysResponse.newBuilder();
    JsonFormat.parser().merge(httpResponse.body(), builder);
    GetActivePublicKeysResponse response = builder.build();
    assertThat(response.getKeysList()).hasSize(1);
    assertThat(response.getKeys(0).getId()).isEqualTo(encryptionKey.getKeyId());
    CleartextKeysetHandle.read(
        BinaryKeysetReader.withBytes(Base64.decode((response.getKeys(0).getTinkBinary()))));
  }

  @Test(timeout = 25_000)
  public void getPublicKeys_emptyList() throws IOException {
    var httpResponse = makeOkRequest();

    assertThat(httpResponse.headers().map().containsKey("cache-control")).isFalse();
    GetActivePublicKeysResponse.Builder builder = GetActivePublicKeysResponse.newBuilder();
    JsonFormat.parser().merge(httpResponse.body(), builder);
    GetActivePublicKeysResponse response = builder.build();
    assertThat(response.getKeysList()).isEmpty();
  }

  @Test(timeout = 25_000)
  public void getPublicKeys_wrongPath() throws IOException {
    checkBadRequest(HttpRequest.newBuilder().uri(getFunctionUri(incorrectPath)).GET().build());
  }

  @Test(timeout = 25_000)
  public void getPublicKeys_wrongMethod() throws IOException {
    checkBadRequest(
        HttpRequest.newBuilder()
            .uri(getFunctionUri(correctBetaPath))
            .POST(BodyPublishers.noBody())
            .build());
  }

  private HttpResponse<String> makeOkRequest() {
    HttpRequest getRequest =
        HttpRequest.newBuilder().uri(getFunctionUri(correctBetaPath)).GET().build();
    HttpResponse<String> httpResponse = executeRequestWithRetry(client, getRequest);
    assertThat(httpResponse.statusCode()).isEqualTo(OK.getHttpStatusCode());
    return httpResponse;
  }

  private void checkBadRequest(HttpRequest badRequest) throws IOException {
    HttpResponse<String> httpResponse = executeRequestWithRetry(client, badRequest);
    assertThat(httpResponse.statusCode()).isEqualTo(NOT_FOUND.getHttpStatusCode());
    assertThat(httpResponse.headers().map().containsKey("cache-control")).isFalse();

    ErrorResponse.Builder builder = ErrorResponse.newBuilder();
    JsonFormat.parser().merge(httpResponse.body(), builder);
    ErrorResponse response = builder.build();
    assertThat(response.getCode()).isEqualTo(NOT_FOUND.getRpcStatusCode());
    assertThat(response.getMessage()).contains("Resource not found");
    assertThat(response.getDetailsList().toString()).contains("INVALID_URL_PATH_OR_VARIABLE");
  }

  private URI getFunctionUri(String path) {
    return URI.create("http://" + functionContainer.getEmulatorEndpoint() + path);
  }
}
