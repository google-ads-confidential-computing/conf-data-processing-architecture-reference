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
import static com.google.scp.coordinator.keymanagement.keyhosting.tasks.v1.ListRecentEncryptionKeysTask.MAX_AGE_SECONDS_PARAM_NAME;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.Assert.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.acai.Acai;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigAllowedMigrators;
import com.google.scp.coordinator.keymanagement.keyhosting.common.cache.ListRecentEncryptionKeysCache;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTaskTestBase;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.keymanagement.testutils.InMemoryTestEnv;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.ListRecentEncryptionKeysResponseProto.ListRecentEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import java.time.Duration;
import java.time.Instant;
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
public class ListRecentEncryptionKeysTaskTest extends ApiTaskTestBase {

  private static final ImmutableList<EncryptionKey> TEST_KEYS =
      ImmutableList.of(
          createKey(null, 7),
          createKey(null, 14),
          createKey("test-set", 7),
          createKey("test-set", 14),
          createKey("test-set", 21));

  @Rule public final Acai acai = new Acai(InMemoryTestEnv.class);
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Inject private InMemoryKeyDb keyDb;
  @Inject private ListRecentEncryptionKeysCache cache;
  @Inject LogMetricHelper logMetricHelper;
  @Inject @KeySetsVendingConfigAllowedMigrators ImmutableSet<String> allowedMigrators;

  @Mock private RequestContext request;
  @Mock private Matcher matcher;

  @Mock(answer = CALLS_REAL_METHODS)
  private ResponseContext response;

  private ListRecentEncryptionKeysTask task;

  @Before
  public void setUp() throws Exception {
    keyDb.createKeys(TEST_KEYS);
    task =
        new ListRecentEncryptionKeysTask(keyDb, cache, true, logMetricHelper, allowedMigrators);
    super.task = spy(task);
  }

  @Test
  public void defaultSet1DayBackCacheTest() throws Exception {
    keysReturned("", 1, 0, true);
  }

  @Test
  public void defaultSet1DayBackNoCacheTest() throws Exception {
    keysReturned("", 1, 0, false);
  }

  @Test
  public void defaultSet1WeekBackCacheTest() throws Exception {
    keysReturned("", 7, 0, true);
  }

  @Test
  public void defaultSet1WeekBackNoCacheTest() throws Exception {
    keysReturned("", 7, 0, false);
  }

  @Test
  public void defaultSet3WeekBackCacheTest() throws Exception {
    keysReturned("", 18, 2, true);
  }

  @Test
  public void defaultSet3WeekBackNoCacheTest() throws Exception {
    keysReturned("", 18, 2, false);
  }

  @Test
  public void namedSet1DayBackCacheTest() throws Exception {
    keysReturned("test-set", 1, 0, true);
  }

  @Test
  public void namedSet1DayBackNoCacheTest() throws Exception {
    keysReturned("test-set", 1, 0, false);
  }

  @Test
  public void namedSet1WeekBackCacheTest() throws Exception {
    keysReturned("test-set", 7, 0, true);
  }

  @Test
  public void namedSet1WeekBackNoCacheTest() throws Exception {
    keysReturned("test-set", 7, 0, false);
  }

  @Test
  public void namedSet4WeekBackCacheTest() throws Exception {
    keysReturned("test-set", 28, 3, true);
  }

  @Test
  public void namedSet4WeekBackNoCacheTest() throws Exception {
    keysReturned("test-set", 28, 3, false);
  }

  private void keysReturned(String setName, int creationDays, int expected, boolean enable)
      throws Exception {
    task =
        new ListRecentEncryptionKeysTask(keyDb, cache, enable, logMetricHelper, allowedMigrators);
    super.task = spy(task);
    doReturn(setName).when(matcher).group("name");
    var daysInSecs = Duration.ofDays(creationDays).toSeconds();
    doReturn(Optional.of(String.valueOf(daysInSecs)))
        .when(request)
        .getFirstQueryParameter(MAX_AGE_SECONDS_PARAM_NAME);

    task.execute(matcher, request, response);
    assertThat(verifyResponse(response).getKeysList()).hasSize(expected);
  }

  @Test
  public void testExecute_missingMaxAgeSeconds_returnsServiceException() {
    // Given
    doReturn("").when(matcher).group("name");
    doReturn(Optional.empty()).when(request).getFirstQueryParameter(MAX_AGE_SECONDS_PARAM_NAME);

    // When
    ThrowingRunnable when = () -> task.execute(matcher, request, response);

    // Then
    ServiceException exception = assertThrows(ServiceException.class, when);
    assertThat(exception.getErrorCode()).isEqualTo(Code.INVALID_ARGUMENT);
    assertThat(exception).hasMessageThat().contains("query parameter is required");
  }

  @Test
  public void testExecute_negativeMaxAgeSeconds_returnsServiceException() {
    // Given
    doReturn("").when(matcher).group("name");
    doReturn(Optional.of("-99")).when(request).getFirstQueryParameter(MAX_AGE_SECONDS_PARAM_NAME);

    // When
    ThrowingRunnable when = () -> task.execute(matcher, request, response);

    // Then
    ServiceException exception = assertThrows(ServiceException.class, when);
    assertThat(exception.getErrorCode()).isEqualTo(Code.INVALID_ARGUMENT);
    assertThat(exception).hasMessageThat().contains("should be positive");
  }

  @Test
  public void testExecute_invalidMaxAgeSeconds_returnsServiceException() {
    // Given
    doReturn("").when(matcher).group("name");
    doReturn(Optional.of("not a number"))
        .when(request)
        .getFirstQueryParameter(MAX_AGE_SECONDS_PARAM_NAME);

    // When
    ThrowingRunnable when = () -> task.execute(matcher, request, response);

    // Then
    ServiceException exception = assertThrows(ServiceException.class, when);
    assertThat(exception.getErrorCode()).isEqualTo(Code.INVALID_ARGUMENT);
    assertThat(exception).hasMessageThat().contains("should be a valid integer");
  }

  private static ListRecentEncryptionKeysResponse verifyResponse(ResponseContext response)
      throws Exception {
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(response).setBody(body.capture());
    ListRecentEncryptionKeysResponse.Builder keysBuilder =
        ListRecentEncryptionKeysResponse.newBuilder();

    JsonFormat.parser().merge(body.getValue(), keysBuilder);
    return keysBuilder.build();
  }

  private static EncryptionKey createKey(String setName, int creationDaysOld) {
    var builder = FakeEncryptionKey.create().toBuilder();
    if (setName != null) {
      builder.setSetName(setName);
    }
    var now = Instant.now();
    var creationTime = now.minus(Duration.ofDays(creationDaysOld));
    return builder
        .setCreationTime(creationTime.toEpochMilli())
        .setExpirationTime(creationTime.plus(1, DAYS).toEpochMilli())
        .build();
  }
}
