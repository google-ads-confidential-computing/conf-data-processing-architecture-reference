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

package com.google.scp.coordinator.keymanagement.keygeneration.tasks.aws;

import static com.google.scp.coordinator.keymanagement.keystorage.tasks.aws.DataKeyEncryptionUtil.encryptWithDataKey;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.PublicKeySign;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient.KeyStorageServiceException;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.EncryptionKeySignatureKey;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KeyEncryptionKeyUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KmsKeyAead;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.CreateSplitKeyTaskBase;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid.KeyIdFactory;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.DataKeyProto.DataKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import com.google.scp.shared.crypto.tink.CloudAeadSelector;
import com.google.scp.shared.util.Base64Util;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Optional;

/**
 * The entire split key generation task. Handles fetching an encrypted Key Exchange Key, decrypting
 * it via KMS, generating the key split, sending the second split to Coordinator B, and receiving
 * and storing the signed response.
 */
public final class AwsCreateSplitKeyTask extends CreateSplitKeyTaskBase {

  private final CloudAeadSelector aeadSelector;

  private final Aead keyEncryptionKeyAead;
  private final String keyEncryptionKeyUri;

  @Inject
  public AwsCreateSplitKeyTask(
      @KmsKeyAead Aead keyEncryptionKeyAead,
      @KeyEncryptionKeyUri String keyEncryptionKeyUri,
      @EncryptionKeySignatureKey Optional<PublicKeySign> signatureKey,
      KeyDb keyDb,
      KeyStorageClient keyStorageClient,
      KeyIdFactory keyIdFactory,
      CloudAeadSelector aeadSelector,
      LogMetricHelper logMetricHelper) {
    super(signatureKey, keyDb, keyStorageClient, keyIdFactory, logMetricHelper);
    this.aeadSelector = aeadSelector;
    this.keyEncryptionKeyAead = keyEncryptionKeyAead;
    this.keyEncryptionKeyUri = keyEncryptionKeyUri;
  }

  /**
   * The actual key generation process. Performs the necessary key exchange key fetching, data
   * encryption key generation and splitting, key storage request, and database persistence with
   * signatures.
   *
   * @see CreateSplitKeyTaskBase#createSplitKey(String, String, int, int, int, Instant)
   */
  @Override
  public void createSplitKey(
      String setName,
      String tinkTemplate,
      int count,
      int validityInDays,
      int ttlInDays,
      boolean noRefreshWindow,
      Instant activation)
      throws ServiceException {
    // Reuse same data key for key batch.
    var dataKey = fetchDataKey();
    // Migrating KEKs on AWS is not currently supported.
    boolean populateMigrationData = false;
    createSplitKeyBase(
        setName,
        tinkTemplate,
        count,
        validityInDays,
        ttlInDays,
        noRefreshWindow,
        activation,
        Optional.of(dataKey),
        populateMigrationData);
  }

  @Override
  protected String encryptPeerCoordinatorSplit(
      String setName, ByteString keySplit, Optional<DataKey> dataKey, String publicKey)
      throws ServiceException {
    try {
      return Base64Util.toBase64String(
          encryptWithDataKey(aeadSelector, dataKey.get(), keySplit, publicKey));
    } catch (GeneralSecurityException e) {
      throw new ServiceException(Code.INVALID_ARGUMENT, "Failed to encrypt key split.", e);
    }
  }

  /** Migrations are not supported in AWS. */
  @Override
  protected String encryptMigrationPeerCoordinatorSplit(
      String setName, ByteString keySplit, Optional<DataKey> dataKey, String publicKey)
      throws ServiceException {
    return encryptPeerCoordinatorSplit(setName, keySplit, dataKey, publicKey);
  }

  @Override
  protected Aead getAead(String kmsKeyEncryptionKeyUri) {
    return this.keyEncryptionKeyAead;
  }

  /** Migrations in AWS are not supported. Returns the standard keyEncryptionKeyAead. */
  @Override
  protected Aead getMigrationAead(String migrationKmsKeyEncryptionKeyUri) {
    return this.keyEncryptionKeyAead;
  }

  /** Takes in setName and returns the kmsKeyEncryptionKeyUri for the key set. */
  @Override
  protected String getKmsKeyEncryptionKeyUri(String setName) {
    return this.keyEncryptionKeyUri;
  }

  /** Migrations in AWS are not supported. Returns the kmsKeyEncryptionKeyUri for the key set. */
  @Override
  protected String getMigrationKmsKeyEncryptionKeyUri(String setName) {
    return this.keyEncryptionKeyUri;
  }

  @Override
  protected EncryptionKey sendKeySplitToPeerCoordinator(
      EncryptionKey unsignedCoordinatorBKey,
      String encryptedKeySplitB,
      Optional<String> migrationEncryptedKeySplitB,
      Optional<DataKey> dataKey)
      throws ServiceException {
    try {
      return keyStorageClient.createKey(
          unsignedCoordinatorBKey, dataKey.get(), encryptedKeySplitB, migrationEncryptedKeySplitB);
    } catch (KeyStorageServiceException e) {
      throw new ServiceException(
          Code.INVALID_ARGUMENT, "Key Storage Service failed to validate, sign, and store key", e);
    }
  }

  private DataKey fetchDataKey() throws ServiceException {
    try {
      return keyStorageClient.fetchDataKey();
    } catch (KeyStorageServiceException e) {
      throw new ServiceException(
          Code.INTERNAL, "Failed to fetch data key from key storage service", e);
    }
  }
}
