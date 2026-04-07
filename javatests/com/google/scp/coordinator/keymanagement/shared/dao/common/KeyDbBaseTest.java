/*
 * Copyright 2023 Google LLC
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
package com.google.scp.coordinator.keymanagement.shared.dao.common;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey.withActivationAndExpirationTimes;
import static com.google.scp.shared.api.model.Code.ALREADY_EXISTS;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.time.Instant;
import javax.inject.Inject;
import org.junit.Test;

public abstract class KeyDbBaseTest {
  private static final String SET_NAME = "test-set-name";

  @Inject protected KeyDb db;

  @Test
  public void getActiveKeys_specificMoments_returnsExpected() throws Exception {
    // Given
    String setName = "test-set-name";
    Instant t0 = Instant.now();
    Instant t1 = t0.plusSeconds(1);
    Instant t2 = t0.plusSeconds(2);
    Instant t3 = t0.plusSeconds(3);
    Instant t4 = t0.plusSeconds(4);

    db.createKey(fakeEncryptionKey(setName, t1, t3));
    db.createKey(fakeEncryptionKey(setName, t1, t3));

    db.createKey(fakeEncryptionKey(setName, t2, t3));
    db.createKey(fakeEncryptionKey(setName, t2, t3));

    db.createKey(fakeEncryptionKey(setName, t2, t4));
    db.createKey(fakeEncryptionKey(setName, t2, t4));

    db.createKey(fakeEncryptionKey(setName, t3, t4));
    db.createKey(fakeEncryptionKey(setName, t3, t4));

    // When/Then
    assertThat(db.getActiveKeys(setName, 20, t0)).hasSize(0);
    assertThat(db.getActiveKeys(setName, 20, t1)).hasSize(2);
    assertThat(db.getActiveKeys(setName, 20, t2)).hasSize(6);
    assertThat(db.getActiveKeys(setName, 20, t3)).hasSize(4);
    assertThat(db.getActiveKeys(setName, 20, t4)).hasSize(0);
  }

  @Test
  public void getActiveKeys_specificSetName_returnsExpected() throws Exception {
    // Given
    String setName2 = "test-set-name-2";

    db.createKey(fakeEncryptionKey(SET_NAME));
    db.createKey(fakeEncryptionKey(SET_NAME));
    db.createKey(fakeEncryptionKey(setName2));
    db.createKey(fakeEncryptionKey(SET_NAME));
    db.createKey(fakeEncryptionKey(setName2));

    // When/Then
    assertThat(db.getActiveKeys(SET_NAME, 20)).hasSize(3);
    assertThat(db.getActiveKeys(setName2, 20)).hasSize(2);
  }

  @Test
  public void getActiveKeysWithPublicKey_doesNotReturnNonAsymmetricKeys() throws Exception {
    // Given
    EncryptionKey asymmetricKey = FakeEncryptionKey.createEncryptionKey(SET_NAME);
    EncryptionKey asymmetricKey2 = FakeEncryptionKey.createEncryptionKey(SET_NAME);
    EncryptionKey nonAsymmetricKey1 =
        FakeEncryptionKey.createEncryptionKey(SET_NAME).toBuilder()
            .clearPublicKey()
            .clearPublicKeyMaterial()
            .build();
    EncryptionKey nonAsymmetricKey2 =
        FakeEncryptionKey.createEncryptionKey(SET_NAME).toBuilder()
            .clearPublicKey()
            .clearPublicKeyMaterial()
            .build();
    db.createKeys(
        ImmutableList.of(asymmetricKey, asymmetricKey2, nonAsymmetricKey1, nonAsymmetricKey2));

    // When
    ImmutableList<EncryptionKey> keys = db.getActiveKeysWithPublicKey(SET_NAME, 100);

    // Then
    ImmutableList<String> ids =
        keys.stream().map(EncryptionKey::getKeyId).collect(toImmutableList());
    assertThat(ids).containsAtLeast(asymmetricKey.getKeyId(), asymmetricKey2.getKeyId());
    assertThat(ids).containsNoneOf(nonAsymmetricKey1.getKeyId(), nonAsymmetricKey2.getKeyId());
  }

  @Test
  public void createKey_overwriteSuccess() throws Exception {
    String keyId = "asdf";
    EncryptionKey key1 =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setJsonEncodedKeyset("12345")
            .setCreationTime(0L)
            .setExpirationTime(0L)
            .build();
    EncryptionKey key2 =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setJsonEncodedKeyset("67890")
            .setCreationTime(0L)
            .setExpirationTime(0L)
            .build();

    db.createKey(key1);
    db.createKey(key2);
    EncryptionKey result = db.getKey(keyId);

    assertThat(result.getExpirationTime()).isEqualTo(key2.getExpirationTime());
    assertThat(result.getKeyId()).isEqualTo(key2.getKeyId());
    assertThat(result.getJsonEncodedKeyset()).isEqualTo(key2.getJsonEncodedKeyset());
  }

  @Test
  public void createKey_overwriteFailWithException() throws ServiceException {
    InMemoryKeyDb keyDb = new InMemoryKeyDb();
    String keyId = "asdf";
    EncryptionKey key1 =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setJsonEncodedKeyset("12345")
            .setCreationTime(0L)
            .setExpirationTime(0L)
            .build();
    EncryptionKey key2 =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setJsonEncodedKeyset("67890")
            .setCreationTime(0L)
            .setExpirationTime(0L)
            .build();

    keyDb.createKey(key1);
    ServiceException e = assertThrows(ServiceException.class, () -> keyDb.createKey(key2, false));
    assertThat(e.getErrorCode()).isEqualTo(ALREADY_EXISTS);
  }

  protected EncryptionKey clearCreationTime(EncryptionKey key) {
    return key.toBuilder().setCreationTime(0L).build();
  }

  private static EncryptionKey fakeEncryptionKey(String setName) {
    return FakeEncryptionKey.createEncryptionKey(setName);
  }

  private static EncryptionKey fakeEncryptionKey(
      String setName, Instant activationTime, Instant expirationTime) {
    return withActivationAndExpirationTimes(setName, activationTime, expirationTime);
  }
}
