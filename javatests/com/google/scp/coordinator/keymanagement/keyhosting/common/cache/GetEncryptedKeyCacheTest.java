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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
public class GetEncryptedKeyCacheTest {

  private static final String KEY = "key";
  private static final LogMetricHelper LOG_METRIC_HELPER = new LogMetricHelper("test");
  private static final EncryptionKey ENCRYPTION_KEY = EncryptionKey.newBuilder()
      .setKeyId("keyId")
      .setKeyType("keyType")
      .build();
  private static final ServiceException SERVICE_EXCEPTION =
      new ServiceException(Code.NOT_FOUND, "errorReason", "msg");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeyDb mockKeyDb;

  @Test
  public void readDbFails_cacheEnabledTest() throws Exception {
    when(mockKeyDb.getKey(anyString())).thenThrow(SERVICE_EXCEPTION);
    readDbFailsTest(true);
  }

  @Test
  public void readDbFails_cacheDisabledTest() throws Exception {
    when(mockKeyDb.getKey(anyString())).thenThrow(SERVICE_EXCEPTION);
    readDbFailsTest(false);
  }

  private void readDbFailsTest(boolean enabled) throws Exception {
    var cache = new GetEncryptedKeyCache(mockKeyDb, enabled, LOG_METRIC_HELPER);
    assertThrows(ServiceException.class, () -> cache.getKey(KEY));
    assertThrows(ServiceException.class, () -> cache.getKey(KEY));
    assertThrows(ServiceException.class, () -> cache.getKey(KEY));
    verify(mockKeyDb, times(enabled ? 1 : 3)).getKey(anyString());
  }

  @Test
  public void readDbSucceeds_cacheEnabledTest() throws Exception {
    when(mockKeyDb.getKey(anyString())).thenReturn(ENCRYPTION_KEY);
    readDbSucceedsTest(true);
  }

  @Test
  public void readDbSucceeds_cacheDisabledTest() throws Exception {
    when(mockKeyDb.getKey(anyString())).thenReturn(ENCRYPTION_KEY);
    readDbSucceedsTest(false);
  }

  public void readDbSucceedsTest(boolean enabled) throws Exception {
    var cache = new GetEncryptedKeyCache(mockKeyDb, enabled, LOG_METRIC_HELPER);
    assertThat(cache.getKey(KEY)).isEqualTo(ENCRYPTION_KEY);
    assertThat(cache.getKey(KEY)).isEqualTo(ENCRYPTION_KEY);
    assertThat(cache.getKey(KEY)).isEqualTo(ENCRYPTION_KEY);
    verify(mockKeyDb, times(enabled ? 1 : 3)).getKey(anyString());
  }
}
