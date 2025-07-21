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

package com.google.scp.coordinator.keymanagement.keystorage.service.gcp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.shared.api.model.Code.INVALID_ARGUMENT;
import static com.google.scp.shared.api.model.Code.OK;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.acai.Acai;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.crypto.tink.KmsClient;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keystorage.service.common.KeyStorageService;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.GetDataKeyTask;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.gcp.GcpCreateKeyTask;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.keymanagement.testutils.FakeKmsClient;
import com.google.scp.coordinator.keymanagement.testutils.InMemoryTestEnv;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.util.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public final class CreateKeyRequestHandlerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final Acai acai = new Acai(InMemoryTestEnv.class);
  @Inject private InMemoryKeyDb keyDb;

  @Mock private HttpRequest httpRequest;
  @Mock private HttpResponse httpResponse;
  // Unused in GCP implementation.
  @Mock GetDataKeyTask getDataKeyTask;

  private BufferedWriter writer;
  private CreateKeyRequestHandler requestHandler;
  private KmsClient kmsClient;
  private KmsClient migrationKmsClient;

  @Before
  public void before() throws IOException {
    kmsClient = new FakeKmsClient();
    migrationKmsClient = new FakeKmsClient();
    KeyStorageService keyStorageService =
        new KeyStorageService(
            new GcpCreateKeyTask(
                keyDb, kmsClient, "fake-kms://$setName$_fake_key_b", migrationKmsClient, "", false),
            getDataKeyTask);
    requestHandler = new CreateKeyRequestHandler(keyStorageService);
    writer = new BufferedWriter(new StringWriter());
    when(httpResponse.getWriter()).thenReturn(writer);
    when(httpRequest.getMethod()).thenReturn("POST");
  }

  @After
  public void after() {
    keyDb.reset();
  }

  @Test
  public void handleRequest_validJson() throws IOException, GeneralSecurityException {
    String keyId = "12345";
    String name = "keys/" + keyId;
    String encryptionKeyType = "MULTI_PARTY_HYBRID_EVEN_KEYSPLIT";
    String publicKeysetHandle = "myPublicKeysetHandle";
    String publicKeyMaterial = Base64.getEncoder().encodeToString("myPublicKeyMaterial".getBytes());
    String privateKeySplit =
        Base64.getEncoder()
            .encodeToString(
                kmsClient
                    .getAead("fake-kms://_fake_key_b")
                    .encrypt("myPrivateKeySplit".getBytes(), "myPublicKeyMaterial".getBytes()));
    String creationTime = "0";
    String expirationTime = "0";
    String testKeyData =
        "{\"publicKeySignature\": \"a\", \"keyEncryptionKeyUri\": \"fake-kms://_fake_key_b\","
            + " \"keyMaterial\": \"c\"}";
    String keyData = "[" + testKeyData + "," + testKeyData + "]";
    String jsonRequest =
        "{\"keyId\":\""
            + keyId
            + "\",\"encryptedKeySplit\":\""
            + privateKeySplit
            + "\",\"key\":{"
            + "\"name\":\""
            + name
            + "\",\"encryptionKeyType\":\""
            + encryptionKeyType
            + "\",\"publicKeysetHandle\":\""
            + publicKeysetHandle
            + "\",\"publicKeyMaterial\":\""
            + publicKeyMaterial
            + "\",\"creationTime\":\""
            + creationTime
            + "\",\"expirationTime\":\""
            + expirationTime
            + "\",\"keyData\":"
            + keyData
            + "}}";
    setRequestBody(jsonRequest);

    requestHandler.handleRequest(httpRequest, httpResponse);

    writer.flush();
    verify(httpResponse).setStatusCode(eq(OK.getHttpStatusCode()));
    byte[] decodedKeys =
        kmsClient
            .getAead("fake-kms://_fake_key_b")
            .decrypt(
                Base64.getDecoder().decode(getPrivateKey(keyId).getJsonEncodedKeyset()),
                new byte[0]);
    assertThat(decodedKeys).isEqualTo("myPrivateKeySplit".getBytes());
  }

  @Test
  public void handleRequest_invalidJson() throws IOException {
    String jsonRequest = "asdf}{";
    setRequestBody(jsonRequest);

    requestHandler.handleRequest(httpRequest, httpResponse);

    writer.flush();
    verify(httpResponse).setStatusCode(eq(INVALID_ARGUMENT.getHttpStatusCode()));
  }

  @Test
  public void handleRequest_invalidHttpMethod() throws IOException, GeneralSecurityException {
    String keyId = "12345";
    String name = "keys/" + keyId;
    String publicKey = "myPublicKey";
    String privateKeySplit = Base64.getEncoder().encodeToString("myPrivateKeySplit".getBytes());
    String expirationTime = "0";
    String jsonRequest =
        "{\"keyId\":\""
            + keyId
            + "\",\"encryptedKeySplit\":\""
            + privateKeySplit
            + "\",\"key\":{"
            + "\"name\":\""
            + name
            + "\",\"publicKey\":\""
            + publicKey
            + "\",\"expirationTime\":"
            + expirationTime
            + "}}";
    setRequestBody(jsonRequest);
    when(httpRequest.getMethod()).thenReturn("PUT");

    requestHandler.handleRequest(httpRequest, httpResponse);

    writer.flush();
    verify(httpResponse).setStatusCode(eq(INVALID_ARGUMENT.getHttpStatusCode()));
  }

  private EncryptionKey getPrivateKey(String keyId) {
    EncryptionKey result;

    try {
      result = keyDb.getKey(keyId);
    } catch (ServiceException ex) {
      result = null;
    }

    return result;
  }

  private void setRequestBody(String body) throws IOException {
    BufferedReader reader = new BufferedReader(new StringReader(body));
    lenient().when(httpRequest.getReader()).thenReturn(reader);
  }
}
