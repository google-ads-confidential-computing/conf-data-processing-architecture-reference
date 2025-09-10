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

import com.google.common.collect.ImmutableMap;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KmsClient;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.protobuf.ByteString;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.PopulateMigrationKeyData;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient.KeyStorageServiceException;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.MigrationKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.CreateSplitKeyTaskBase;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid.KeyIdFactory;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.KmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationPeerCoordinatorKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationPeerKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerCoordinatorKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerKmsAeadClient;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.DataKeyProto.DataKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The entire split key generation task. Handles generating the key split, encrypting the primary
 * split, encrypting the second split with the PeerCoordinatorKmsKeyAead and sending it to
 * Coordinator B, and finally receiving and storing the signed response.
 *
 * <p>The main difference with AWS is the direct encryption of Coordinator B keySplit rather than
 * the use of a DataKey via the KeyExchangeService.
 */
public final class GcpCreateSplitKeyTask extends CreateSplitKeyTaskBase {

  private static final Logger logger = LoggerFactory.getLogger(GcpCreateSplitKeyTask.class);

  private final LogMetricHelper logMetricHelper;
  private final String keyEncryptionKeyBaseUri;
  private final String migrationKeyEncryptionKeyBaseUri;
  private final String peerCoordinatorKeyEncryptionKeyBaseUri;
  private final String migrationPeerCoordinatorKeyEncryptionKeyBaseUri;
  private final KmsClient kmsClient;
  private final KmsClient migrationKmsClient;
  private final KmsClient peerKmsClient;
  private final KmsClient migrationPeerKmsClient;
  private final Provider<Boolean> populateMigrationDataProvider;

  @Inject
  public GcpCreateSplitKeyTask(
      @KeyEncryptionKeyBaseUri String keyEncryptionKeyBaseUri,
      @MigrationKeyEncryptionKeyBaseUri String migrationKeyEncryptionKeyBaseUri,
      @PeerCoordinatorKeyEncryptionKeyBaseUri String peerCoordinatorKeyEncryptionKeyBaseUri,
      @MigrationPeerCoordinatorKeyEncryptionKeyBaseUri String migrationPeerCoordinatorKeyEncryptionKeyBaseUri,
      @KmsAeadClient KmsClient kmsClient,
      @MigrationKmsAeadClient KmsClient migrationKmsClient,
      @PeerKmsAeadClient KmsClient peerKmsClient,
      @MigrationPeerKmsAeadClient KmsClient migrationPeerKmsClient,
      @PopulateMigrationKeyData Provider<Boolean> populateMigrationDataProvider,
      KeyIdFactory keyIdFactory,
      KeyDb keyDb,
      KeyStorageClient keyStorageClient,
      LogMetricHelper logMetricHelper) {
    super(Optional.empty(), keyDb, keyStorageClient, keyIdFactory, logMetricHelper);
    this.logMetricHelper = logMetricHelper;
    this.keyEncryptionKeyBaseUri = keyEncryptionKeyBaseUri;
    this.migrationKeyEncryptionKeyBaseUri = migrationKeyEncryptionKeyBaseUri;
    this.peerCoordinatorKeyEncryptionKeyBaseUri = peerCoordinatorKeyEncryptionKeyBaseUri;
    this.migrationPeerCoordinatorKeyEncryptionKeyBaseUri = migrationPeerCoordinatorKeyEncryptionKeyBaseUri;
    this.kmsClient = kmsClient;
    this.migrationKmsClient = migrationKmsClient;
    this.peerKmsClient = peerKmsClient;
    this.migrationPeerKmsClient = migrationPeerKmsClient;
    this.populateMigrationDataProvider = populateMigrationDataProvider;
  }

  /**
   * The actual key generation process. Performs the necessary encryption key generation and
   * splitting, key storage request, and database persistence with signatures.
   *
   * @see CreateSplitKeyTaskBase#createSplitKey(String, String, int, int, int, boolean, Instant)
   */
  public void createSplitKey(
      String setName,
      String tinkTemplate,
      int count,
      int validityInDays,
      int ttlInDays,
      boolean noRefreshWindow,
      Instant activation)
      throws ServiceException {
    createSplitKeyBase(
        setName,
        tinkTemplate,
        count,
        validityInDays,
        ttlInDays,
        noRefreshWindow,
        activation,
        Optional.empty(),
        populateMigrationDataProvider.get());
  }

  /**
   * Encrypt CoordinatorB KeySplit using Peer Coordinator KMS Key.
   *
   * @param dataKey Unused by design since GCP encrypts the keySplit directly.
   */
  @Override
  protected String encryptPeerCoordinatorSplit(
      String setName, ByteString keySplit, Optional<DataKey> dataKey, String publicKeyMaterial)
      throws ServiceException {
    return encryptPeerCoordinatorSplit(
        setName,
        keySplit,
        publicKeyMaterial,
        false);
  }

