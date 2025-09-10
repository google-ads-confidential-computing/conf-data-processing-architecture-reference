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
import static com.google.scp.coordinator.keymanagement.keyhosting.tasks.v1.GetActiveEncryptionKeysTask.END_EPOCH_MILLI_PARAM;
import static com.google.scp.coordinator.keymanagement.keyhosting.tasks.v1.GetActiveEncryptionKeysTask.START_EPOCH_MILLI_PARAM;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.Assert.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.acai.Acai;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigAllowedMigrators;
import com.google.scp.coordinator.keymanagement.keyhosting.common.cache.AllKeysForSetNameCache;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTaskTestBase;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.keymanagement.testutils.InMemoryTestEnv;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetActiveEncryptionKeysResponseProto.GetActiveEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.util.Base64;
import java.util.Optional;
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

  private static final String APPROVED_CALLER = "approved-caller@google.com";
  private static final String TEST_SET = "test-set";
  private static final String TEST_SET_NO_ROTATION = "test-set-no-rotation";
  private static final ImmutableList<EncryptionKey> TEST_KEYS =
      ImmutableList.of(
          FakeEncryptionKey.create(),
          FakeEncryptionKey.create(),
          FakeEncryptionKey.create().toBuilder().setSetName(TEST_SET).build(),
          FakeEncryptionKey.create().toBuilder().setSetName(TEST_SET).build(),
          FakeEncryptionKey.create().toBuilder().setSetName(TEST_SET).build(),
          FakeEncryptionKey.create()
              .toBuilder()
              .setSetName(TEST_SET_NO_ROTATION)
              .clearExpirationTime()
              .build());

  @Rule public final Acai acai = new Acai(InMemoryTestEnv.class);
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Inject private InMemoryKeyDb keyDb;
  @Inject private AllKeysForSetNameCache cache;
  @Inject LogMetricHelper logMetricHelper;
  @Inject @KeySetsVendingConfigAllowedMigrators ImmutableSet<String> allowedMigrators;
  @Inject private GetActiveEncryptionKeysTask task;

  @Mock private RequestContext request;
  @Mock private Matcher matcher;

  @Mock(answer = CALLS_REAL_METHODS)
  private ResponseContext response;

  private InMemoryKeyDb spyKeyDb;

  @Before
  public void setUp() throws Exception {
    keyDb.createKeys(TEST_KEYS);
    super.task = spy(this.task);
    spyKeyDb = spy(keyDb);
  }

  @Test
  public void noParamsThrowsExceptionTest() {
    doReturn("").when(matcher).group("name");
    assertThrows(ServiceException.class, () -> task.execute(matcher, request, response));
  }

  @Test
  public void noStartParamThrowsExceptionTest() {
    doReturn("").when(matcher).group("name");
    doReturn(Optional.of("50000"))
        .when(request)
        .getFirstQueryParameter(END_EPOCH_MILLI_PARAM);
    assertThrows(ServiceException.class, () -> task.execute(matcher, request, response));
  }

  @Test
  public void noEndParamThrowsExceptionTest() {
    doReturn("").when(matcher).group("name");
    doReturn(Optional.of("5"))
        .when(request)
        .getFirstQueryParameter(START_EPOCH_MILLI_PARAM);
    assertThrows(ServiceException.class, () -> task.execute(matcher, request, response));
  }

  @Test
  public void startAfterEndThrowsTest() {
    doReturn("").when(matcher).group("name");
    doReturn(Optional.of("5000"))
        .when(request)
        .getFirstQueryParameter(START_EPOCH_MILLI_PARAM);
    doReturn(Optional.of("500"))
        .when(request)
        .getFirstQueryParameter(END_EPOCH_MILLI_PARAM);
    assertThrows(ServiceException.class, () -> task.execute(matcher, request, response));
  }

  @Test
  public void validStartEndMillis_emptySetName_returnAllExpected() throws Exception {
    var now = now();
    var start = now.plusMillis(1000).toEpochMilli();
    var end = now.plusMillis(10000).toEpochMilli();
    returnAllExpectedValidation("", start, end, 2);
  }

  @Test
  public void validStartEndMillis_testSetName_returnAllExpected() throws Exception {
    var now = now();
    var start = now.plusMillis(1000).toEpochMilli();
    var end = now.plusMillis(10000).toEpochMilli();
    returnAllExpectedValidation(TEST_SET, start, end, 3);
  }

  @Test
  public void validStartEndMillis_noRotationSetName_returnAllExpected() throws Exception {
    var now = now();
    var start = now.plusMillis(1000).toEpochMilli();
    var end = now.plusMillis(10000).toEpochMilli();
    returnAllExpectedValidation(TEST_SET_NO_ROTATION, start, end, 1);
  }

  @Test
  public void endMillisBeforeActivation_returnNothing() throws Exception {
    var now = now();
    var start = now.minus(2, DAYS).toEpochMilli();
    var end = now.minus(1, DAYS).toEpochMilli();
    returnAllExpectedValidation(TEST_SET, start, end, 0);
  }

  @Test
  public void endMillisBeforeActivation_noRotation_returnNothing() throws Exception {
    var now = now();
    var start = now.minus(2, DAYS).toEpochMilli();
    var end = now.minus(1, DAYS).toEpochMilli();
    returnAllExpectedValidation(TEST_SET_NO_ROTATION, start, end, 0);
  }

  @Test
  public void startMillisAfterExpiration_returnNothing() throws Exception {
    var now = now();
    var start = now.plus(100, DAYS).toEpochMilli();
    var end = now.plus(200, DAYS).toEpochMilli();
    returnAllExpectedValidation(TEST_SET, start, end, 0);
  }

  @Test
  public void noRotationKey_startMillisFarInFuture_returnsExpected() throws Exception {
    var now = now();
    var start = now.plus(100, DAYS).toEpochMilli();
    var end = now.plus(200, DAYS).toEpochMilli();
    returnAllExpectedValidation(TEST_SET_NO_ROTATION, start, end, 1);
  }

  @Test
  public void cacheGlobalEnabledTest() throws Exception {
    keysReturned(true, ImmutableSet.of());
    verify(spyKeyDb, never()).getActiveKeys(anyString(), anyInt(), any(), any());
  }

  @Test
  public void cacheGlobalDisabledTest() throws Exception {
    keysReturned(false, ImmutableSet.of());
    verify(spyKeyDb).getActiveKeys(anyString(), anyInt(), any(), any());
  }

  @Test
  public void cacheGlobalDisabled_approvedCallerTest() throws Exception {
    String fakeJwt = createFakeJwt(APPROVED_CALLER);
    doReturn(Optional.of("Bearer: " + fakeJwt)).when(request).getFirstHeader("Authorization");
    keysReturned(false, ImmutableSet.of(APPROVED_CALLER));
    verify(spyKeyDb, never()).getActiveKeys(anyString(), anyInt(), any(), any());
  }

  @Test
  public void cacheGlobalDisabled_approvedSetNameTest() throws Exception {
    keysReturned(false, ImmutableSet.of("test-set"));
    verify(spyKeyDb, never()).getActiveKeys(anyString(), anyInt(), any(), any());
  }

  private void keysReturned(boolean enable, ImmutableSet<String> cacheUsers) throws Exception {
    var now = now();
    var start = now.plus(100, DAYS).toEpochMilli();
    var end = now.plus(200, DAYS).toEpochMilli();

    doReturn("test-set").when(matcher).group("name");
    doReturn(Optional.of(String.valueOf(start)))
        .when(request)
        .getFirstQueryParameter(START_EPOCH_MILLI_PARAM);
    doReturn(Optional.of(String.valueOf(end)))
        .when(request)
        .getFirstQueryParameter(END_EPOCH_MILLI_PARAM);

    var task =
        new GetActiveEncryptionKeysTask(
            spyKeyDb, cache, enable, logMetricHelper, allowedMigrators, cacheUsers);
    task.execute(matcher, request, response);
  }

  private void returnAllExpectedValidation(
      String setName, long start, long end, int expected) throws Exception {

    doReturn(setName).when(matcher).group("name");
    doReturn(Optional.of(String.valueOf(start)))
        .when(request)
        .getFirstQueryParameter(START_EPOCH_MILLI_PARAM);
    doReturn(Optional.of(String.valueOf(end)))
        .when(request)
        .getFirstQueryParameter(END_EPOCH_MILLI_PARAM);

    task.execute(matcher, request, response);
    assertThat(verifyResponse(response).getKeysList()).hasSize(expected);
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

  private String createFakeJwt(String email) {
    String payload = String.format("{\"email\":\"%s\"}", email);
    String encodedPayload =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
    // A dummy header and signature are needed to pass the split check in the task.
    return "header." + encodedPayload + ".signature";
  }
}
