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
import static com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb.DEFAULT_SET_NAME;
import static com.google.scp.shared.util.KeyParams.DEFAULT_TINK_TEMPLATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.acai.Acai;
import com.google.acai.TestScoped;
import com.google.crypto.tink.KmsClient;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.PopulateMigrationKeyData;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient.KeyStorageServiceException;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.MigrationKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.CreateSplitKeyTaskBase;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.SplitKeyGenerationTestEnv;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid.KeyIdFactory;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid.SequenceKeyIdFactory;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.KmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationPeerCoordinatorKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationPeerKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerCoordinatorKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerKmsAeadClient;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.keymanagement.testutils.FakeKmsClient;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.security.GeneralSecurityException;
import java.time.Instant;
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
    int expectedBackfillDays = 5;

    task.createSplitKey(
        DEFAULT_SET_NAME,
        DEFAULT_TINK_TEMPLATE,
        keysToCreate,
        expectedExpiryInDays,
        expectedTtlInDays,
        expectedBackfillDays,
        Instant.now());

    task.createSplitKey(
        DEFAULT_SET_NAME,
        DEFAULT_TINK_TEMPLATE,
        keysToCreate,
        expectedExpiryInDays,
        expectedTtlInDays,
        expectedBackfillDays,
        Instant.now());

    List<String> keys = sortKeysById();

    for (int i = 0; i < 2 * keysToCreate; i++) {
      String keyId = keyIdFactory.encodeKeyIdToString((long) i);
      assertThat(keys.get(i)).isEqualTo(keyId);
    }
  }

  @Test
  public void createSplitKey_successWithOverflow() throws Exception {
    int keysToCreate = 100;

    for (int i = 75; i > 50; i--) {
      insertKeyWithKeyId(keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE - i));
    }
    List<String> keys = sortKeysById();
    // Make sure keys are created correctly
    for (int i = 0; i < 25; i++) {
      String keyId = keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE - 75 + i);
      assertThat(keys.get(i)).isEqualTo(keyId);
    }
    int expectedExpiryInDays = 10;
    int expectedTtlInDays = 20;
    int expectedBackfillDays = 5;

    task.createSplitKey(
        DEFAULT_SET_NAME,
        DEFAULT_TINK_TEMPLATE,
        keysToCreate,
        expectedExpiryInDays,
        expectedTtlInDays,
        expectedBackfillDays,
        Instant.now());

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

    insertKeyWithKeyId(keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE));
    List<String> keys = sortKeysById();
    // Make sure keys are created correctly
    assertThat(keys.getFirst()).isEqualTo(keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE));
    int expectedExpiryInDays = 10;
    int expectedTtlInDays = 20;
    int expectedBackfillDays = 5;

    task.createSplitKey(
        DEFAULT_SET_NAME,
        DEFAULT_TINK_TEMPLATE,
        keysToCreate,
        expectedExpiryInDays,
        expectedTtlInDays,
        expectedBackfillDays,
        Instant.now());

    keys = sortKeysById();
    for (int i = 0; i < keysToCreate; i++) {
      String keyId = keyIdFactory.encodeKeyIdToString(Long.MIN_VALUE + i);
      assertThat(keys.get(i)).isEqualTo(keyId);
    }
  }

  @Test
  public void createSplitKey_successAfterOverflow() throws Exception {
    int keysToCreate = 100;

    insertKeyWithKeyId(keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE));
    insertKeyWithKeyId(keyIdFactory.encodeKeyIdToString(Long.MIN_VALUE));
    List<String> keys = sortKeysById();
    // Make sure keys are created correctly
    assertThat(keys.get(0)).isEqualTo(keyIdFactory.encodeKeyIdToString(Long.MIN_VALUE));
    assertThat(keys.get(1)).isEqualTo(keyIdFactory.encodeKeyIdToString(Long.MAX_VALUE));
    int expectedExpiryInDays = 10;
    int expectedTtlInDays = 20;
    int expectedBackfillDays = 5;

    task.createSplitKey(
        DEFAULT_SET_NAME,
        DEFAULT_TINK_TEMPLATE,
        keysToCreate,
        expectedExpiryInDays,
        expectedTtlInDays,
        expectedBackfillDays,
        Instant.now());

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
    int expectedBackfillDays = 5;
    when(keyStorageClient.createKey(any(), any(), any()))
        .thenCallRealMethod()
        .thenThrow(new KeyStorageServiceException("Failure", new GeneralSecurityException()))
        .thenCallRealMethod();
    try {
      task.createSplitKey(
          DEFAULT_SET_NAME,
          DEFAULT_TINK_TEMPLATE,
          keysToCreate,
          expectedExpiryInDays,
          expectedTtlInDays,
          expectedBackfillDays,
          Instant.now());
    } catch (ServiceException e) {
      List<String> keys = sortKeysById();
      assertThat(keys.size()).isEqualTo(2);
      for (int i = 0; i < keys.size(); i++) {
        String keyId = keyIdFactory.encodeKeyIdToString((long) i);
        assertThat(keys.get(i)).isEqualTo(keyId);
      }
      Map<String, EncryptionKey> key =
          keyDb.getAllKeys().stream().collect(Collectors.toMap(EncryptionKey::getKeyId, a -> a));
      // Make sure the placeholder key is invalid
      assertThat(key.get(keyIdFactory.encodeKeyIdToString(1L)).getActivationTime())
          .isEqualTo(key.get(keyIdFactory.encodeKeyIdToString(1L)).getExpirationTime());
    }
  }

  private List<String> sortKeysById() throws Exception {
    return keyDb.getAllKeys().stream()
        .map(EncryptionKey::getKeyId)
        .sorted(Comparator.comparing(keyIdFactory::decodeKeyIdFromString))
        .collect(Collectors.toList());
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
    @MigrationKmsAeadClient
    public KmsClient provideMigrationKmsAeadClient() {
      return spy(new FakeKmsClient());
    }

    @Provides
    @TestScoped
    @PeerKmsAeadClient
    public KmsClient providePeerKmsAeadClient() {
      return spy(new FakeKmsClient());
    }

    @Provides
    @TestScoped
    @MigrationPeerKmsAeadClient
    public KmsClient provideMigrationPeerKmsAeadClient() {
      return spy(new FakeKmsClient());
    }

    @Override
    public void configure() {
      install(new SplitKeyGenerationTestEnv());
      bind(CreateSplitKeyTaskBase.class).to(GcpCreateSplitKeyTask.class);
      bind(String.class)
          .annotatedWith(PeerCoordinatorKeyEncryptionKeyBaseUri.class)
          .toInstance("fake-kms://$setName$-fake-id-b");
      bind(String.class)
          .annotatedWith(MigrationPeerCoordinatorKeyEncryptionKeyBaseUri.class)
          .toInstance("");
      bind(Boolean.class).annotatedWith(PopulateMigrationKeyData.class).toInstance(false);
      bind(String.class)
          .annotatedWith(KeyEncryptionKeyBaseUri.class)
          .toInstance("fake-kms://$setName$-fake-id-a");
      bind(String.class).annotatedWith(MigrationKeyEncryptionKeyBaseUri.class).toInstance("");
      bind(KeyIdFactory.class).toInstance(new SequenceKeyIdFactory());
    }
  }
}