  /**
   * Encrypt CoordinatorB Migration KeySplit using Migration Peer Coordinator KMS Key.
   *
   * @param dataKey Unused by design since GCP encrypts the keySplit directly.
   */
  @Override
  protected String encryptMigrationPeerCoordinatorSplit(
      String setName, ByteString keySplit, Optional<DataKey> dataKey, String publicKeyMaterial)
      throws ServiceException {
    return encryptPeerCoordinatorSplit(
        setName,
        keySplit,
        publicKeyMaterial,
        populateMigrationDataProvider.get());
  }

  /**
   * Encrypt CoordinatorB KeySplit using Peer Coordinator KMS Key.
   */
  private String encryptPeerCoordinatorSplit(
      String setName, ByteString keySplit, String publicKeyMaterial, Boolean useMigrationData)
      throws ServiceException {
    try {
      Aead aead = useMigrationData
          ? getMigrationPeerAead(getMigrationPeerCoordinatorKmsKeyBaseUri(setName))
          : getPeerAead(getPeerCoordinatorKmsKeyBaseUri(setName));
      return Base64.getEncoder()
          .encodeToString(
              aead.encrypt(keySplit.toByteArray(), Base64.getDecoder().decode(publicKeyMaterial)));
    } catch (GeneralSecurityException e) {
      throw new ServiceException(Code.INVALID_ARGUMENT, "Failed to encrypt key split.", e);
    }
  }

  /** Takes in kmsKeyEncryptionKeyUri and return an Aead for the key. */
  @Override
  protected Aead getAead(String keyEncryptionKeyUri) throws GeneralSecurityException {
    logger.info("Aead keyuri: " + keyEncryptionKeyUri);
    return kmsClient.getAead(keyEncryptionKeyUri);
  }

  protected Aead getMigrationAead(String keyEncryptionKeyUri) throws GeneralSecurityException {
    logger.info("Migration Aead keyuri: " + keyEncryptionKeyUri);
    return migrationKmsClient.getAead(keyEncryptionKeyUri);
  }

  /** Takes in setName and returns kmsKeyEncryptionKeyUri for the key set. */
  @Override
  protected String getKmsKeyEncryptionKeyUri(String setName) {
    return getKeyEncryptionKeyUri(setName, this.keyEncryptionKeyBaseUri);
  }

  /** Takes in setName and returns kmsKeyEncryptionMigrationKeyUri for the key set. */
  @Override
  protected String getMigrationKmsKeyEncryptionKeyUri(String setName) {
    return getKeyEncryptionKeyUri(setName, this.migrationKeyEncryptionKeyBaseUri);
  }

  private String getPeerCoordinatorKmsKeyBaseUri(String setName) {
    return getKeyEncryptionKeyUri(setName, this.peerCoordinatorKeyEncryptionKeyBaseUri);
  }

  private String getMigrationPeerCoordinatorKmsKeyBaseUri(String setName) {
    return getKeyEncryptionKeyUri(setName, this.migrationPeerCoordinatorKeyEncryptionKeyBaseUri);
  }

  private String getKeyEncryptionKeyUri(String setName, String keyEncryptionKeyBaseUri) {
    String keyEncryptionKeyUri = keyEncryptionKeyBaseUri.replace("$setName$", setName);
    logger.info("Keyuri: " + keyEncryptionKeyUri);
    return keyEncryptionKeyUri;
  }

  private Aead getPeerAead(String keyEncryptionKeyUri) throws GeneralSecurityException {
    logger.info("Peer Aead keyuri: " + keyEncryptionKeyUri);
    return peerKmsClient.getAead(keyEncryptionKeyUri);
  }

  private Aead getMigrationPeerAead(String keyEncryptionKeyUri) throws GeneralSecurityException {
    logger.info("Migration Peer Aead keyuri: " + keyEncryptionKeyUri);
    return migrationPeerKmsClient.getAead(keyEncryptionKeyUri);
  }

  /**
   * Send KeySplit to Peer Coordinator using KeyStorageService.
   *
   * @param dataKey Unused by design since GCP does not use a data key to encrypt keySplit. See
   *     {@link GcpCreateSplitKeyTask#encryptPeerCoordinatorSplit}
   */
  @Override
  protected EncryptionKey sendKeySplitToPeerCoordinator(
      EncryptionKey unsignedCoordinatorBKey, String encryptedKeySplitB, Optional<String> encryptedMigrationKeySplitB, Optional<DataKey> dataKey)
      throws ServiceException {
    try {
      return keyStorageClient.createKey(unsignedCoordinatorBKey, encryptedKeySplitB, encryptedMigrationKeySplitB);
    } catch (KeyStorageServiceException e) {
      logger.error(
          logMetricHelper.format(
              "key_generation/error", ImmutableMap.of("errorReason", e.getMessage())));
      throw new ServiceException(
          Code.INVALID_ARGUMENT, "Key Storage Service failed to validate, sign, and store key", e);
    }
  }
}
