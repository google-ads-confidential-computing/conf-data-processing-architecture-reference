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

package com.google.scp.shared.util;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KeysetWriter;
import com.google.crypto.tink.proto.EncryptedKeyset;
import com.google.crypto.tink.proto.HpkePublicKey;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.proto.Keyset;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Tink util class for converting between a KeysetHandle and the underlying raw public key material.
 */
public final class PublicKeyConversionUtil {

  private PublicKeyConversionUtil() {}

  /**
   * Returns a base64-encoded string representation of the raw public key belonging to the provided
   * public key KeysetHandle.
   *
   * @deprecated Direct handling of raw key materials is discouraged, consider Tink alternatives.
   *     <p>Used for transforming a KeysetHandle into a format that can be used without Tink (e.g.
   *     to be used by web browsers).
   * @param keysetHandle a KeysetHandle representing a single asymmetric public key, throwwing if a
   *     different type of key is encountered.
   */
  @Deprecated
  public static String getPublicKey(KeysetHandle keysetHandle) {
    var writer = new PublicKeyExtractor();
    try {
      CleartextKeysetHandle.write(keysetHandle, writer);
      return getRawKeyFromSerializedKey(writer.getSerializedKey());
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Failed to write keysethandle", e);
    }
  }

  /**
   * Returns the base64-encoded raw public key from the provided serialized {@link HpkePublicKey}
   *
   * <p>This removes the Hpke parameters that are part of the HpkePublicKey which are necessary for
   * encryption using this key.
   *
   * @throws GeneralSecurityException if the HpkeParams of the provided key do not match the
   *     expected params.
   */
  private static String getRawKeyFromSerializedKey(ByteString serializedKey)
      throws InvalidProtocolBufferException, GeneralSecurityException {
    var deserializedKey = HpkePublicKey.parseFrom(serializedKey);
    var publicKey = deserializedKey.getPublicKey().toByteArray();

    return new String(Base64.getEncoder().encode(publicKey));
  }

  /**
   * Captures serialized public key belonging to a single Tink {@link KeysetHandle}.
   *
   * <p>Implemented as a KeysetWriter because {@link
   * CleartextKeysetHandle#write(KeysetHandle,KeysetWriter)} appears to be the only public way of
   * accessing the underlying Keyset proto.
   *
   * <p>While this is somewhat convoluted, there appears to be no better way currently to extract
   * the embedded public key. See below:
   *
   * <ul>
   *   <li>https://github.com/google/tink/issues/308 - suggests implementing KeysetReader
   *   <li>https://github.com/google/tink/issues/124#issuecomment-965906732 - someone asking if
   *       there is a better way than manually parsing the JSON
   * </ul>
   */
  private static class PublicKeyExtractor implements KeysetWriter {
    private ByteString serializedKey;

    @Override
    public void write(Keyset keyset) {
      if (keyset.getKeyCount() != 1) {
        throw new IllegalArgumentException(
            String.format("Unexpected number of keys, got %d, expected 1", keyset.getKeyCount()));
      }
      var keyData = keyset.getKey(0).getKeyData();
      if (keyData.getKeyMaterialType() != KeyMaterialType.ASYMMETRIC_PUBLIC) {
        throw new IllegalArgumentException("Unexpected non-public key");
      }
      serializedKey = keyData.getValue();
    }

    @Override
    public void write(EncryptedKeyset keyset) throws IOException {
      throw new IOException("PublicKeyExtractor does not support encrypted keysets");
    }

    /**
     * Returns the key value. This key value is a serialized Tink key proto which must be
     * deserialized into the specific Tink public key proto.
     */
    public ByteString getSerializedKey() {
      return serializedKey;
    }
  }
}
