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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.acai.Acai;
import com.google.acai.TestScoped;
import com.google.crypto.tink.KmsClient;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.DisableKeySetAcl;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient.KeyStorageServiceException;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.CreateSplitKeyTaskBase;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.SplitKeyGenerationTestEnv;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid.KeyIdFactory;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid.SequenceKeyIdFactory;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.KmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerCoordinatorKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerKmsAeadClient;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.keymanagement.testutils.FakeKmsClient;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

/** Test the full CreateSplitKeyTask using a faked KeyStorageClient and spied-on dependencies. */
public final class GcpSeqIdCreateSplitKeyTaskTest extends GcpCreateSplitKeyTaskTestBase {

  @Rule public final Acai acai = new Acai(TestEnv.class);

  @Inject private SequenceKeyIdFactory keyIdFactory;

  @Test
  public void createSplitKey_successWithExistingKeys() throws Exception {
    int keysToCreate = 100;
    int expectedExpiryInDays = 10;
    int expectedTtlInDays = 20;

    task.createSplitKey(keysToCreate, expectedExpiryInDays, expectedTtlInDays, Instant.now());

    task.createSplitKey(keysToCreate, expectedExpiryInDays, expectedTtlInDays, Instant.now());

    List<String> keys = sortKeysById();

    for (int i = 0; i < 2 * keysToCreate; i++) {
      String keyId = keyIdFactory.encodeKeyIdToString((long) i);
      assertThat(keys.get(i)).isEqualTo(keyId);
    }
  }

  @Test
  public void createSplitKey_successWithOverflow() throws Exception {
    int keysToCreate = 100;

    SequenceKeyIdFactory sequenceKeyIdFactory = (SequenceKeyIdFactory) keyIdFactory;
    for (int i = 75; i > 50; i--) {
      insertKeyWithKeyId(sequenceKeyIdFactory.encodeKeyIdToString(Long.MAX_VALUE - i));
    }
    List<String> keys = sortKeysById();
    // Make sure keys are created correctly
    for (int i = 0; i < 25; i++) {
      String keyId = keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE - 75 + i);
      assertThat(keys.get(i)).isEqualTo(keyId);
    }
    int expectedExpiryInDays = 10;
    int expectedTtlInDays = 20;

    task.createSplitKey(keysToCreate, expectedExpiryInDays, expectedTtlInDays, Instant.now());

    keys = sortKeysById();
    // First half after overflow
    for (int i = 0; i < keysToCreate - 51; i++) {
      String keyId = keyIdFactory.encodeKeyIdToString(Long.MIN_VALUE + i);
      assertThat(keys.get(i)).isEqualTo(keyId);
    }

