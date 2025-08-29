/*
 * Copyright 2025 Google LLC
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

package com.google.scp.coordinator.keymanagement.keyhosting.common.cache;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AllKeysForSetNameCacheTest {

  private static final String SET_NAME = "setName";
  private static final LogMetricHelper LOG_METRIC_HELPER = new LogMetricHelper("test");
  private static final EncryptionKey ENCRYPTION_KEY =
      EncryptionKey.newBuilder().setKeyId("keyId").setKeyType("keyType").build();
  private static final ImmutableList<EncryptionKey> ENCRYPTION_KEYS =
      ImmutableList.of(ENCRYPTION_KEY, ENCRYPTION_KEY);
  private static final ServiceException SERVICE_EXCEPTION =
      new ServiceException(Code.NOT_FOUND, "errorReason", "msg");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeyDb mockKeyDb;

  @Test
  public void readDbThrowsTest() throws Exception {
    when(mockKeyDb.listAllKeysForSetName(anyString())).thenThrow(SERVICE_EXCEPTION);
    var cache = new AllKeysForSetNameCache(mockKeyDb, 1, LOG_METRIC_HELPER);
    assertThrows(ServiceException.class, () -> cache.get(SET_NAME));
  }

  @Test
  public void readCacheAfterRefreshTest() throws Exception {
    when(mockKeyDb.listAllKeysForSetName(eq(SET_NAME))).thenReturn(ENCRYPTION_KEYS);
    var cache = new AllKeysForSetNameCache(mockKeyDb, 1, SECONDS, LOG_METRIC_HELPER);

    assertThat(cache.get(SET_NAME)).isEqualTo(ENCRYPTION_KEYS);
    Thread.sleep(1000 * 3); // 3 seconds
    assertThat(cache.get(SET_NAME)).isEqualTo(ENCRYPTION_KEYS);
    verify(mockKeyDb, times(2)).listAllKeysForSetName(eq(SET_NAME));
  }

  @Test
  public void readDbSucceeds_dbCalledOnceTest() throws Exception {
    when(mockKeyDb.listAllKeysForSetName(eq(SET_NAME))).thenReturn(ENCRYPTION_KEYS);
    var cache = new AllKeysForSetNameCache(mockKeyDb, 1, LOG_METRIC_HELPER);
    assertThat(cache.get(SET_NAME)).isEqualTo(ENCRYPTION_KEYS);
    assertThat(cache.get(SET_NAME)).isEqualTo(ENCRYPTION_KEYS);
    assertThat(cache.get(SET_NAME)).isEqualTo(ENCRYPTION_KEYS);
    verify(mockKeyDb).listAllKeysForSetName(eq(SET_NAME));
  }
}
