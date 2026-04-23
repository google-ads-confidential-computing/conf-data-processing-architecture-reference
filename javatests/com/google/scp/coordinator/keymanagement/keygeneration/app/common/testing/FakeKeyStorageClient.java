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

package com.google.scp.coordinator.keymanagement.keygeneration.app.common.testing;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.signature.EcdsaSignKeyManager;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import java.security.GeneralSecurityException;
import java.util.Optional;

/** Simple in-memory KeyStorageClient. */
public class FakeKeyStorageClient implements KeyStorageClient {

  public static final String KEK_URI = "aws-kms://arn:aws:kms:us-east-1:000000000000:key/b";
  public static final String MIGRATION_KEK_URI =
      "aws-kms://arn:aws:kms:us-east-1:000000000000:key/migration-b";

  private static final PublicKeySign PUBLIC_KEY_SIGN;
  public static final PublicKeyVerify PUBLIC_KEY_VERIFY;

  static {
    try {
      SignatureConfig.register();
      KeysetHandle signatureKey = KeysetHandle.generateNew(EcdsaSignKeyManager.ecdsaP256Template());
      PUBLIC_KEY_SIGN = signatureKey.getPrimitive(PublicKeySign.class);
      PUBLIC_KEY_VERIFY = signatureKey.getPublicKeysetHandle().getPrimitive(PublicKeyVerify.class);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Error initializing Fake Key Storage Client signature keys.");
    }
  }

  /**
   * Returns the passed in EncryptionKey with an additional KeySplitData
   *
   * <p>This method is safe to mock with {@code when(createKey(any(), any(), any())}.
   */
  @Override
  public EncryptionKey createKey(
      EncryptionKey encryptionKey,
      String encryptedKeySplit,
      Optional<String> migrationEncryptedKeySplit)
      throws KeyStorageServiceException {
    // Return early to prevent an exception being thrown when mocking. When invoked with generic
    // Mockito argument matchers for the purposes of stubbing, null is passed to this function.
    if (encryptionKey == null) {
      return null;
    }

    EncryptionKey.Builder signedKey = encryptionKey.toBuilder();
    signedKey.addKeySplitData(KeySplitData.newBuilder().setKeySplitKeyEncryptionKeyUri(KEK_URI));
    if (migrationEncryptedKeySplit.isPresent()) {
      signedKey.addMigrationKeySplitData(
          KeySplitData.newBuilder().setKeySplitKeyEncryptionKeyUri(MIGRATION_KEK_URI));
    }
    return signedKey.build();
  }
}