    // Second half before overflow
    for (int i = keysToCreate - 50; i < keys.size(); i++) {
      String keyId = keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE - 124 + i);
      assertThat(keys.get(i)).isEqualTo(keyId);
    }
  }

  @Test
  public void createSplitKey_successAtOverflow() throws Exception {
    int keysToCreate = 100;

    SequenceKeyIdFactory sequenceKeyIdFactory = (SequenceKeyIdFactory) keyIdFactory;
    insertKeyWithKeyId(sequenceKeyIdFactory.encodeKeyIdToString(Long.MAX_VALUE));
    List<String> keys = sortKeysById();
    // Make sure keys are created correctly
    assertThat(keys.get(0)).isEqualTo(keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE));
    int expectedExpiryInDays = 10;
    int expectedTtlInDays = 20;

    task.createSplitKey(keysToCreate, expectedExpiryInDays, expectedTtlInDays, Instant.now());

    keys = sortKeysById();
    for (int i = 0; i < keysToCreate; i++) {
      String keyId = keyIdFactory.encodeKeyIdToString(Long.MIN_VALUE + i);
      assertThat(keys.get(i)).isEqualTo(keyId);
    }
  }

  @Test
  public void createSplitKey_successAfterOverflow() throws Exception {
    int keysToCreate = 100;

    SequenceKeyIdFactory sequenceKeyIdFactory = (SequenceKeyIdFactory) keyIdFactory;
    insertKeyWithKeyId(sequenceKeyIdFactory.encodeKeyIdToString(Long.MAX_VALUE));
    insertKeyWithKeyId(sequenceKeyIdFactory.encodeKeyIdToString(Long.MIN_VALUE));
    List<String> keys = sortKeysById();
    // Make sure keys are created correctly
    assertThat(keys.get(0)).isEqualTo(keyIdFactory.encodeKeyIdToString(Long.MIN_VALUE));
    assertThat(keys.get(1)).isEqualTo(keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE));
    int expectedExpiryInDays = 10;
    int expectedTtlInDays = 20;

    task.createSplitKey(keysToCreate, expectedExpiryInDays, expectedTtlInDays, Instant.now());

    keys = sortKeysById();
    for (int i = 0; i < keysToCreate + 1; i++) {
      String keyId = keyIdFactory.encodeKeyIdToString(Long.MIN_VALUE + i);
      assertThat(keys.get(i)).isEqualTo(keyId);
    }
  }

  @Test
  public void createSplitKey_coordinatorBFailure() throws Exception {
    int keysToCreate = 3;
    int expectedExpiryInDays = 10;
    int expectedTtlInDays = 20;
    SequenceKeyIdFactory sequenceKeyIdFactory = (SequenceKeyIdFactory) keyIdFactory;
    when(keyStorageClient.createKey(any(), any()))
        .thenCallRealMethod()
        .thenThrow(new KeyStorageServiceException("Failure", new GeneralSecurityException()))
        .thenCallRealMethod();
    try {
      task.createSplitKey(keysToCreate, expectedExpiryInDays, expectedTtlInDays, Instant.now());
    } catch (ServiceException e) {
      List<String> keys = sortKeysById();
      assertThat(keys.size()).isEqualTo(2);
      for (int i = 0; i < keys.size(); i++) {
        String keyId = keyIdFactory.encodeKeyIdToString((long) i);
        assertThat(keys.get(i)).isEqualTo(keyId);
      }
      Map<String, EncryptionKey> key =
          keyDb.getAllKeys().stream().collect(Collectors.toMap(a -> a.getKeyId(), a -> a));
      // Make sure the placeholder key is invalid
      assertThat(key.get(sequenceKeyIdFactory.encodeKeyIdToString(1L)).getActivationTime())
          .isEqualTo(key.get(sequenceKeyIdFactory.encodeKeyIdToString(1L)).getExpirationTime());
    }
  }

  private List<String> sortKeysById() throws Exception {
    SequenceKeyIdFactory sequenceKeyIdFactory = keyIdFactory;

    List<String> keys =
        keyDb.getAllKeys().stream().map(a -> a.getKeyId()).collect(Collectors.toList());
    Collections.sort(
        keys,
        new Comparator<String>() {
          @Override
          public int compare(String o1, String o2) {
            return sequenceKeyIdFactory
                .decodeKeyIdFromString(o1)
                .compareTo(sequenceKeyIdFactory.decodeKeyIdFromString(o2));
          }
        });
    return keys;
  }

  protected void insertKeyWithExpiration(Instant expirationTime) throws ServiceException {
    keyDb.createKey(
        FakeEncryptionKey.createBuilderWithDefaults()
            .setKeyId(keyIdFactory.getNextKeyId(keyDb))
            .setExpirationTime(expirationTime.toEpochMilli())
            .build());
  }

  private void insertKeyWithKeyId(String keyId) throws ServiceException {
    keyDb.createKey(FakeEncryptionKey.withKeyId(keyId));
  }

  private static class TestEnv extends AbstractModule {
    @Provides
    @TestScoped
    @KmsAeadClient
    public KmsClient provideKmsAeadClient() {
      return spy(new FakeKmsClient());
    }

    @Provides
    @TestScoped
    @PeerKmsAeadClient
    public KmsClient providePeerKmsAeadClient() {
      return spy(new FakeKmsClient());
    }

    @Override
    public void configure() {
      install(new SplitKeyGenerationTestEnv());
      bind(CreateSplitKeyTaskBase.class).to(GcpCreateSplitKeyTask.class);
      bind(String.class)
          .annotatedWith(PeerCoordinatorKeyEncryptionKeyBaseUri.class)
          .toInstance("fake-kms://$setName$-fake-id-b");
      bind(Boolean.class).annotatedWith(DisableKeySetAcl.class).toInstance(false);
      bind(String.class)
          .annotatedWith(KeyEncryptionKeyBaseUri.class)
          .toInstance("fake-kms://$setName$-fake-id-a");
      bind(KeyIdFactory.class).toInstance(new SequenceKeyIdFactory());
    }
  }
}
