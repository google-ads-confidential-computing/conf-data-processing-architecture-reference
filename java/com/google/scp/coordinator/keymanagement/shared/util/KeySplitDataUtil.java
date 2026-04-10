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

package com.google.scp.coordinator.keymanagement.shared.util;

import com.google.common.base.Joiner;
import com.google.crypto.tink.PublicKeySign;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Utility methods for signing public key material for an encryption key and assigning them to new
 * KeySplitData
 */
public final class KeySplitDataUtil {
  private KeySplitDataUtil() {}

  /**
   * Creates the {@link KeySplitData} containing the specified encryption key uri and signature if
   * signature key is provided.
   */
  public static KeySplitData buildKeySplitData(
      EncryptionKey encryptionKey, String keyEncryptionKeyUri, Optional<PublicKeySign> signatureKey)
      throws GeneralSecurityException {
    KeySplitData.Builder keySplitData =
        KeySplitData.newBuilder().setKeySplitKeyEncryptionKeyUri(keyEncryptionKeyUri);
    if (signatureKey.isPresent()) {
      keySplitData.setPublicKeySignature(
          generateEncryptionKeySignature(encryptionKey, signatureKey.get()));
    }
    return keySplitData.build();
  }

  /**
   * The signature of an EncryptionKey material in ${key_id}:${creation_time}:${public_key_material}
   * format.
   */
  private static String generateEncryptionKeySignature(
      EncryptionKey encryptionKey, PublicKeySign signatureKey) throws GeneralSecurityException {
    return new String(
        Base64.getEncoder()
            .encodeToString(
                signatureKey.sign(
                    encryptionKeySignatureMessage(encryptionKey)
                        .getBytes(StandardCharsets.UTF_8))));
  }

  /**
   * The signature of an EncryptionKey material in ${key_id}|${iso 8601
   * creation_time}|${public_key_material} format.
   */
  private static String encryptionKeySignatureMessage(EncryptionKey encryptionKey) {
    // ${key_id}|${iso 8601 creation_time}|${public_key_material}
    Instant creationTime = Instant.ofEpochMilli(encryptionKey.getCreationTime());
    return Joiner.on("|")
        .join(
            encryptionKey.getKeyId(),
            creationTime.toString(),
            encryptionKey.getPublicKeyMaterial());
  }
}
