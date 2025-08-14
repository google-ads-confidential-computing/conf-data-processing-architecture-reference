/*
 * Copyright 2025 Google LLC
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

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KmsClient;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import com.google.crypto.tink.subtle.Hkdf;
import com.google.protobuf.ByteString;
import com.google.scp.shared.util.KeysetHandleSerializerUtil;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Optional;

public class FakeKmsClient implements KmsClient {
  HashMap<String, Aead> aeadMap;
  Optional<String> encodedKeySetHandle;

  public FakeKmsClient() {
    aeadMap = new HashMap<>();
    this.encodedKeySetHandle = Optional.empty();
  }

  public FakeKmsClient(String encodedKeySetHandle) {
    aeadMap = new HashMap<>();
    this.encodedKeySetHandle = Optional.of(encodedKeySetHandle);
  }

  @Override
  public boolean doesSupport(String keyUri) {
    return true;
  }

  @Override
  public KmsClient withCredentials(String credentialPath) {
    return this;
  }

  @Override
  public KmsClient withDefaultCredentials() {
    return this;
  }

  @Override
  public Aead getAead(String keyUri) throws GeneralSecurityException {
    if (encodedKeySetHandle.isPresent()) {
      ByteString keysetHandleByteString =
          ByteString.copyFrom(Base64.getDecoder().decode(encodedKeySetHandle.get()));
      try {
        return KeysetHandleSerializerUtil.fromBinaryCleartext(keysetHandleByteString)
            .getPrimitive(Aead.class);
      } catch (RuntimeException | IOException e) {
        throw new GeneralSecurityException(e);
      }
    }
    if (!aeadMap.containsKey(keyUri)) {
      AeadConfig.register();

      byte[] fixedSalt = "test-aead".getBytes();

      // Derive a 256-bit (32-byte) key using HKDF with fixed text
      byte[] keyBytes = Hkdf.computeHkdf("HmacSHA256", keyUri.getBytes(), fixedSalt, null, 32);

      // Use this lower level class that will work with raw key. Test-only.
      aeadMap.put(keyUri, new AesGcmJce(keyBytes));
    }
    return aeadMap.get(keyUri);
  }
}
