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
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.CreateKeyTask;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.DataKeyProto.DataKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import java.security.GeneralSecurityException;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates a key in the database */
public final class GcpCreateKeyTask implements CreateKeyTask {
  private static final Logger logger = LoggerFactory.getLogger(CreateKeyTask.class);

  private final KeyDb keyDb;
  private final String kmsKeyEncryptionKeyBaseUri;
  private final KmsClient kmsClient;

  @Inject
  public GcpCreateKeyTask(
      KeyDb keyDb,
      @KmsAeadClient KmsClient kmsClient,
      @KmsKeyEncryptionKeyBaseUri String kmsKeyEncryptionKeyBaseUri) {
    this.keyDb = keyDb;
    this.kmsClient = kmsClient;
    this.kmsKeyEncryptionKeyBaseUri = kmsKeyEncryptionKeyBaseUri;
  }

  /** Creates the key in the database */
  @Override
  public EncryptionKey createKey(EncryptionKey encryptionKey, String encryptedKeySplit)
      throws ServiceException {
    if (encryptedKeySplit.isEmpty()) {
      throw new ServiceException(
          Code.INVALID_ARGUMENT, SERVICE_ERROR.name(), "Key payload is empty.");
    }
    String kmsKeyUri = getKmsKeyEncryptionKeyUri(encryptionKey.getSetName());
    String validatedKeySplit =
        validatedPrivateKeySplit(
            kmsKeyUri, encryptedKeySplit, encryptionKey.getPublicKeyMaterial());

    var newKeySplitData =
        ImmutableList.<KeySplitData>builder()
            .addAll(encryptionKey.getKeySplitDataList())
            .add(KeySplitData.newBuilder().setKeySplitKeyEncryptionKeyUri(kmsKeyUri).build());
    var newEncryptionKey =
        encryptionKey.toBuilder()
            .setJsonEncodedKeyset(validatedKeySplit)
            .setKeyEncryptionKeyUri(kmsKeyUri)
            // Need to clear before adding, otherwise there will be duplicate KeySplitData elements.
            .clearKeySplitData()
            .addAllKeySplitData(newKeySplitData.build())
            .build();

    keyDb.createKey(newEncryptionKey);
    // TODO(b/206030473): Figure out where exactly signing should happen.
    return newEncryptionKey;
  }

  @Override
  public EncryptionKey createKey(
      EncryptionKey encryptionKey, DataKey dataKey, String decryptedKeySplit)
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
      byte[] cipherText = Base64.getDecoder().decode(privateKeySplit);
      byte[] associatedData = Base64.getDecoder().decode(publicKeyMaterial);
      byte[] plainText = kmsAead.decrypt(cipherText, associatedData);
      return Base64.getEncoder().encodeToString(kmsAead.encrypt(plainText, new byte[0]));
    } catch (NullPointerException | GeneralSecurityException ex) {
      throw new ServiceException(
          INVALID_ARGUMENT, SERVICE_ERROR.name(), "Key-split validation failed.", ex);
    }
  }

  private String getKmsKeyEncryptionKeyUri(String setName) {
    return this.kmsKeyEncryptionKeyBaseUri.replace("$setName$", setName);
  }
}
