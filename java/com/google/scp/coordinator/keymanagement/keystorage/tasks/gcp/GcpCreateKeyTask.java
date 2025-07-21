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

import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.SERVICE_ERROR;
import static com.google.scp.shared.api.model.Code.INVALID_ARGUMENT;

import com.google.common.collect.ImmutableList;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KmsClient;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.KmsAeadClient;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.KmsKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.MigrationKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.MigrationKmsKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.PopulateMigrationKeyData;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.CreateKeyTask;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.DataKeyProto.DataKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates a key in the database */
public final class GcpCreateKeyTask implements CreateKeyTask {
  private static final Logger logger = LoggerFactory.getLogger(CreateKeyTask.class);

  private final KeyDb keyDb;
  private final String kmsKeyEncryptionKeyBaseUri;
  private final String migrationKmsKeyEncryptionKeyBaseUri;
  private final KmsClient kmsClient;
  private final KmsClient migrationKmsClient;
  private final Boolean populateMigrationKeyData;

  private record ValidatedKeySplits(
      String reencryptedKeySplit, String reencryptedMigrationKeySplit) {}

  @Inject
  public GcpCreateKeyTask(
      KeyDb keyDb,
      @KmsAeadClient KmsClient kmsClient,
      @KmsKeyEncryptionKeyBaseUri String kmsKeyEncryptionKeyBaseUri,
      @MigrationKmsAeadClient KmsClient migrationKmsClient,
      @MigrationKmsKeyEncryptionKeyBaseUri String migrationKmsKeyEncryptionKeyBaseUri,
      @PopulateMigrationKeyData Boolean populateMigrationKeyData) {
    this.keyDb = keyDb;
    this.kmsClient = kmsClient;
    this.migrationKmsClient = migrationKmsClient;
    this.kmsKeyEncryptionKeyBaseUri = kmsKeyEncryptionKeyBaseUri;
    this.migrationKmsKeyEncryptionKeyBaseUri = migrationKmsKeyEncryptionKeyBaseUri;
    this.populateMigrationKeyData = populateMigrationKeyData;
  }

  /** Creates the key in the database */
  @Override
  public EncryptionKey createKey(
      EncryptionKey encryptionKey, String encryptedKeySplit, String migrationEncryptedKeySplit)
      throws ServiceException {
    if (encryptedKeySplit.isEmpty()) {
      throw new ServiceException(
          Code.INVALID_ARGUMENT, SERVICE_ERROR.name(), "Key payload is empty.");
    }

    EncryptionKey.Builder newEncryptionKeyBuilder = encryptionKey.toBuilder();

    // Ensure no migration data will be returned if this key is not flagged for migration.
    Boolean activeMigration = populateMigrationKeyData;
    if (activeMigration && migrationEncryptedKeySplit.isEmpty()) {
      activeMigration = false;
      newEncryptionKeyBuilder
          .clearMigrationJsonEncodedKeyset()
          .clearMigrationKeyEncryptionKeyUri()
          .clearMigrationKeySplitData();
      String logMessage =
          "KeyID {} for set {} has its migration key payload empty but PopulateMigrationKeyData is"
              + " True. The key will be generated without migration key data.";
      logger.warn(logMessage, encryptionKey.getKeyId(), encryptionKey.getSetName());
    }

    // Populates the common new EncryptionKey data (non-migration).
    String kmsKeyUri = getKmsKeyEncryptionKeyUri(encryptionKey.getSetName());
    var newKeySplitData =
        ImmutableList.<KeySplitData>builder()
            .addAll(encryptionKey.getKeySplitDataList())
            .add(KeySplitData.newBuilder().setKeySplitKeyEncryptionKeyUri(kmsKeyUri).build());
    newEncryptionKeyBuilder
        .setKeyEncryptionKeyUri(kmsKeyUri)
        // Need to clear before adding, otherwise there will be duplicate KeySplitData elements.
        .clearKeySplitData()
        .addAllKeySplitData(newKeySplitData.build());

    // Validates EncryptionKeys
    if (activeMigration) {
      // Validates both the original and migration key splits at the same time.
      String migrationKmsKeyUri = getMigrationKmsKeyEncryptionKeyUri(encryptionKey.getSetName());
      ValidatedKeySplits validatedKeySplits =
          validatedPrivateKeySplitWithMigration(
              kmsKeyUri,
              encryptedKeySplit,
              migrationKmsKeyUri,
              migrationEncryptedKeySplit,
              encryptionKey.getPublicKeyMaterial());
      // Populates the new EncryptionKey with the validated migration data and the validated
      // reencrypted original key split.
      var newMigrationKeySplitData =
          ImmutableList.<KeySplitData>builder()
              .addAll(encryptionKey.getMigrationKeySplitDataList())
              .add(
                  KeySplitData.newBuilder()
                      .setKeySplitKeyEncryptionKeyUri(migrationKmsKeyUri)
                      .build());
      newEncryptionKeyBuilder
          .setJsonEncodedKeyset(validatedKeySplits.reencryptedKeySplit())
          .setMigrationJsonEncodedKeyset(validatedKeySplits.reencryptedMigrationKeySplit())
          .setMigrationKeyEncryptionKeyUri(migrationKmsKeyUri)
          // Need to clear before adding, otherwise there will be duplicate KeySplitData elements.
          .clearMigrationKeySplitData()
          .addAllMigrationKeySplitData(newMigrationKeySplitData.build());
    } else /* (non-migration) */ {
      String validatedKeySplit =
          validatedPrivateKeySplit(
              kmsKeyUri, encryptedKeySplit, encryptionKey.getPublicKeyMaterial());
      // Populates the new EncryptionKey with the validated reencrypted key split (non-migration).
      newEncryptionKeyBuilder.setJsonEncodedKeyset(validatedKeySplit);
    }

    EncryptionKey newEncryptionKey = newEncryptionKeyBuilder.build();
    keyDb.createKey(newEncryptionKey);
    String logMessage =
        activeMigration
            ? "Created new key {} with migration data for set {}"
            : "Created new key {} for set {}";
    logger.info(logMessage, newEncryptionKey.getKeyId(), encryptionKey.getSetName());
    // TODO(b/206030473): Figure out where exactly signing should happen.
    return newEncryptionKey;
  }

