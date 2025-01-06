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

package com.google.scp.coordinator.keymanagement.keystorage.converters;

import com.google.common.collect.ImmutableList;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyTypeProto.EncryptionKeyType;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.KeyDataProto.KeyData;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import com.google.scp.shared.proto.ProtoUtil;
import java.util.Set;

/**
 * Convert {@link EncryptionKey} values between the API model and storage model, with respect to the
 * key storage API.
 */
public final class EncryptionKeyConverter {
  private static final String PRIVATE_KEY_RESOURCE_COLLECTION = "encryptionKeys/";

  private EncryptionKeyConverter() {}

  /**
   * Converts an API EncryptionKey into a key storage service EncryptionKey
   *
   * <p>Ignores any private key material included in the source encryptionKey -- expected to be
   * handled elsewhere.
   */
  public static com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto
          .EncryptionKey
      toStorageEncryptionKey(String keyId, EncryptionKey encryptionKey) {
    if (keyId == null || keyId.trim().isEmpty()) {
      throw new IllegalArgumentException("KeyId cannot be null or empty.");
    }

    // currently ignores keyMaterial
    ImmutableList<KeySplitData> keySplitData =
        ImmutableList.copyOf(
            encryptionKey.getKeyDataList().stream()
                .map(
                    keyData ->
                        KeySplitData.newBuilder()
                            .setKeySplitKeyEncryptionKeyUri(keyData.getKeyEncryptionKeyUri())
                            .setPublicKeySignature(keyData.getPublicKeySignature())
                            .build())
                .iterator());

    EncryptionKeyProto.EncryptionKey.Builder keyBuilder =
        EncryptionKeyProto.EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setSetName(encryptionKey.getSetName())
            .setPublicKey(encryptionKey.getPublicKeysetHandle())
            .setPublicKeyMaterial(encryptionKey.getPublicKeyMaterial())
            .setActivationTime(encryptionKey.getActivationTime())
            .setCreationTime(encryptionKey.getCreationTime())
            .addAllKeySplitData(keySplitData)
            .setKeyType(encryptionKey.getEncryptionKeyType().name());
    if (encryptionKey.hasTtlTime()) {
      keyBuilder.setTtlTime((long) encryptionKey.getTtlTime());
    }
    if (encryptionKey.hasExpirationTime()) {
      keyBuilder.setExpirationTime(encryptionKey.getExpirationTime());
    }
    return keyBuilder.build();
  }

  /**
   * Converts key storage service API encryption key to an API EncryptionKey with all blank {@code
   * keyMaterial} inside {@code keyData}, intended to be used by the request and response for
   * StoreKey.
   */
  public static EncryptionKey toApiEncryptionKey(
      com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto
              .EncryptionKey
          encryptionKey) {
    String name = PRIVATE_KEY_RESOURCE_COLLECTION + encryptionKey.getKeyId();

    // Does not populate keyMaterial
    ImmutableList<KeyData> keyData =
        encryptionKey.getKeySplitDataList().stream()
            .map(
                keySplitData ->
                    KeyData.newBuilder()
                        .setPublicKeySignature(keySplitData.getPublicKeySignature())
                        .setKeyEncryptionKeyUri(keySplitData.getKeySplitKeyEncryptionKeyUri())
                        .build())
            .collect(ImmutableList.toImmutableList());

    if (encryptionKey.getTtlTime() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "encryptionKey.ttlTime() cannot be safely downcasted to 32-bits ");
    }
    var encryptionKeyBuilder =
        EncryptionKey.newBuilder()
            .setName(name)
            .setSetName(encryptionKey.getSetName())
            .setPublicKeysetHandle(encryptionKey.getPublicKey())
            .setPublicKeyMaterial(encryptionKey.getPublicKeyMaterial())
            .setActivationTime(encryptionKey.getActivationTime())
            .setCreationTime(encryptionKey.getCreationTime())
            .addAllKeyData(keyData);

    if (encryptionKey.hasExpirationTime()) {
      encryptionKeyBuilder.setExpirationTime(encryptionKey.getExpirationTime());
    }
    if (encryptionKey.hasTtlTime()) {
      encryptionKeyBuilder.setTtlTime(Long.valueOf(encryptionKey.getTtlTime()).intValue());
    }

    Set<String> keyTypeValues = ProtoUtil.getValidEnumValues(EncryptionKeyType.class);
    if (keyTypeValues.contains(encryptionKey.getKeyType())) {
      encryptionKeyBuilder.setEncryptionKeyType(
          EncryptionKeyType.valueOf(encryptionKey.getKeyType()));
    } else if (encryptionKey.getKeyType().isEmpty()) {
      // Default to single key if no key type specified.
      encryptionKeyBuilder.setEncryptionKeyType(EncryptionKeyType.SINGLE_PARTY_HYBRID_KEY);
    } else {
      throw new IllegalArgumentException("Unrecognized KeyType: " + encryptionKey.getKeyType());
    }

    return encryptionKeyBuilder.build();
  }
}
