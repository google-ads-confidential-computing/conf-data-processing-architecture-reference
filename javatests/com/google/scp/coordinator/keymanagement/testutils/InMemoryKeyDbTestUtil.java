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

package com.google.scp.coordinator.keymanagement.testutils;

import static com.google.scp.shared.util.KeysetHandleSerializerUtil.toJsonCleartext;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.UUID.randomUUID;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.EncodedPublicKeyProto.EncodedPublicKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyStatusProto.EncryptionKeyStatus;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.util.KeyParams;
import com.google.scp.shared.util.PublicKeyConversionUtil;
import java.security.GeneralSecurityException;
import java.util.stream.IntStream;

public final class InMemoryKeyDbTestUtil {

  public static final Long CACHE_CONTROL_MAX = 604800L;
  public static final Integer KEY_LIMIT = 5;

  static {
    try {
      HybridConfig.register();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Failed to register hybrid config for Tink.");
    }
  }

  private InMemoryKeyDbTestUtil() {}

  /** Adds keyCount-number of keys to InMemoryDB */
  public static void addRandomKeysToKeyDb(int keyCount, String setName, InMemoryKeyDb keyDb) {
    IntStream.range(0, keyCount).forEach(unused -> createRandomKey(setName, keyDb));
  }

  /**
   * Puts one Key item with random values to InMemoryKeyDb instance. Returns randomly-generated
   * EncryptionKey
   */
  private static void createRandomKey(String setName, InMemoryKeyDb keyDb) {
    try {
      KeysetHandle key = KeysetHandle.generateNew(KeyParams.getDefaultKeyTemplate());
      var publicKey = toJsonCleartext(key.getPublicKeysetHandle());
      var publicKeyMaterial = PublicKeyConversionUtil.getPublicKey(key.getPublicKeysetHandle());
      createKey(keyDb, setName, publicKey, publicKeyMaterial);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Puts one Key item with parameter values to InMemoryKeyDb instance. Returns EncryptionKey if
   * successful
   */
  private static void createKey(
      InMemoryKeyDb keyDb, String setName, String publicKey, String publicKeyMaterial) {
    try {
      var keyId = randomUUID().toString();
      EncryptionKey.Builder encryptionKey =
          EncryptionKey.newBuilder()
              .setSetName(setName)
              .setKeyId(keyId)
              .setStatus(EncryptionKeyStatus.ACTIVE)
              .setJsonEncodedKeyset(randomUUID().toString())
              .setPublicKey(publicKey)
              .setPublicKeyMaterial(publicKeyMaterial)
              .setKeyEncryptionKeyUri(randomUUID().toString())
              .setCreationTime(now().toEpochMilli())
              .setExpirationTime(now().plus(7, DAYS).toEpochMilli());
      keyDb.createKey(encryptionKey.build());
      keyDb.getKey(keyId);
    } catch (ServiceException e) {
      throw new IllegalArgumentException("Could not create key", e);
    }
  }

  /** Builds {@link EncodedPublicKey} given its properties */
  public static EncodedPublicKey expectedEncodedPublicKey(String keyId) {
    return EncodedPublicKey.newBuilder().setId(keyId).build();
  }

  /** Builds {@link EncodedPublicKey} representation of {@link EncryptionKey} */
  public static EncodedPublicKey expectedEncodedPublicKey(EncryptionKey encryptionKey) {
    return EncodedPublicKey.newBuilder().setId(encryptionKey.getKeyId()).build();
  }

  public static String expectedErrorResponseBody(int code, String message, String reason) {
    return String.format(
        """
        {
          "code": %d,
          "message": "%s",
          "details": [{
            "reason": "%s",
            "domain": "",
            "metadata": {
            }
          }]
        }\
        """,
        code, message, reason);
  }
}
