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

package com.google.scp.coordinator.keymanagement.keystorage.converters;

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.coordinator.keymanagement.keystorage.converters.EncryptionKeyConverter.toApiEncryptionKey;
import static com.google.scp.coordinator.keymanagement.keystorage.converters.EncryptionKeyConverter.toStorageEncryptionKey;
import static com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey.createEncryptionKeyBuilder;
import static com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey.createEncryptionKeyWithMigration;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyTypeProto.EncryptionKeyType;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EncryptionKeyConverterTest {
  private static final String SET_NAME = "test-set-name";

  @Test
  public void toStorageEncryptionKey_success() {
    String path = "keys/";
    String keyId = "myKey";
    EncryptionKey keyStorageKey =
        EncryptionKey.newBuilder()
            .setName(path + keyId)
            .setSetName(SET_NAME)
            .setEncryptionKeyType(EncryptionKeyType.SINGLE_PARTY_HYBRID_KEY)
            .setPublicKeysetHandle("12345")
            .setPublicKeyMaterial("qwert")
            .setCreationTime(0L)
            .setActivationTime(1L)
            .setExpirationTime(2L)
            .setKeyMetadata(
                EncryptionKey.KeyMetadata.newBuilder().setBackfillExpirationTime(3L).build())
            .addAllKeyData(ImmutableList.of())
            .build();

    var result = toStorageEncryptionKey(keyId, keyStorageKey);

    assertThat(path + result.getKeyId()).isEqualTo(keyStorageKey.getName());
    assertThat(result.getSetName()).isEqualTo(keyStorageKey.getSetName());
    assertThat(result.getPublicKey()).isEqualTo(keyStorageKey.getPublicKeysetHandle());
    assertThat(result.getActivationTime()).isEqualTo(keyStorageKey.getActivationTime());
    assertThat(result.getExpirationTime()).isEqualTo(keyStorageKey.getExpirationTime());
    assertThat(result.getKeyMetadata().getBackfillExpirationTime())
        .isEqualTo(keyStorageKey.getKeyMetadata().getBackfillExpirationTime());
  }

  @Test
  public void toStorageEncryptionKey_withNulls_success() {
    String path = "keys/";
    String keyId = "myKey";
    EncryptionKey keyStorageKey =
        EncryptionKey.newBuilder()
            .setName(path + keyId)
            .setSetName(SET_NAME)
            .setEncryptionKeyType(EncryptionKeyType.SINGLE_PARTY_HYBRID_KEY)
            .setPublicKeysetHandle("12345")
            .setPublicKeyMaterial("qwert")
            .setCreationTime(0L)
            .setActivationTime(1L)
            .addAllKeyData(ImmutableList.of())
            .addAllMigrationKeyData(ImmutableList.of())
            .build();

    var result = toStorageEncryptionKey(keyId, keyStorageKey);

    assertThat(path + result.getKeyId()).isEqualTo(keyStorageKey.getName());
    assertThat(result.getSetName()).isEqualTo(keyStorageKey.getSetName());
    assertThat(result.getPublicKey()).isEqualTo(keyStorageKey.getPublicKeysetHandle());
    assertThat(result.getActivationTime()).isEqualTo(keyStorageKey.getActivationTime());
    assertThat(result.hasTtlTime()).isFalse();
    assertThat(result.hasExpirationTime()).isFalse();
    assertThat(result.getKeySplitDataList().size()).isEqualTo(0);
    assertThat(result.getMigrationKeySplitDataList().size()).isEqualTo(0);
    assertThat(result.getMigrationKeyEncryptionKeyUri()).isEqualTo("");
  }

  @Test
  public void toStorageEncryptionKey_missingKeyId() {
    String path = "keys/";
    EncryptionKey keyStorageKey =
        EncryptionKey.newBuilder()
            .setName(path)
            .setEncryptionKeyType(EncryptionKeyType.SINGLE_PARTY_HYBRID_KEY)
            .setPublicKeysetHandle("12345")
            .setPublicKeyMaterial("qwert")
            .setCreationTime(0L)
            .setExpirationTime(0L)
            .addAllKeyData(ImmutableList.of())
            .addAllMigrationKeyData(ImmutableList.of())
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> toStorageEncryptionKey(null, keyStorageKey));

    assertThat(ex.getMessage()).isEqualTo("KeyId cannot be null or empty.");
  }

  @Test
  public void toApiEncryptionKey_noPrivateKeyMaterial() {
    EncryptionKey encryptionKey = toApiEncryptionKey(createEncryptionKeyWithMigration(SET_NAME));
    // {@link FakeEncryptionKey} should always give a non-empty list of {@code keyData} so that this
    // test is meaningful.  If this assertion fails, it means we should fix {@code
    // FakeEncryptionKey.create} to provide more representative test data.
    assertThat(encryptionKey.getKeyDataList()).isNotEmpty();
    boolean privateKeyMaterialEmpty =
        encryptionKey.getKeyDataList().stream()
            .allMatch(keyData -> keyData.getKeyMaterial().isEmpty());
    assertThat(encryptionKey.getMigrationKeyDataList()).isNotEmpty();
    boolean migrationKeyMaterialEmpty =
        encryptionKey.getMigrationKeyDataList().stream()
            .allMatch(keyData -> keyData.getKeyMaterial().isEmpty());
    assertThat(privateKeyMaterialEmpty).isTrue();
    assertThat(migrationKeyMaterialEmpty).isTrue();
  }

  @Test
  public void toApiEncryptionKey_correctName() {
    String keyId = "abc";
    String path = "encryptionKeys/abc";
    EncryptionKey encryptionKey =
        toApiEncryptionKey(createEncryptionKeyBuilder().setKeyId(keyId).build());
    assertThat(encryptionKey.getName()).isEqualTo(path);
  }

  @Test
  public void toApiEncryptionKey_roundTrip() {
    var original = createEncryptionKeyBuilder().setJsonEncodedKeyset("").build();
    var converted = toStorageEncryptionKey(original.getKeyId(), toApiEncryptionKey(original));
    // The top-level URI doesn't exist on the API model, so ignore it.
    var originalNoUri = original.toBuilder().clearKeyEncryptionKeyUri().build();
    assertThat(converted).isEqualTo(originalNoUri);
  }

  @Test
  public void toApiEncryptionKey_badKeyType() {
    var encryptionKey = createEncryptionKeyBuilder().setKeyType("HYPER_PARTY_KEYSPLIT").build();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> toApiEncryptionKey(encryptionKey));

    assertThat(ex.getMessage()).isEqualTo("Unrecognized KeyType: HYPER_PARTY_KEYSPLIT");
  }

  @Test
  public void toApiEncryptionKey_unspecifiedKeyType() {
    var storageKey = createEncryptionKeyBuilder().setKeyType("").build();

    EncryptionKey encryptionKey = toApiEncryptionKey(storageKey);

    assertThat(encryptionKey.getEncryptionKeyType())
        .isEqualTo(EncryptionKeyType.SINGLE_PARTY_HYBRID_KEY);
  }

  @Test
  public void toApiEncryptionKey_withNulls() {
    var encryptionKey = EncryptionKeyProto.EncryptionKey.newBuilder().build();

    EncryptionKey storageKey = toApiEncryptionKey(encryptionKey);

    assertThat(storageKey.hasExpirationTime()).isFalse();
    assertThat(storageKey.hasTtlTime()).isFalse();
  }
}
