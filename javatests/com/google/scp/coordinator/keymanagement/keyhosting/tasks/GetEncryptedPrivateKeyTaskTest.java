/*
 * Copyright 2022 Google LLC
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

package com.google.scp.coordinator.keymanagement.keyhosting.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.acai.Acai;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.coordinator.keymanagement.keyhosting.common.cache.GetEncryptedKeyCache;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTaskTestBase;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.keymanagement.testutils.InMemoryTestEnv;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyProto;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GetEncryptedPrivateKeyTaskTest extends ApiTaskTestBase {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final Acai acai = new Acai(InMemoryTestEnv.class);

  private static final String SET_NAME = "TestSet";
  private static final EncryptionKey TEST_KEY =
      FakeEncryptionKey.createWithMigration().toBuilder().setSetName(SET_NAME).build();
  private static final String STANDARD_KEYSET = TEST_KEY.getJsonEncodedKeyset();
  private static final String MIGRATION_KEYSET = TEST_KEY.getMigrationJsonEncodedKeyset();

  @Inject private InMemoryKeyDb keyDb;
  @Inject private GetEncryptedPrivateKeyTask task;

  @Mock private RequestContext request;
  @Mock private Matcher matcher;

  @Mock(answer = CALLS_REAL_METHODS)
  private ResponseContext response;

  private InMemoryKeyDb spyKeyDb;

  @Before
  public void setUp() throws Exception {
    keyDb.createKey(TEST_KEY);
    super.task = spy(this.task);
    spyKeyDb = spy(keyDb);
  }

  @Test
  public void testExecute_existingKey_returnsExpected() throws Exception {
    // Given
    doReturn(TEST_KEY.getKeyId()).when(matcher).group("id");

    // When
    task.execute(matcher, request, response);

    // Then
    EncryptionKeyProto.EncryptionKey responseKey = getResponseKey();
    assertThat(responseKey.getName()).endsWith(TEST_KEY.getKeyId());
  }

  @Test
  public void testExecute_nonExistingKey_throwsException() {
    // Given
    doReturn("non-existing-key").when(matcher).group("id");

    // When
    ThrowingRunnable runnable = () -> task.execute(matcher, request, response);

    // Then
    assertThrows(Exception.class, runnable);
  }

  @Test
  public void execute_noAuthHeader_vendsStandardKey() throws Exception {
    // Given no "Authorization" header is provided
    doReturn(Optional.empty()).when(request).getFirstHeader("Authorization");
    doReturn(TEST_KEY.getKeyId()).when(matcher).group("id");

    // When
    task.execute(matcher, request, response);

    // Then
    EncryptionKeyProto.EncryptionKey responseKey = getResponseKey();
    verify(request, never()).getFirstHeader(anyString());
    assertThat(responseKey.getKeyData(0).getKeyMaterial()).isEqualTo(STANDARD_KEYSET);
  }

  @Test
  public void cacheDisabledTest() throws Exception {
    cacheValidation(false);
  }

  @Test
  public void cacheEnabledTest() throws Exception {
    cacheValidation(true);
  }

  private void cacheValidation(boolean enabled) throws Exception {
    doReturn(TEST_KEY.getKeyId()).when(matcher).group("id");
    var taskWithMigrators =
        new GetEncryptedPrivateKeyTask(
            spyKeyDb,
            new GetEncryptedKeyCache(spyKeyDb, new LogMetricHelper("test")),
            enabled,
            new LogMetricHelper("test"),
            "v1Alpha",
            ImmutableSet.of());
    taskWithMigrators.execute(matcher, request, response);
    taskWithMigrators.execute(matcher, request, response);
    taskWithMigrators.execute(matcher, request, response);

    verify(spyKeyDb, times(enabled ? 1 : 3)).getKey(TEST_KEY.getKeyId());
  }

  @Test
  public void execute_unapprovedCaller_vendsStandardKey() throws Exception {
    // Given an unapproved caller sends a valid JWT
    doReturn(TEST_KEY.getKeyId()).when(matcher).group("id");
    String fakeJwt = createFakeJwt("unapproved-caller@google.com");
    doReturn(Optional.of("Bearer: " + fakeJwt)).when(request).getFirstHeader("Authorization");

    vendMigrationKeyValidation(ImmutableSet.of("approved-caller@google.com"), STANDARD_KEYSET);
  }

  @Test
  public void execute_approvedCaller_vendsMigrationKey() throws Exception {
    // Given an approved caller sends a valid JWT
    doReturn(TEST_KEY.getKeyId()).when(matcher).group("id");
    String fakeJwt = createFakeJwt("approved-caller@google.com");
    doReturn(Optional.of("Bearer: " + fakeJwt)).when(request).getFirstHeader("Authorization");

    vendMigrationKeyValidation(ImmutableSet.of("approved-caller@google.com"), MIGRATION_KEYSET);
  }

  @Test
  public void execute_approvedSetName_vendsMigrationKey() throws Exception {
    // Given an approved caller sends a valid JWT
    doReturn(TEST_KEY.getKeyId()).when(matcher).group("id");
    String fakeJwt = createFakeJwt("approved-caller@google.com");
    doReturn(Optional.of("Bearer: " + fakeJwt)).when(request).getFirstHeader("Authorization");

    vendMigrationKeyValidation(ImmutableSet.of(SET_NAME), MIGRATION_KEYSET);
  }

  private void vendMigrationKeyValidation(
      ImmutableSet<String> allowedMigrators, String keySet) throws Exception {
    // When
    var taskWithMigrators =
        new GetEncryptedPrivateKeyTask(
            keyDb,
            new GetEncryptedKeyCache(keyDb, new LogMetricHelper("test")),
            true,
            new LogMetricHelper("test"),
            "v1Alpha",
            allowedMigrators);
    taskWithMigrators.execute(matcher, request, response);

    // Then
    EncryptionKeyProto.EncryptionKey responseKey = getResponseKey();
    assertThat(responseKey.getKeyData(0).getKeyMaterial()).isEqualTo(keySet);
    verify(request).getFirstHeader("Authorization");
  }

  /** Helper to extract the response body and parse it into an EncryptionKey proto. */
  private EncryptionKeyProto.EncryptionKey getResponseKey() throws Exception {
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(response).setBody(body.capture());
    EncryptionKeyProto.EncryptionKey.Builder keyBuilder =
        EncryptionKeyProto.EncryptionKey.newBuilder();
    JsonFormat.parser().merge(body.getValue(), keyBuilder);
    return keyBuilder.build();
  }

  /** Creates a fake JWT string with a given email claim for testing. */
  private String createFakeJwt(String email) {
    String payload = String.format("{\"email\":\"%s\"}", email);
    String encodedPayload =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
    // A dummy header and signature are needed to pass the split check in the task.
    return "header." + encodedPayload + ".signature";
  }
}
