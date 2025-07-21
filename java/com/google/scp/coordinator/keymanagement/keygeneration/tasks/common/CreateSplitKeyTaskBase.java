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

package com.google.scp.coordinator.keymanagement.keygeneration.tasks.common;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.scp.shared.util.KeysetHandleSerializerUtil.toJsonCleartext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.mac.MacConfig;
import com.google.protobuf.ByteString;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid.KeyIdFactory;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.util.KeySplitDataUtil;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyTypeProto.EncryptionKeyType;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.DataKeyProto.DataKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyStatusProto.EncryptionKeyStatus;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import com.google.scp.shared.util.KeySplitUtil;
import com.google.scp.shared.util.PublicKeyConversionUtil;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared base class between cloud providers for Split Key Generation. Handles generating the key
 * split, sending the second split to Coordinator B, and receiving and storing the signed response.
 */
public abstract class CreateSplitKeyTaskBase implements CreateSplitKeyTask {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateSplitKeyTaskBase.class);
  private static final int KEY_ID_CONFLICT_MAX_RETRY = 5;
  protected final Optional<PublicKeySign> signatureKey;
  protected final KeyDb keyDb;
  protected final KeyStorageClient keyStorageClient;
  protected final KeyIdFactory keyIdFactory;
  protected final LogMetricHelper logMetricHelper;

  static {
    try {
      HybridConfig.register();
      MacConfig.register();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Error initializing tink.");
    }
  }

  public CreateSplitKeyTaskBase(
      Optional<PublicKeySign> signatureKey,
      KeyDb keyDb,
      KeyStorageClient keyStorageClient,
      KeyIdFactory keyIdFactory,
      LogMetricHelper logMetricHelper) {
    this.signatureKey = signatureKey;
    this.keyDb = keyDb;
    this.keyStorageClient = keyStorageClient;
    this.keyIdFactory = keyIdFactory;
    this.logMetricHelper = logMetricHelper;
  }

  /**
   * Encrypts a key split for Coordinator B and returns it as an Encrypted key. Optionally use
   * dataKey to encrypt.
   *
   * @throws ServiceException in case of encryption errors.
   */
  protected abstract String encryptPeerCoordinatorSplit(
      String setName, ByteString keySplit, Optional<DataKey> dataKey, String publicKey)
      throws ServiceException;

  protected abstract String encryptMigrationPeerCoordinatorSplit(
      String setName, ByteString keySplit, Optional<DataKey> dataKey, String publicKey)
      throws ServiceException;

  /** Takes in kmsKeyEncryptionKeyUri and returns an Aead for the key. */
  protected abstract Aead getAead(String kmsKeyEncryptionKeyUri) throws GeneralSecurityException;

  /** Takes in migrationKmsKeyEncryptionKeyUri and returns an Aead for the key. */
  protected abstract Aead getMigrationAead(String migrationKmsKeyEncryptionKeyUri)
      throws GeneralSecurityException;

  /** Takes in setName and returns kmsKeyEncryptionKeyUri for the key set. */
  protected abstract String getKmsKeyEncryptionKeyUri(String setName);

  /** Takes in setName and returns the migrationKmsKeyEncryptionKeyUri for the key set. */
  protected abstract String getMigrationKmsKeyEncryptionKeyUri(String setName);

  /**
   * Takes in an encrypted KeySplit for Coordinator B and an unsignedKey to send to the KeyStorage
   * service for database storage and verification respectively. Returns an EncryptionKey with the
   * keystorage response. Optionally creates a key with a dataKey.
   *
   * @throws ServiceException in case of key storage errors.
   */
  protected abstract EncryptionKey sendKeySplitToPeerCoordinator(
      EncryptionKey unsignedCoordinatorBKey,
      String encryptedKeySplitB,
      Optional<String> encryptedMigrationKeySplitB,
      Optional<DataKey> dataKey)
      throws ServiceException;

  public void create(
      String setName, String tinkTemplate, int numDesiredKeys, int validityInDays, int ttlInDays)
      throws ServiceException {
    // Anchor one consistent definition of now for the creation process.
    Instant now = Instant.now();

    // Check if there are enough number of active keys, if not, create any missing keys.
    ImmutableList<EncryptionKey> activeKeys = keyDb.getActiveKeys(setName, numDesiredKeys, now);

    LOGGER.info(
        "[{}] Found {} of {} expected active keys.", setName, activeKeys.size(), numDesiredKeys);
    if (activeKeys.size() < numDesiredKeys) {
      LOGGER.info(
          "[{}] Generating {} immediately active keys.",
          setName,
          numDesiredKeys - activeKeys.size());
      createSplitKey(
          setName,
          tinkTemplate,
          numDesiredKeys - activeKeys.size(),
          validityInDays,
          ttlInDays,
          now);
    }
    activeKeys = keyDb.getActiveKeys(setName, numDesiredKeys, now);
    if (activeKeys.size() < numDesiredKeys) {
      LOGGER.error(format(setName, "activeKeys_lt_numDesiredKeys"));
      throw new AssertionError(
          String.format(
              "Unexpected failure to generate sufficient immediately active keys for key set"
                  + " \"%s\". Only %d of %d found.",
              setName, activeKeys.size(), numDesiredKeys));
    }

    // Check if there will be enough number of active keys when each active key expires, if not,
    // create any missing pending-active keys.
    ImmutableList<Instant> expirations =
        activeKeys.stream()
            .filter(EncryptionKey::hasExpirationTime)
            .map(EncryptionKey::getExpirationTime)
            .map(Instant::ofEpochMilli)
            .distinct()
            .sorted()
            .collect(toImmutableList());

    for (Instant expiration : expirations) {
      int actual = keyDb.getActiveKeys(setName, numDesiredKeys, expiration).size();
      LOGGER.info(
          "[{}] Found {} of {} expected keys for when some keys expire on datetime={}.",
          setName,
          actual,
          numDesiredKeys,
          expiration);
      if (actual < numDesiredKeys) {
        LOGGER.info(
            "[{}] Generating {} pending active keys  activating on datetime={}",
            setName,
            numDesiredKeys - actual,
            expiration);
        createSplitKey(
            setName,
            tinkTemplate,
            numDesiredKeys - actual,
            validityInDays,
            ttlInDays,
            expiration.minus(KEY_REFRESH_WINDOW));
      }
      actual = keyDb.getActiveKeys(setName, numDesiredKeys, expiration).size();
      if (actual < numDesiredKeys) {
        LOGGER.error(format(setName, "actual_lt_numDesiredKeys"));
        throw new AssertionError(
            String.format(
                "Unexpected failure to generate sufficient pending active keys for datetime=%s for"
                    + " key set \"%s\". Only %d of %d found.",
                expiration, setName, actual, numDesiredKeys));
      }
    }
  }

  /**
   * Key generation process. Performs encryption key generation and splitting, key storage request,
   * and database persistence with signatures. Coordinator B encryption and key storage creation are
   * handled by abstract methods implemented in each cloud provider.
   *
   * @param activation the instant when the key should be active for encryption.
   * @param dataKey Passed to encryptPeerCoordinatorSplit and sendKeySplitToPeerCoordinator as
   *     needed for each cloud provider.
   */
  protected final void createSplitKeyBase(
      String setName,
      String tinkTemplate,
      int count,
      int validityInDays,
      int ttlInDays,
      Instant activation,
      Optional<DataKey> dataKey,
      Boolean populateMigrationData)
      throws ServiceException {
    LOGGER.info("[{}] Trying to generate {} keys.", setName, count);
    for (int i = 0; i < count; i++) {
      createSplitKeyBase(
          setName,
          tinkTemplate,
          validityInDays,
          ttlInDays,
          activation,
          dataKey,
          populateMigrationData,
          0);
    }
    LOGGER.info(
        "[{}] Successfully generated {} keys to be active on {}.", setName, count, activation);
  }

  private void createSplitKeyBase(
      String setName,
      String tinkTemplate,
      int validityInDays,
      int ttlInDays,
      Instant activation,
      Optional<DataKey> dataKey,
      Boolean populateMigrationData,
      int keyIdConflictRetryCount)
      throws ServiceException {
    Instant creationTime = Instant.now();
    EncryptionKey unsignedCoordinatorAKey;
    EncryptionKey unsignedCoordinatorBKey;
    String encryptedKeySplitB;
    Optional<String> encryptedMigrationKeySplitB;
    try {
      var template = KeyTemplates.get(tinkTemplate);
      KeysetHandle privateKeysetHandle = generateKeysetHandleWithJitter(template);
      Optional<KeysetHandle> publicKeysetHandle = getPublicKeysetHandle(privateKeysetHandle);

      ImmutableList<ByteString> keySplits = KeySplitUtil.xorSplit(privateKeysetHandle, 2);
      String keyEncryptionKeyUri = getKmsKeyEncryptionKeyUri(setName);
      Optional<String> migrationKeyEncryptionKeyUri =
          populateMigrationData
              ? Optional.of(getMigrationKmsKeyEncryptionKeyUri(setName))
              : Optional.empty();
      EncryptionKey key =
          buildEncryptionKey(
              setName,
              keyIdFactory.getNextKeyId(keyDb),
              creationTime,
              activation,
              validityInDays,
              ttlInDays,
              publicKeysetHandle,
              keyEncryptionKeyUri,
              migrationKeyEncryptionKeyUri,
              signatureKey);
      unsignedCoordinatorAKey =
          migrationKeyEncryptionKeyUri.isEmpty()
              ? createCoordinatorAKey(
                  keySplits.get(0), key, getAead(keyEncryptionKeyUri), keyEncryptionKeyUri)
              : createCoordinatorAKeyWithMigration(
                  keySplits.get(0),
                  key,
                  getAead(keyEncryptionKeyUri),
                  keyEncryptionKeyUri,
                  getMigrationAead(migrationKeyEncryptionKeyUri.get()),
                  migrationKeyEncryptionKeyUri.get());
      unsignedCoordinatorBKey = createCoordinatorBKey(key);

      encryptedKeySplitB =
          encryptPeerCoordinatorSplit(
              setName, keySplits.get(1), dataKey, key.getPublicKeyMaterial());
      // Only encrypt with the peer migration KEK when there is an active ongoing migration.
      encryptedMigrationKeySplitB =
          populateMigrationData
              ? Optional.of(
                  encryptMigrationPeerCoordinatorSplit(
                      setName, keySplits.get(1), dataKey, key.getPublicKeyMaterial()))
              : Optional.empty();
    } catch (GeneralSecurityException | IOException e) {
      LOGGER.error(format(setName, "crypto_error"));
      String msg = "Error generating keys.";
      throw new ServiceException(Code.INTERNAL, "CRYPTO_ERROR", msg, e);
    } catch (ServiceException e) {
      LOGGER.error(format(setName, e.getErrorReason()));
      throw e;
    }

    try {
      // Reserve the key ID with a placeholder key that's not valid yet. Will be made valid at a
      // later step once key-split is successfully delivered to coordinator B.
      keyDb.createKey(
          unsignedCoordinatorAKey.toBuilder()
              // Setting activation_time to expiration_time such that the key is invalid for now.
              .setActivationTime(unsignedCoordinatorAKey.getExpirationTime())
              .build(),
          false);
    } catch (ServiceException e) {
      if (e.getErrorCode().equals(Code.ALREADY_EXISTS)
          && keyIdConflictRetryCount < KEY_ID_CONFLICT_MAX_RETRY) {
        LOGGER.warn(
            String.format(
                "Failed to insert placeholder key split with keyId %s, retry count: %d, error "
                    + "message: %s",
                unsignedCoordinatorAKey.getKeyId(), keyIdConflictRetryCount, e.getErrorReason()));
        createSplitKeyBase(
            setName,
            tinkTemplate,
            validityInDays,
            ttlInDays,
            activation,
            dataKey,
            populateMigrationData,
            keyIdConflictRetryCount + 1);
        return;
      }
      LOGGER.error(format(setName, e.getErrorReason()));
      LOGGER.error("Failed to insert placeholder key due to database error");
      throw e;
    }

    // Send Coordinator B valid key split
    EncryptionKey partyBResponse =
        sendKeySplitToPeerCoordinator(
            unsignedCoordinatorBKey, encryptedKeySplitB, encryptedMigrationKeySplitB, dataKey);

    // Accumulate signatures
    EncryptionKey.Builder signedCoordinatorAKeyBuilder = unsignedCoordinatorAKey.toBuilder();
    signedCoordinatorAKeyBuilder
        // Need to clear KeySplitData before adding the combined KeySplitData lists.
        .clearKeySplitData()
        .addAllKeySplitData(
            combineKeySplitData(
                partyBResponse.getKeySplitDataList(),
                unsignedCoordinatorAKey.getKeySplitDataList()))
        .clearMigrationKeySplitData()
        .addAllMigrationKeySplitData(
            combineKeySplitData(
                partyBResponse.getMigrationKeySplitDataList(),
                unsignedCoordinatorAKey.getMigrationKeySplitDataList()));

    // Migration data was not populated from the secondary coordinator so it should not be written
    // to the primary coordinator's DB.
    if (populateMigrationData && partyBResponse.getMigrationKeySplitDataList().isEmpty()) {
      signedCoordinatorAKeyBuilder
          .clearMigrationJsonEncodedKeyset()
          .clearMigrationKeyEncryptionKeyUri()
          .clearMigrationKeySplitData();
      String logMessage =
          "Key {} for set {} will exclude migration data. Coordinator B's response missing"
              + " migration key split data.";
      LOGGER.warn(logMessage, partyBResponse.getKeyId(), partyBResponse.getSetName());
    }

    EncryptionKey signedCoordinatorAKey = signedCoordinatorAKeyBuilder.build();
    // Note: We want to store the keys as they are generated and signed.
    try {
      keyDb.createKey(signedCoordinatorAKey, true);
    } catch (ServiceException e) {
      LOGGER.error(format(setName, e.getErrorReason()));
      throw e;
    }
  }

  private String format(String setName, String errorReason) {
    return logMetricHelper.format(
        "key_generation/error", ImmutableMap.of("setName", setName, "errorReason", errorReason));
  }

  /**
   * Create unsigned EncryptionKey for Coordinator A, to store after receiving signature from
   * Coordinator B. Performs key split encryption for Coordinator A.
   */
  private static EncryptionKey createCoordinatorAKey(
      ByteString keySplit, EncryptionKey key, Aead keyEncryptionKeyAead, String keyEncryptionKeyUri)
      throws GeneralSecurityException, IOException {
    String encryptedKeySplitA =
        Base64.getEncoder()
            .encodeToString(keyEncryptionKeyAead.encrypt(keySplit.toByteArray(), new byte[0]));
    return key.toBuilder()
        .setJsonEncodedKeyset(encryptedKeySplitA)
        .setKeyEncryptionKeyUri(keyEncryptionKeyUri)
        .build();
  }

  /**
   * Create unsigned EncryptionKey with migration data for Coordinator A, to store after receiving
   * signature from Coordinator B. Performs key split encryption for Coordinator A.
   */
  private static EncryptionKey createCoordinatorAKeyWithMigration(
      ByteString keySplit,
      EncryptionKey key,
      Aead keyEncryptionKeyAead,
      String keyEncryptionKeyUri,
      Aead migrationKeyEncryptionKeyAead,
      String migrationKeyEncryptionKeyUri)
      throws GeneralSecurityException, IOException {
    EncryptionKey keyWithoutMigrationData =
        createCoordinatorAKey(keySplit, key, keyEncryptionKeyAead, keyEncryptionKeyUri);
    String migrationEncryptedKeySplitA =
        Base64.getEncoder()
            .encodeToString(
                migrationKeyEncryptionKeyAead.encrypt(keySplit.toByteArray(), new byte[0]));
    return keyWithoutMigrationData.toBuilder()
        .setMigrationJsonEncodedKeyset(migrationEncryptedKeySplitA)
        .setMigrationKeyEncryptionKeyUri(migrationKeyEncryptionKeyUri)
        .build();
  }

  /**
   * Create unsigned EncryptionKey for Coordinator B, to send to Coordinator B in the Create Key
   * service.
   */
  private static EncryptionKey createCoordinatorBKey(EncryptionKey key)
      throws GeneralSecurityException, IOException {
    return key.toBuilder()
        .setJsonEncodedKeyset("")
        .setKeyEncryptionKeyUri("")
        .setMigrationJsonEncodedKeyset("")
        .setMigrationKeyEncryptionKeyUri("")
        .build();
  }

  /**
   * A Builder pre-populated with shared fields between both generated encryption keys, and also
   * with a key split data containing Coordinator A's signature of the public key material.
   */
  private static EncryptionKey buildEncryptionKey(
      String setName,
      String keyId,
      Instant creationTime,
      Instant activation,
      int validityInDays,
      int ttlInDays,
      Optional<KeysetHandle> publicKeysetHandle,
      String keyEncryptionKeyUri,
      Optional<String> migrationkeyEncryptionKeyUri,
      Optional<PublicKeySign> signatureKey)
      throws ServiceException, IOException {
    // LINT.IfChange
    EncryptionKey.Builder unsignedEncryptionKey =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setSetName(setName)
            .setStatus(EncryptionKeyStatus.ACTIVE)
            .setCreationTime(creationTime.toEpochMilli())
            .setActivationTime(activation.toEpochMilli())
            .setKeyType(EncryptionKeyType.MULTI_PARTY_HYBRID_EVEN_KEYSPLIT.name());
    // LINT.ThenChange(/java/com/google/scp/coordinator/keymanagement/keystorage/converters/EncryptionKeyConverter.java)

    if (publicKeysetHandle.isPresent()) {
      unsignedEncryptionKey
          .setPublicKey(toJsonCleartext(publicKeysetHandle.get()))
          .setPublicKeyMaterial(PublicKeyConversionUtil.getPublicKey(publicKeysetHandle.get()));
    }
    if (validityInDays > 0) {
      unsignedEncryptionKey.setExpirationTime(
          activation.plus(validityInDays, ChronoUnit.DAYS).plus(KEY_REFRESH_WINDOW).toEpochMilli());
    }
    if (ttlInDays > 0) {
      unsignedEncryptionKey.setTtlTime(
          activation.plus(ttlInDays, ChronoUnit.DAYS).getEpochSecond());
    }

    try {
      return KeySplitDataUtil.addKeySplitData(
          unsignedEncryptionKey.build(),
          keyEncryptionKeyUri,
          migrationkeyEncryptionKeyUri,
          signatureKey);
    } catch (GeneralSecurityException e) {
      String msg = "Error generating public key signature";
      throw new ServiceException(Code.INTERNAL, "CRYPTO_ERROR", msg, e);
    }
  }

  /**
   * Combine two {@link KeySplitData} lists into a single list. If two {@link KeySplitData} have the
   * same {@code keySplitKeyEncryptionUri}, only keep one arbitrary one.
   */
  private static ImmutableList<KeySplitData> combineKeySplitData(
      List<KeySplitData> keySplitDataA, List<KeySplitData> keySplitDataB) {
    return ImmutableList.of(keySplitDataA, keySplitDataB).stream()
        .flatMap(List::stream)
        .collect(
            toImmutableMap(
                KeySplitData::getKeySplitKeyEncryptionKeyUri,
                keySplitDataItem -> keySplitDataItem,
                /* make an arbitrary choice */ (keySplitData1, keySplitData2) -> keySplitData1))
        .values()
        .stream()
        .collect(toImmutableList());
  }

  private static Optional<KeysetHandle> getPublicKeysetHandle(KeysetHandle keysetHandle) {
    try {
      return Optional.of(keysetHandle.getPublicKeysetHandle());
    } catch (GeneralSecurityException e) {
      return Optional.empty();
    }
  }

  private static KeysetHandle generateKeysetHandleWithJitter(KeyTemplate template)
      throws GeneralSecurityException {
    // Adding jitter to mitigate timing attack.
    try {
      Thread.sleep(new Random().nextInt(100));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    KeysetHandle keysetHandle = KeysetHandle.generateNew(template);
    // Adding jitter to mitigate timing attack.
    try {
      Thread.sleep(new Random().nextInt(100));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return keysetHandle;
  }
}