  @Override
  public EncryptionKey createKey(
      EncryptionKey encryptionKey,
      DataKey dataKey,
      String decryptedKeySplit,
      String migrationDecryptedKeySplit)
      throws ServiceException {
    throw new ServiceException(
        Code.NOT_FOUND, SERVICE_ERROR.name(), "DataKey decryption not implemented");
  }

  /**
   * Attempts to validate a private key split along with the associated public key. Valid private
   * key split is re-encrypted.
   *
   * @return Validated and re-encrypted private key split without associated data.
   * @throw ServiceException if the private key split is invalid.
   */
  private String validatedPrivateKeySplit(
      String kmsKeyUri, String privateKeySplit, String publicKeyMaterial) throws ServiceException {
    try {
      Aead kmsAead = kmsClient.getAead(kmsKeyUri);
      byte[] cipherText = decodeBase64(privateKeySplit);
      byte[] associatedData = decodeBase64(publicKeyMaterial);
      byte[] plainText = kmsAead.decrypt(cipherText, associatedData);
      return encryptAndEncodeKeySplit(kmsAead, plainText);
    } catch (NullPointerException | GeneralSecurityException ex) {
      throw new ServiceException(
          INVALID_ARGUMENT, SERVICE_ERROR.name(), "Key-split validation failed.", ex);
    }
  }

  /**
   * Attempts to validate a private key split and a migration key split along with the associated
   * public key. Valid private key splits are returned re-encrypted.
   *
   * @return ValidatedKeySplits containing valid and re-encrypted private key splits without
   *     associated data.
   * @throw ServiceException if the private key split is invalid.
   */
  private ValidatedKeySplits validatedPrivateKeySplitWithMigration(
      String kmsKeyUri,
      String privateKeySplit,
      String migrationKmsKeyUri,
      String migrationPrivateKeySplit,
      String publicKeyMaterial)
      throws ServiceException {
    try {

      Aead kmsAead = kmsClient.getAead(kmsKeyUri);
      Aead migrationKmsAead = migrationKmsClient.getAead(migrationKmsKeyUri);
      // Decodes associated material for decryption
      byte[] associatedData = decodeBase64(publicKeyMaterial);
      byte[] cipherText = decodeBase64(privateKeySplit);
      byte[] migrationCipherText = decodeBase64(migrationPrivateKeySplit);
      // Decrypts the key splits.
      byte[] plainText = kmsAead.decrypt(cipherText, associatedData);
      byte[] migrationPlainText = migrationKmsAead.decrypt(migrationCipherText, associatedData);
      // Guarantee the underlying key split material matches
      if (!Arrays.equals(plainText, migrationPlainText)) {
        throw new ServiceException(
            INVALID_ARGUMENT,
            SERVICE_ERROR.name(),
            "Key-split validation failed. Migration key does not match source key.");
      }
      // Reencrypts and returns the validated key splits
      String reencryptedKeySplit = encryptAndEncodeKeySplit(kmsAead, plainText);
      String reencryptedMigrationKeySplit =
          encryptAndEncodeKeySplit(migrationKmsAead, migrationPlainText);
      return new ValidatedKeySplits(reencryptedKeySplit, reencryptedMigrationKeySplit);
    } catch (NullPointerException | GeneralSecurityException ex) {
      throw new ServiceException(
          INVALID_ARGUMENT, SERVICE_ERROR.name(), "Key-split validation failed.", ex);
    }
  }

  /** Assists in decoding strings to byte arrays. */
  private byte[] decodeBase64(String data) {
    return Base64.getDecoder().decode(data);
  }

  /** Assists in encrypting and then encoding plain text with a chosen Aead. */
  private String encryptAndEncodeKeySplit(Aead aead, byte[] plainTextKeySplit)
      throws GeneralSecurityException {
    return Base64.getEncoder().encodeToString(aead.encrypt(plainTextKeySplit, new byte[0]));
  }

  /** Returns the Kms key encryption key URI for a provided SetName. */
  private String getKmsKeyEncryptionKeyUri(String setName) {
    return this.kmsKeyEncryptionKeyBaseUri.replace("$setName$", setName);
  }

  /** Returns the migration Kms key encryption key URI for a provided SetName. */
  private String getMigrationKmsKeyEncryptionKeyUri(String setName) {
    return this.migrationKmsKeyEncryptionKeyBaseUri.replace("$setName$", setName);
  }
}
