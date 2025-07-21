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

package com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.CreateSplitKeyTask.KEY_REFRESH_WINDOW;
import static com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb.DEFAULT_SET_NAME;
import static com.google.scp.shared.util.KeyParams.DEFAULT_TINK_TEMPLATE;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.collect.Streams;
import com.google.crypto.tink.KmsClient;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient.KeyStorageServiceException;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.testing.FakeKeyStorageClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KeyEncryptionKeyUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.CreateSplitKeyTaskBaseTest;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.KmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationPeerKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerCoordinatorKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerKmsAeadClient;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyStatusProto.EncryptionKeyStatus;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public abstract class GcpCreateSplitKeyTaskTestBase extends CreateSplitKeyTaskBaseTest {
  @Inject protected InMemoryKeyDb keyDb;
  @Inject @KeyEncryptionKeyUri protected String keyEncryptionKeyUri;
  @Inject protected FakeKeyStorageClient keyStorageClient;

  @Inject @PeerCoordinatorKeyEncryptionKeyBaseUri
  protected String peerCoordinatorKeyEncryptionKeyBaseUri;

  @Inject @KeyEncryptionKeyBaseUri protected String keyEncryptionKeyBaseUri;
  @Inject @KmsAeadClient KmsClient kmsClient;
  @Inject @PeerKmsAeadClient KmsClient peerKmsClient;
  @Inject @MigrationPeerKmsAeadClient KmsClient migrationPeerKmsClient;

  // TODO: Refactor common test code to shared class
  @Test
  public void createSplitKey_success() throws Exception {
    String setName = DEFAULT_SET_NAME;
    int keysToCreate = 1;
    int expectedExpiryInDays = 10;
    int expectedTtlInDays = 20;
    var encryptionKeyCaptor = ArgumentCaptor.forClass(EncryptionKey.class);
    var encryptedKeySplitCaptor = ArgumentCaptor.forClass(String.class);
    task.createSplitKey(
        DEFAULT_SET_NAME,
        DEFAULT_TINK_TEMPLATE,
        keysToCreate,
        expectedExpiryInDays,
        expectedTtlInDays,
        Instant.now());

    ImmutableList<EncryptionKey> keys = keyDb.getAllKeys();
    assertThat(keys).hasSize(keysToCreate);

    EncryptionKey key = keys.getFirst();
    String keyEncryptionKeyUri = keyEncryptionKeyBaseUri.replace("$setName$", setName);

    // Validate that the key split decrypts (currently no associated data)
    kmsClient
        .getAead(keyEncryptionKeyUri)
        .decrypt(Base64.getDecoder().decode(key.getJsonEncodedKeyset()), new byte[0]);

    // Misc metadata
    assertThat(key.getStatus()).isEqualTo(EncryptionKeyStatus.ACTIVE);
    assertThat(key.getKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    assertThat(key.getKeyType()).isEqualTo("MULTI_PARTY_HYBRID_EVEN_KEYSPLIT");

    // Properties about Key Split Data
    assertThat(key.getKeySplitDataList()).hasSize(2);
    ImmutableMap<String, KeySplitData> keySplitDataMap =
        key.getKeySplitDataList().stream()
            .collect(
                toImmutableMap(
                    KeySplitData::getKeySplitKeyEncryptionKeyUri,
                    keySplitDataItem -> keySplitDataItem));
    KeySplitData keySplitDataA = keySplitDataMap.get(keyEncryptionKeyUri);
    KeySplitData keySplitDataB = keySplitDataMap.get(FakeKeyStorageClient.KEK_URI);

    // Coordinator A's KeySplitData
    assertThat(keySplitDataA).isNotNull();
    assertThat(keySplitDataA.getKeySplitKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    // TODO: Verify signature
    // Coordinator B's KeySplitData
    assertThat(keySplitDataB).isNotNull();
    assertThat(keySplitDataB.getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(FakeKeyStorageClient.KEK_URI);
    // TODO: Verify signature

    // Must have a creationTime of now
    var now = Instant.now().toEpochMilli();
    assertThat(key.getCreationTime()).isIn(Range.closed(now - 1000, now));

    // Must have expected expiration time
    var dayInMilli =
        Instant.now()
            .plus(expectedExpiryInDays, ChronoUnit.DAYS)
            .plus(KEY_REFRESH_WINDOW)
            .toEpochMilli();
    assertThat(key.getExpirationTime()).isIn(Range.closed(dayInMilli - 1000, dayInMilli));

    // Must match expected ttl
    var ttlInSec = Instant.now().plus(expectedTtlInDays, ChronoUnit.DAYS).getEpochSecond();
    assertThat(key.getTtlTime()).isIn(Range.closed(ttlInSec - 2, ttlInSec));

    verify(keyStorageClient, times(keysToCreate))
        .createKey(
            encryptionKeyCaptor.capture(), encryptedKeySplitCaptor.capture(), eq(Optional.empty()));

    assertThat(encryptionKeyCaptor.getValue().getKeyId()).isEqualTo(key.getKeyId());

    // Validate that the key split decrypts with peer coordinator KEK
    peerKmsClient
        .getAead(peerCoordinatorKeyEncryptionKeyBaseUri.replace("$setName$", setName))
        .decrypt(
            Base64.getDecoder().decode(encryptedKeySplitCaptor.getValue()),
            Base64.getDecoder().decode(key.getPublicKeyMaterial()));
  }

  @Test
  public void createSplitKey_keyGenerationError()
      throws ServiceException, GeneralSecurityException {
    int keysToCreate = 5;
    String keyEncryptionKeyUri =
        keyEncryptionKeyBaseUri.replace("$setName$", KeyDb.DEFAULT_SET_NAME);
    Mockito.doThrow(new GeneralSecurityException()).when(kmsClient).getAead(keyEncryptionKeyUri);

    ServiceException ex =
        assertThrows(
            ServiceException.class,
            () ->
                task.createSplitKey(
                    DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, keysToCreate, 10, 20, Instant.now()));

    assertThat(ex).hasCauseThat().isInstanceOf(GeneralSecurityException.class);
    assertThat(ex.getErrorCode()).isEqualTo(Code.INTERNAL);
    ImmutableList<EncryptionKey> keys = keyDb.getAllKeys();
    assertThat(keys).isEmpty();
  }

  /** Ensure that even if we fail after two attempted generations, we store two keys */
  @Test
  public void createSplitKey_keyGenerationInterrupted()
      throws ServiceException, KeyStorageServiceException {
    int keysToCreate = 5;

    when(keyStorageClient.createKey(any(), any(), any()))
        .thenCallRealMethod()
        .thenCallRealMethod()
        .thenThrow(new KeyStorageServiceException("Failure", new GeneralSecurityException()));

    ServiceException ex =
        assertThrows(
            ServiceException.class,
            () ->
                task.createSplitKey(
                    DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, keysToCreate, 10, 20, Instant.now()));

    assertThat(ex).hasCauseThat().isInstanceOf(KeyStorageServiceException.class);
    ImmutableList<EncryptionKey> keys = keyDb.getAllKeys();
    assertThat(keys).hasSize(3);
  }

  @Test
  public void createSplitKey_createKeysWithoutExpirationAndTtl_success() throws Exception {
    String setName = KeyDb.DEFAULT_SET_NAME;
    int keysToCreate = 1;
    int expectedExpiryInDays = 0;
    int expectedTtlInDays = 0;
    var encryptionKeyCaptor = ArgumentCaptor.forClass(EncryptionKey.class);
    var encryptedKeySplitCaptor = ArgumentCaptor.forClass(String.class);

    task.createSplitKey(
        DEFAULT_SET_NAME,
        DEFAULT_TINK_TEMPLATE,
        keysToCreate,
        expectedExpiryInDays,
        expectedTtlInDays,
        Instant.now());

    ImmutableList<EncryptionKey> keys = keyDb.getAllKeys();
    assertThat(keys).hasSize(keysToCreate);

    EncryptionKey key = keys.getFirst();

    // Validate that the key split decrypts (currently no associated data)
    String keyEncryptionKeyUri = keyEncryptionKeyBaseUri.replace("$setName$", setName);
    kmsClient
        .getAead(keyEncryptionKeyUri)
        .decrypt(Base64.getDecoder().decode(key.getJsonEncodedKeyset()), new byte[0]);

    // Misc metadata
    assertThat(key.getStatus()).isEqualTo(EncryptionKeyStatus.ACTIVE);
    assertThat(key.getKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    assertThat(key.getKeyType()).isEqualTo("MULTI_PARTY_HYBRID_EVEN_KEYSPLIT");

    // Properties about Key Split Data
    assertThat(key.getKeySplitDataList()).hasSize(2);
    ImmutableMap<String, KeySplitData> keySplitDataMap =
        key.getKeySplitDataList().stream()
            .collect(
                toImmutableMap(
                    KeySplitData::getKeySplitKeyEncryptionKeyUri,
                    keySplitDataItem -> keySplitDataItem));
    KeySplitData keySplitDataA = keySplitDataMap.get(keyEncryptionKeyUri);
    KeySplitData keySplitDataB = keySplitDataMap.get(FakeKeyStorageClient.KEK_URI);

    // Coordinator A's KeySplitData
    assertThat(keySplitDataA).isNotNull();
    assertThat(keySplitDataA.getKeySplitKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    // TODO: Verify signature
    // Coordinator B's KeySplitData
    assertThat(keySplitDataB).isNotNull();
    assertThat(keySplitDataB.getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(FakeKeyStorageClient.KEK_URI);
    // TODO: Verify signature

    // Must have a creationTime of now
    var now = Instant.now().toEpochMilli();
    assertThat(key.getCreationTime()).isIn(Range.closed(now - 1000, now));

    // Must have null expiration time which is represented as 0
    assertThat(key.getExpirationTime()).isEqualTo(0);

    // Must have null expiration time which is represented as 0
    assertThat(key.getTtlTime()).isEqualTo(0);

    verify(keyStorageClient, times(keysToCreate))
        .createKey(
            encryptionKeyCaptor.capture(), encryptedKeySplitCaptor.capture(), eq(Optional.empty()));

    assertThat(encryptionKeyCaptor.getValue().getKeyId()).isEqualTo(key.getKeyId());

    // Validate that the key split decrypts with peer coordinator KEK
    peerKmsClient
        .getAead(peerCoordinatorKeyEncryptionKeyBaseUri.replace("$setName$", setName))
        .decrypt(
            Base64.getDecoder().decode(encryptedKeySplitCaptor.getValue()),
            Base64.getDecoder().decode(key.getPublicKeyMaterial()));

    // Verify that the keys will not expire
    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, 1, Instant.now()).size()).isEqualTo(1);
    assertThat(
            keyDb
                .getActiveKeys(DEFAULT_SET_NAME, 1, Instant.now().plus(Duration.ofHours(1)))
                .size())
        .isEqualTo(1);
    assertThat(
            keyDb.getActiveKeys(DEFAULT_SET_NAME, 1, Instant.now().plus(Duration.ofDays(1))).size())
        .isEqualTo(1);
    assertThat(
            keyDb
                .getActiveKeys(DEFAULT_SET_NAME, 1, Instant.now().plus(Duration.ofDays(365)))
                .size())
        .isEqualTo(1);
    assertThat(
            keyDb
                .getActiveKeys(DEFAULT_SET_NAME, 1, Instant.now().plus(Duration.ofDays(36500)))
                .size())
        .isEqualTo(1);
  }

  @Override
  protected ImmutableList<byte[]> capturePeerSplits() throws Exception {
    var encryptionKeyCaptor = ArgumentCaptor.forClass(EncryptionKey.class);
    var encryptedKeySplitCaptor = ArgumentCaptor.forClass(String.class);

    verify(keyStorageClient, atLeastOnce())
        .createKey(
            encryptionKeyCaptor.capture(), encryptedKeySplitCaptor.capture(), eq(Optional.empty()));

    return Streams.zip(
            encryptionKeyCaptor.getAllValues().stream(),
            encryptedKeySplitCaptor.getAllValues().stream(),
            (encryptionKey, encryptedKeySplit) -> {
              try {
                return peerKmsClient
                    .getAead(
                        peerCoordinatorKeyEncryptionKeyBaseUri.replace(
                            "$setName$", encryptionKeyCaptor.getValue().getSetName()))
                    .decrypt(
                        Base64.getDecoder().decode(encryptedKeySplit),
                        Base64.getDecoder().decode(encryptionKey.getPublicKeyMaterial()));
              } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(toImmutableList());
  }

  @Test
  public void create_differentTinkTemplates_successfullyReconstructExpectedPrimitives()
      throws Exception {
    super.create_differentTinkTemplates_successfullyReconstructExpectedPrimitives(
        kmsClient.getAead(keyEncryptionKeyBaseUri.replace("$setName$", "test-set")));
  }
}
