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

package com.google.scp.coordinator.keymanagement.keystorage.tasks.gcp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.SERVICE_ERROR;
import static com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey.createEncryptionKey;
import static com.google.scp.coordinator.keymanagement.testutils.InMemoryKeyDbTestUtil.KEY_LIMIT;
import static com.google.scp.shared.api.model.Code.INTERNAL;
import static org.junit.Assert.assertThrows;

import com.google.acai.Acai;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KmsClient;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.CreateKeyTask;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.keymanagement.testutils.FakeKmsClient;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GcpCreateKeyTaskTest {
  // TODO: b/483708292 - Cleanup/refactor tests
  private static final String SET_NAME = "test-set-name";
  private static final String MIGRATION_KEK_BASE_URI = "gcp-kms://$setName$-migration-kek-uri";
  private static final String KEK_BASE_URI = "gcp-kms://$setName$-kek-uri";
  private static final String TEST_PUBLIC_KEY = "test private key split";
  private static final String TEST_PRIVATE_KEY = "test public key";
  private static final String KEY_ID = "keyId";

  private static Aead TEST_AEAD;
  private static KmsClient kmsClient;
  private static KmsClient migrationKmsClient;
  private static String keyEncryptionKeyUri;
  private static String migrationKeyEncryptionKeyUri;

  @Rule public final Acai acai = new Acai(GcpKeyStorageTestEnv.class);
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Inject private InMemoryKeyDb keyDb;

  @BeforeClass
  public static void setUp() throws Exception {
    keyEncryptionKeyUri = KEK_BASE_URI.replace("$setName$", "");
    migrationKeyEncryptionKeyUri = MIGRATION_KEK_BASE_URI.replace("$setName$", "");
    kmsClient = new FakeKmsClient();
    migrationKmsClient = new FakeKmsClient();
    AeadConfig.register();
    TEST_AEAD = KeysetHandle.generateNew(KeyTemplates.get("AES128_GCM")).getPrimitive(Aead.class);
  }

  @Test
  public void createKey_success() throws Exception {
    String keyEncryptionKeyUri = KEK_BASE_URI.replace("$setName$", SET_NAME);
    String publicKey = "123456";
    String privateKeySplit = "privateKeySplit";
    var encryptedPrivateKeySplit =
        toBase64AndEncodeWithAead(
            privateKeySplit, kmsClient.getAead(keyEncryptionKeyUri), publicKey);
    CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb,
            kmsClient,
            KEK_BASE_URI,
            migrationKmsClient,
            migrationKeyEncryptionKeyUri,
            false);
    var creationTime = Instant.now().toEpochMilli();
    var expirationTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
    EncryptionKey key =
        FakeEncryptionKey.createEncryptionKeyBuilder(SET_NAME)
            .setKeyId(KEY_ID)
            .setExpirationTime(expirationTime)
            .setCreationTime(creationTime)
            .setKeyEncryptionKeyUri(keyEncryptionKeyUri)
            .setPublicKeyMaterial(toBase64(publicKey))
            .build();
    taskWithMock.createKey(key, encryptedPrivateKeySplit, "");

    var createdKey = keyDb.getActiveKeys(SET_NAME, KEY_LIMIT).getFirst();
    assertThat(createdKey.getPublicKey()).isEqualTo(key.getPublicKey());
    assertThat(createdKey.getCreationTime()).isEqualTo(creationTime);
    assertThat(createdKey.getExpirationTime()).isEqualTo(expirationTime);

    byte[] decodedKeys =
        decodeAndDecryptWithAead(createdKey.getJsonEncodedKeyset(), keyEncryptionKeyUri);
    assertThat(decodedKeys).isEqualTo(privateKeySplit.getBytes());
    assertThat(createdKey.getExpirationTime()).isEqualTo(expirationTime);
    assertThat(createdKey.getCreationTime()).isEqualTo(creationTime);
    assertThat(createdKey.getKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    assertThat(createdKey.getKeySplitDataList().get(0).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(key.getKeySplitDataList().getFirst().getKeySplitKeyEncryptionKeyUri());
    // Test data has 2 keysplitdata items, so the new one would be after
    assertThat(createdKey.getKeySplitDataList().get(2).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(keyEncryptionKeyUri);
  }

  @Test
  public void createKey_successOverwrite() throws ServiceException, GeneralSecurityException {
    String keyEncryptionKeyUri = KEK_BASE_URI.replace("$setName$", "");

    CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb,
            kmsClient,
            KEK_BASE_URI,
            migrationKmsClient,
            migrationKeyEncryptionKeyUri,
            false);
    var split1 = toBase64AndEncodeWithAead("12345", kmsClient.getAead(keyEncryptionKeyUri), "123");
    var split2 = toBase64AndEncodeWithAead("67890", kmsClient.getAead(keyEncryptionKeyUri), "123");
    EncryptionKey key1 =
        EncryptionKey.newBuilder()
            .setKeyId(KEY_ID)
            .setPublicKey("")
            .setExpirationTime(10L)
            .setCreationTime(0L)
            .setPublicKeyMaterial(toBase64("123"))
            .build();
    EncryptionKey key2 =
        EncryptionKey.newBuilder()
            .setKeyId(KEY_ID)
            .setPublicKey("")
            .setExpirationTime(5L)
            .setCreationTime(1L)
            .setPublicKeyMaterial(toBase64("123"))
            .build();

    taskWithMock.createKey(key1, split1, "");
    taskWithMock.createKey(key2, split2, "");

    var keyById = keyDb.getKey(KEY_ID);
    assertThat(keyById.getPublicKey()).isEqualTo(key2.getPublicKey());
    assertThat(decodeAndDecryptWithAead(keyById.getJsonEncodedKeyset(), keyEncryptionKeyUri))
        .isEqualTo("67890".getBytes());
    assertThat(keyById.getCreationTime()).isEqualTo(key2.getCreationTime());
    assertThat(keyById.getExpirationTime()).isEqualTo(key2.getExpirationTime());
  }

  @Test
  public void createKey_with_migration_success() throws ServiceException, GeneralSecurityException {
    this.createKey_success_with_migration_base("");
  }

  @Test
  public void createKey_with_migration_and_set_name_success()
      throws ServiceException, GeneralSecurityException {
    this.createKey_success_with_migration_base("testSet");
  }

  public void createKey_success_with_migration_base(String setName)
      throws ServiceException, GeneralSecurityException {
    String kmsKeyEncryptionKeyBaseUri = "gcp-kms://$setName$-kek-uri";
    String keyEncryptionKeyUri = kmsKeyEncryptionKeyBaseUri.replace("$setName$", setName);
    String migrationKmsKeyEncryptionKeyBaseUri = "gcp-kms://$setName$-migration-kek-uri";
    String migrationKeyEncryptionKeyUri =
        migrationKmsKeyEncryptionKeyBaseUri.replace("$setName$", setName);
    String publicKey = "123456";
    String privateKeySplit = "privateKeySplit";
    var encryptedPrivateKeySplit =
        toBase64AndEncodeWithAead(
            privateKeySplit, kmsClient.getAead(keyEncryptionKeyUri), publicKey);
    var migrationEncryptedPrivateKeySplit =
        toBase64AndEncodeWithAead(
            privateKeySplit, migrationKmsClient.getAead(migrationKeyEncryptionKeyUri), publicKey);
    CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb,
            kmsClient,
            kmsKeyEncryptionKeyBaseUri,
            migrationKmsClient,
            migrationKmsKeyEncryptionKeyBaseUri,
            true);
    var creationTime = Instant.now().toEpochMilli();
    var expirationTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
    EncryptionKey key =
        FakeEncryptionKey.createEncryptionKeyWithMigration(setName).toBuilder()
            .setKeyId(KEY_ID)
            .setExpirationTime(expirationTime)
            .setCreationTime(creationTime)
            .setKeyEncryptionKeyUri(keyEncryptionKeyUri)
            .setMigrationKeyEncryptionKeyUri(migrationKeyEncryptionKeyUri)
            .setPublicKeyMaterial(toBase64(publicKey))
            .build();
    taskWithMock.createKey(key, encryptedPrivateKeySplit, migrationEncryptedPrivateKeySplit);

    var createdKey = keyDb.getKey(KEY_ID);
    assertThat(createdKey.getPublicKey()).isEqualTo(key.getPublicKey());
    assertThat(createdKey.getCreationTime()).isEqualTo(creationTime);
    assertThat(createdKey.getExpirationTime()).isEqualTo(expirationTime);

    byte[] decodedKeys =
        decodeAndDecryptWithAead(createdKey.getJsonEncodedKeyset(), keyEncryptionKeyUri);
    assertThat(decodedKeys).isEqualTo(privateKeySplit.getBytes());
    byte[] migrationDecodedKeys =
        decodeAndDecryptWithAead(
            createdKey.getMigrationJsonEncodedKeyset(), migrationKeyEncryptionKeyUri);
    assertThat(migrationDecodedKeys).isEqualTo(privateKeySplit.getBytes());
    assertThat(createdKey.getExpirationTime()).isEqualTo(expirationTime);
    assertThat(createdKey.getCreationTime()).isEqualTo(creationTime);
    assertThat(createdKey.getKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    assertThat(createdKey.getMigrationKeyEncryptionKeyUri())
        .isEqualTo(migrationKeyEncryptionKeyUri);
    assertThat(createdKey.getKeySplitDataList().get(0).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(key.getKeySplitDataList().getFirst().getKeySplitKeyEncryptionKeyUri());
    assertThat(createdKey.getMigrationKeySplitDataList().get(0).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(key.getMigrationKeySplitDataList().getFirst().getKeySplitKeyEncryptionKeyUri());
    // Test data has 2 keysplitdata items, so the new one would be after
    assertThat(createdKey.getKeySplitDataList().get(2).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(keyEncryptionKeyUri);
    assertThat(createdKey.getMigrationKeySplitDataList().get(2).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(migrationKeyEncryptionKeyUri);
  }

  @Test
  public void createKey_missing_migration_success() throws Exception {
    this.createKey_success_with_missing_migration_data_base("");
  }

  @Test
  public void createKey_with_set_name_missing_migration_success() throws Exception {
    this.createKey_success_with_missing_migration_data_base("testSet");
  }

  public void createKey_success_with_missing_migration_data_base(String setName)
      throws ServiceException, GeneralSecurityException {
    String kmsKeyEncryptionKeyBaseUri = "gcp-kms://$setName$-kek-uri";
    String keyEncryptionKeyUri = kmsKeyEncryptionKeyBaseUri.replace("$setName$", setName);
    String migrationKmsKeyEncryptionKeyBaseUri = "gcp-kms://$setName$-migration-kek-uri";
    String migrationKeyEncryptionKeyUri =
        migrationKmsKeyEncryptionKeyBaseUri.replace("$setName$", setName);
    String publicKey = "123456";
    String privateKeySplit = "privateKeySplit";
    var encryptedPrivateKeySplit =
        toBase64AndEncodeWithAead(
            privateKeySplit, kmsClient.getAead(keyEncryptionKeyUri), publicKey);
    // Intentionally blank migration key split
    var migrationEncryptedPrivateKeySplit = "";
    CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb,
            kmsClient,
            kmsKeyEncryptionKeyBaseUri,
            migrationKmsClient,
            migrationKmsKeyEncryptionKeyBaseUri,
            true);
    var creationTime = Instant.now().toEpochMilli();
    var expirationTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();

    EncryptionKey key =
        FakeEncryptionKey.createEncryptionKeyWithMigration(setName).toBuilder()
            .setKeyId(KEY_ID)
            .setExpirationTime(expirationTime)
            .setCreationTime(creationTime)
            .setKeyEncryptionKeyUri(keyEncryptionKeyUri)
            .setMigrationKeyEncryptionKeyUri(migrationKeyEncryptionKeyUri)
            .setPublicKeyMaterial(toBase64(publicKey))
            .build();
    taskWithMock.createKey(key, encryptedPrivateKeySplit, migrationEncryptedPrivateKeySplit);

    var createdKey = keyDb.getKey(KEY_ID);
    assertThat(createdKey.getPublicKey()).isEqualTo(key.getPublicKey());
    assertThat(createdKey.getCreationTime()).isEqualTo(creationTime);
    assertThat(createdKey.getExpirationTime()).isEqualTo(expirationTime);

    byte[] decodedKeys =
        decodeAndDecryptWithAead(createdKey.getJsonEncodedKeyset(), keyEncryptionKeyUri);
    assertThat(decodedKeys).isEqualTo(privateKeySplit.getBytes());
    assertThat(createdKey.getExpirationTime()).isEqualTo(expirationTime);
    assertThat(createdKey.getCreationTime()).isEqualTo(creationTime);
    assertThat(createdKey.getKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    assertThat(createdKey.getKeySplitDataList().get(0).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(key.getKeySplitDataList().getFirst().getKeySplitKeyEncryptionKeyUri());
    // Test data has 2 keysplitdata items, so the new one would be after
    assertThat(createdKey.getKeySplitDataList().get(2).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(keyEncryptionKeyUri);

    // Migration data should be empty since it was missing from the input migration EncryptionKey
    assertThat(createdKey.getMigrationJsonEncodedKeyset()).isEqualTo("");
    assertThat(createdKey.getMigrationKeyEncryptionKeyUri()).isEqualTo("");
    assertThat(createdKey.getMigrationKeySplitDataList().size()).isEqualTo(0);
  }

  @Test
  public void createKey_databaseException() {
    CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb,
            kmsClient,
            KEK_BASE_URI,
            migrationKmsClient,
            migrationKeyEncryptionKeyUri,
            false);
    EncryptionKey key =
        EncryptionKey.newBuilder()
            .setKeyId(KEY_ID)
            .setPublicKey(toBase64(""))
            .setExpirationTime(0L)
            .build();
    keyDb.setServiceException(new ServiceException(INTERNAL, SERVICE_ERROR.name(), "error"));
    ServiceException ex =
        assertThrows(
            ServiceException.class,
            () ->
                taskWithMock.createKey(
                    key,
                    toBase64AndEncodeWithAead("123", kmsClient.getAead(keyEncryptionKeyUri), ""),
                    ""));

    assertThat(ex.getErrorCode()).isEqualTo(INTERNAL);
  }

  @Test
  public void createKey_validationException() throws ServiceException {
    com.google.scp.coordinator.keymanagement.keystorage.tasks.common.CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb,
            kmsClient,
            KEK_BASE_URI,
            migrationKmsClient,
            migrationKeyEncryptionKeyUri,
            false);
    EncryptionKey key =
        EncryptionKey.newBuilder()
            .setKeyId(KEY_ID)
            .setPublicKey("123")
            .setExpirationTime(0L)
            .build();

    ServiceException ex =
        assertThrows(
            ServiceException.class, () -> taskWithMock.createKey(key, toBase64("456"), ""));

    assertThat(ex).hasCauseThat().isInstanceOf(GeneralSecurityException.class);
    assertThat(ex.getErrorCode()).isEqualTo(Code.INVALID_ARGUMENT);
    assertThat(keyDb.getActiveKeys("", 5).size()).isEqualTo(0);
  }

  @Test
  public void createKey_missingPrivateKeyException() throws ServiceException {
    CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb,
            kmsClient,
            KEK_BASE_URI,
            migrationKmsClient,
            migrationKeyEncryptionKeyUri,
            false);
    EncryptionKey key =
        EncryptionKey.newBuilder()
            .setKeyId(KEY_ID)
            .setPublicKey("123")
            .setExpirationTime(0L)
            .build();

    ServiceException ex =
        assertThrows(ServiceException.class, () -> taskWithMock.createKey(key, "", ""));

    assertThat(ex.getErrorCode()).isEqualTo(Code.INVALID_ARGUMENT);
    assertThat(keyDb.getActiveKeys("", 5).size()).isEqualTo(0);
  }

  @Test
  public void createKey_happyInput_createsExpected() throws Exception {
    String keyEncryptionKeyUri = KEK_BASE_URI.replace("$setName$", SET_NAME);
    // Given
    var encryptedPrivateKeyWithAssociatedPublicKey =
        toBase64AndEncodeWithAead(
            TEST_PRIVATE_KEY, kmsClient.getAead(keyEncryptionKeyUri), TEST_PUBLIC_KEY);
    var key =
        createEncryptionKey(SET_NAME).toBuilder()
            .setPublicKeyMaterial(toBase64(TEST_PUBLIC_KEY))
            .build();

    // When
    var task =
        new GcpCreateKeyTask(
            keyDb,
            kmsClient,
            KEK_BASE_URI,
            migrationKmsClient,
            migrationKeyEncryptionKeyUri,
            false);
    task.createKey(key, encryptedPrivateKeyWithAssociatedPublicKey, "");

    // Then
    var storedKey = keyDb.getKey(key.getKeyId());
    var storedPrivateKey =
        decodeAndDecryptWithAead(storedKey.getJsonEncodedKeyset(), keyEncryptionKeyUri);
    assertThat(storedPrivateKey).isEqualTo(TEST_PRIVATE_KEY.getBytes());
  }

  @Test
  public void createKey_mismatchPublicKey_throwsExpectedError() throws Exception {
    // Given
    var encryptedPrivateKeyWithAssociatedPublicKey =
        Base64.getEncoder()
            .encodeToString(TEST_AEAD.encrypt(TEST_PUBLIC_KEY.getBytes(), "mismatch".getBytes()));
    var key =
        createEncryptionKey(SET_NAME).toBuilder()
            .setPublicKeyMaterial(Base64.getEncoder().encodeToString(TEST_PRIVATE_KEY.getBytes()))
            .build();

    // When
    var task =
        new GcpCreateKeyTask(
            keyDb,
            kmsClient,
            KEK_BASE_URI,
            migrationKmsClient,
            migrationKeyEncryptionKeyUri,
            false);
    ThrowingRunnable when =
        () -> task.createKey(key, encryptedPrivateKeyWithAssociatedPublicKey, "");

    // Then
    var exception = assertThrows(ServiceException.class, when);
    assertThat(exception).hasMessageThat().contains("Key-split validation failed");
  }

  /** Small helper function for generating valid base64 from a string literal. */
  private static String toBase64(String input) {
    return Base64.getEncoder().encodeToString(input.getBytes());
  }

  private static String toBase64AndEncodeWithAead(String input, Aead aead, String data)
      throws GeneralSecurityException {
    return Base64.getEncoder().encodeToString(aead.encrypt(input.getBytes(), data.getBytes()));
  }

  private static byte[] decodeAndDecryptWithAead(String input, String keyEncryptionKeyUri)
      throws GeneralSecurityException {
    return kmsClient
        .getAead(keyEncryptionKeyUri)
        .decrypt(Base64.getDecoder().decode(input), new byte[0]);
  }
}
