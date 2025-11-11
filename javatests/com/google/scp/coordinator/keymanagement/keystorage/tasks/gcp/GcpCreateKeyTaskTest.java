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
import static com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb.DEFAULT_SET_NAME;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.SERVICE_ERROR;
import static com.google.scp.coordinator.keymanagement.testutils.DynamoKeyDbTestUtil.KEY_LIMIT;
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
  private static final String KEK_URI = "gcp-kms://kek-uri";
  private static final String MIGRATION_KEK_URI = "gcp-kms://migration-kek-uri";
  private static final String KEK_BASE_URI = "gcp-kms://$setName$-kek-uri";
  private static final String TEST_PUBLIC_KEY = "test private key split";
  private static final String TEST_PRIVATE_KEY = "test public key";
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
    keyEncryptionKeyUri = KEK_URI.replace("$setName$", "");
    migrationKeyEncryptionKeyUri = MIGRATION_KEK_URI.replace("$setName$", "");
    kmsClient = new FakeKmsClient();
    migrationKmsClient = new FakeKmsClient();
    AeadConfig.register();
    TEST_AEAD = KeysetHandle.generateNew(KeyTemplates.get("AES128_GCM")).getPrimitive(Aead.class);
  }

  @Test
  public void createKey_success() throws Exception {
    createKey_success_base(KEK_BASE_URI.replace("$setName$", ""), KEK_BASE_URI);
  }

  @Test
  public void createKey_disableKeySetAcl_success() throws Exception {
    createKey_success_base(KEK_URI, KEK_URI);
  }

  public void createKey_success_base(String keyEncryptionKeyUri, String baseUrl) throws Exception {
    String publicKey = "123456";
    String privateKeySplit = "privateKeySplit";
    var encryptedPrivateKeySplit =
        toBase64AndEncodeWithAead(
            privateKeySplit, kmsClient.getAead(keyEncryptionKeyUri), publicKey);
    CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb, kmsClient, baseUrl, migrationKmsClient, migrationKeyEncryptionKeyUri, false);
    String keyId = "asdf";
    var creationTime = Instant.now().toEpochMilli();
    var expirationTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
    EncryptionKey key =
        FakeEncryptionKey.create().toBuilder()
            .setKeyId(keyId)
            .setExpirationTime(expirationTime)
            .setCreationTime(creationTime)
            .setKeyEncryptionKeyUri(keyEncryptionKeyUri)
            .setPublicKeyMaterial(toBase64(publicKey))
            .build();
    taskWithMock.createKey(key, encryptedPrivateKeySplit, "");

    com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey
        resultPublic = keyDb.getActiveKeys(DEFAULT_SET_NAME, KEY_LIMIT).getFirst();
    com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey
        resultPrivate = keyDb.getKey(keyId);
    assertThat(resultPublic.getKeyId()).isEqualTo(keyId);
    assertThat(resultPublic.getPublicKey()).isEqualTo(key.getPublicKey());
    assertThat(resultPublic.getCreationTime()).isEqualTo(creationTime);
    assertThat(resultPublic.getExpirationTime()).isEqualTo(expirationTime);
    assertThat(resultPrivate.getKeyId()).isEqualTo(keyId);
    byte[] decodedKeys =
        decodeAndDecryptWithAead(resultPrivate.getJsonEncodedKeyset(), keyEncryptionKeyUri);
    assertThat(decodedKeys).isEqualTo(privateKeySplit.getBytes());
    assertThat(resultPrivate.getExpirationTime()).isEqualTo(expirationTime);
    assertThat(resultPrivate.getCreationTime()).isEqualTo(creationTime);
    assertThat(resultPrivate.getKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    assertThat(resultPrivate.getKeySplitDataList().get(0).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(key.getKeySplitDataList().getFirst().getKeySplitKeyEncryptionKeyUri());
    // Test data has 2 keysplitdata items, so the new one would be after
    assertThat(resultPrivate.getKeySplitDataList().get(2).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(keyEncryptionKeyUri);
  }

  @Test
  public void createKey_successOverwrite() throws ServiceException, GeneralSecurityException {
    this.createKey_successOverwrite_base(KEK_BASE_URI.replace("$setName$", ""), KEK_BASE_URI);
  }

  @Test
  public void createKey_successOverwrite_disableKeySetAcl()
      throws ServiceException, GeneralSecurityException {
    this.createKey_successOverwrite_base(KEK_URI, KEK_URI);
  }

  public void createKey_successOverwrite_base(String keyEncryptionKeyUri, String baseUrl)
      throws ServiceException, GeneralSecurityException {
    CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb, kmsClient, baseUrl, migrationKmsClient, migrationKeyEncryptionKeyUri, false);
    String keyId = "asdf";
    var creationTime = 500L;
    var split1 = toBase64AndEncodeWithAead("12345", kmsClient.getAead(keyEncryptionKeyUri), "123");
    var split2 = toBase64AndEncodeWithAead("67890", kmsClient.getAead(keyEncryptionKeyUri), "123");
    EncryptionKey key1 =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setPublicKey("")
            .setExpirationTime(0L)
            .setCreationTime(creationTime)
            .setPublicKeyMaterial(toBase64("123"))
            .build();
    EncryptionKey key2 =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setPublicKey("")
            .setExpirationTime(0L)
            .setCreationTime(creationTime)
            .setPublicKeyMaterial(toBase64("123"))
            .build();

    taskWithMock.createKey(key1, split1, "");
    taskWithMock.createKey(key2, split2, "");

    com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey
        result = keyDb.getKey(keyId);
    assertThat(result.getKeyId()).isEqualTo(keyId);
    assertThat(result.getPublicKey()).isEqualTo(key2.getPublicKey());
    assertThat(decodeAndDecryptWithAead(result.getJsonEncodedKeyset(), keyEncryptionKeyUri))
        .isEqualTo("67890".getBytes());
    assertThat(result.getCreationTime()).isEqualTo(creationTime);
    assertThat(result.getExpirationTime()).isEqualTo(key2.getExpirationTime());
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
    String keyId = "asdf";
    var creationTime = Instant.now().toEpochMilli();
    var expirationTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
    EncryptionKey key =
        FakeEncryptionKey.createWithMigration().toBuilder()
            .setKeyId(keyId)
            .setSetName(setName)
            .setExpirationTime(expirationTime)
            .setCreationTime(creationTime)
            .setKeyEncryptionKeyUri(keyEncryptionKeyUri)
            .setMigrationKeyEncryptionKeyUri(migrationKeyEncryptionKeyUri)
            .setPublicKeyMaterial(toBase64(publicKey))
            .build();
    taskWithMock.createKey(key, encryptedPrivateKeySplit, migrationEncryptedPrivateKeySplit);

    // keyDb.getActiveKeys always uses the empty string as the default key set.
    if (setName.isEmpty()) {
      com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto
              .EncryptionKey
          resultPublic = keyDb.getActiveKeys(DEFAULT_SET_NAME, KEY_LIMIT).getFirst();
      assertThat(resultPublic.getKeyId()).isEqualTo(keyId);
      assertThat(resultPublic.getPublicKey()).isEqualTo(key.getPublicKey());
      assertThat(resultPublic.getCreationTime()).isEqualTo(creationTime);
      assertThat(resultPublic.getExpirationTime()).isEqualTo(expirationTime);
    }

    com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey
        resultPrivate = keyDb.getKey(keyId);
    assertThat(resultPrivate.getKeyId()).isEqualTo(keyId);
    byte[] decodedKeys =
        decodeAndDecryptWithAead(resultPrivate.getJsonEncodedKeyset(), keyEncryptionKeyUri);
    assertThat(decodedKeys).isEqualTo(privateKeySplit.getBytes());
    byte[] migrationDecodedKeys =
        decodeAndDecryptWithAead(
            resultPrivate.getMigrationJsonEncodedKeyset(), migrationKeyEncryptionKeyUri);
    assertThat(migrationDecodedKeys).isEqualTo(privateKeySplit.getBytes());
    assertThat(resultPrivate.getExpirationTime()).isEqualTo(expirationTime);
    assertThat(resultPrivate.getCreationTime()).isEqualTo(creationTime);
    assertThat(resultPrivate.getKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    assertThat(resultPrivate.getMigrationKeyEncryptionKeyUri())
        .isEqualTo(migrationKeyEncryptionKeyUri);
    assertThat(resultPrivate.getKeySplitDataList().get(0).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(key.getKeySplitDataList().getFirst().getKeySplitKeyEncryptionKeyUri());
    assertThat(resultPrivate.getMigrationKeySplitDataList().get(0).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(key.getMigrationKeySplitDataList().getFirst().getKeySplitKeyEncryptionKeyUri());
    // Test data has 2 keysplitdata items, so the new one would be after
    assertThat(resultPrivate.getKeySplitDataList().get(2).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(keyEncryptionKeyUri);
    assertThat(resultPrivate.getMigrationKeySplitDataList().get(2).getKeySplitKeyEncryptionKeyUri())
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
    String keyId = "asdf";
    var creationTime = Instant.now().toEpochMilli();
    var expirationTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();

    EncryptionKey key =
        FakeEncryptionKey.createWithMigration().toBuilder()
            .setKeyId(keyId)
            .setSetName(setName)
            .setExpirationTime(expirationTime)
            .setCreationTime(creationTime)
            .setKeyEncryptionKeyUri(keyEncryptionKeyUri)
            .setMigrationKeyEncryptionKeyUri(migrationKeyEncryptionKeyUri)
            .setPublicKeyMaterial(toBase64(publicKey))
            .build();
    taskWithMock.createKey(key, encryptedPrivateKeySplit, migrationEncryptedPrivateKeySplit);

    // keyDb.getActiveKeys always uses the empty string as the default key set.
    if (setName.isEmpty()) {
      com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto
              .EncryptionKey
          resultPublic = keyDb.getActiveKeys(DEFAULT_SET_NAME, KEY_LIMIT).getFirst();
      assertThat(resultPublic.getKeyId()).isEqualTo(keyId);
      assertThat(resultPublic.getPublicKey()).isEqualTo(key.getPublicKey());
      assertThat(resultPublic.getCreationTime()).isEqualTo(creationTime);
      assertThat(resultPublic.getExpirationTime()).isEqualTo(expirationTime);
    }

    com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey
        resultPrivate = keyDb.getKey(keyId);
    assertThat(resultPrivate.getKeyId()).isEqualTo(keyId);
    byte[] decodedKeys =
        decodeAndDecryptWithAead(resultPrivate.getJsonEncodedKeyset(), keyEncryptionKeyUri);
    assertThat(decodedKeys).isEqualTo(privateKeySplit.getBytes());
    assertThat(resultPrivate.getExpirationTime()).isEqualTo(expirationTime);
    assertThat(resultPrivate.getCreationTime()).isEqualTo(creationTime);
    assertThat(resultPrivate.getKeyEncryptionKeyUri()).isEqualTo(keyEncryptionKeyUri);
    assertThat(resultPrivate.getKeySplitDataList().get(0).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(key.getKeySplitDataList().getFirst().getKeySplitKeyEncryptionKeyUri());
    // Test data has 2 keysplitdata items, so the new one would be after
    assertThat(resultPrivate.getKeySplitDataList().get(2).getKeySplitKeyEncryptionKeyUri())
        .isEqualTo(keyEncryptionKeyUri);

    // Migration data should be empty since it was missing from the input migration EncryptionKey
    assertThat(resultPrivate.getMigrationJsonEncodedKeyset()).isEqualTo("");
    assertThat(resultPrivate.getMigrationKeyEncryptionKeyUri()).isEqualTo("");
    assertThat(resultPrivate.getMigrationKeySplitDataList().size()).isEqualTo(0);
  }

  @Test
  public void createKey_databaseException() {
    CreateKeyTask taskWithMock =
        new GcpCreateKeyTask(
            keyDb, kmsClient, KEK_URI, migrationKmsClient, migrationKeyEncryptionKeyUri, false);
    EncryptionKey key =
        EncryptionKey.newBuilder()
            .setKeyId("asdf")
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
            keyDb, kmsClient, KEK_URI, migrationKmsClient, migrationKeyEncryptionKeyUri, false);
    EncryptionKey key =
        EncryptionKey.newBuilder()
            .setKeyId("myName")
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
            keyDb, kmsClient, KEK_URI, migrationKmsClient, migrationKeyEncryptionKeyUri, false);
    EncryptionKey key =
        EncryptionKey.newBuilder()
            .setKeyId("myName")
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
    createKey_happyInput_createsExpected_base(KEK_BASE_URI.replace("$setName$", ""), KEK_BASE_URI);
  }

  @Test
  public void createKey_happyInput_createsExpected_disableKeySetAcl() throws Exception {
    createKey_happyInput_createsExpected_base(KEK_URI, KEK_URI);
  }

  public void createKey_happyInput_createsExpected_base(String keyEncryptionKeyUri, String baseUrl)
      throws Exception {
    // Given
    var encryptedPrivateKeyWithAssociatedPublicKey =
        toBase64AndEncodeWithAead(
            TEST_PRIVATE_KEY, kmsClient.getAead(keyEncryptionKeyUri), TEST_PUBLIC_KEY);
    var key =
        FakeEncryptionKey.create().toBuilder()
            .setPublicKeyMaterial(toBase64(TEST_PUBLIC_KEY))
            .build();

    // When
    var task =
        new GcpCreateKeyTask(
            keyDb, kmsClient, baseUrl, migrationKmsClient, migrationKeyEncryptionKeyUri, false);
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
        FakeEncryptionKey.create().toBuilder()
            .setPublicKeyMaterial(Base64.getEncoder().encodeToString(TEST_PRIVATE_KEY.getBytes()))
            .build();

    // When
    var task =
        new GcpCreateKeyTask(
            keyDb, kmsClient, KEK_URI, migrationKmsClient, migrationKeyEncryptionKeyUri, false);
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
