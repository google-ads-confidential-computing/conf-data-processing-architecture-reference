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

package com.google.scp.coordinator.keymanagement.keyhosting.tasks.v1;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.acai.Acai;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTaskTestBase;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.keymanagement.testutils.InMemoryTestEnv;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetActiveEncryptionKeysResponseProto.GetActiveEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import java.util.regex.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GetActiveEncryptionKeysTaskTest extends ApiTaskTestBase {

  private static final ImmutableList<EncryptionKey> TEST_KEYS =
      ImmutableList.of(
          FakeEncryptionKey.create(),
          FakeEncryptionKey.create(),
          FakeEncryptionKey.create().toBuilder().setSetName("test-set").build(),
          FakeEncryptionKey.create().toBuilder().setSetName("test-set").build(),
          FakeEncryptionKey.create().toBuilder().setSetName("test-set").build());

  @Rule public final Acai acai = new Acai(InMemoryTestEnv.class);
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Inject private InMemoryKeyDb keyDb;
  @Inject private GetActiveEncryptionKeysTask task;

  @Mock private RequestContext request;
  @Mock private Matcher matcher;

  @Mock(answer = CALLS_REAL_METHODS)
  private ResponseContext response;

  @Before
  public void setUp() throws Exception {
    keyDb.createKeys(TEST_KEYS);
    super.task = spy(this.task);
  }

  @Test
  public void testExecute_happyPath_returnsExpected() throws Exception {
    // Given
    doReturn("").when(matcher).group("name");

    // When
    task.execute(matcher, request, response);

    // Then
    GetActiveEncryptionKeysResponse keys = verifyResponse(response);
    assertThat(keys.getKeysList()).hasSize(2);
    assertThat(keys.getKeysList()).hasSize(2);
    assertThat(keys.getKeysList()).hasSize(2);
  }

  @Test
  public void testExecute_specificSet_returnsExpected() throws Exception {
    // Given
    doReturn("test-set").when(matcher).group("name");

    // When
    task.execute(matcher, request, response);

    // Then
    GetActiveEncryptionKeysResponse keys = verifyResponse(response);
    assertThat(keys.getKeysList()).hasSize(3);
  }

  private static GetActiveEncryptionKeysResponse verifyResponse(ResponseContext response)
      throws Exception {
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(response).setBody(body.capture());
    GetActiveEncryptionKeysResponse.Builder keysBuilder =
        GetActiveEncryptionKeysResponse.newBuilder();

    JsonFormat.parser().merge(body.getValue(), keysBuilder);
    return keysBuilder.build();
  }
}
