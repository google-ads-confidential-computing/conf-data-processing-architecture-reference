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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.signature.EcdsaSignKeyManager;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KeySplitDataUtilTest {

  @Before
  public void setUp() throws GeneralSecurityException {
    SignatureConfig.register();
  }

  /** Tests format of added keysplit data */
  @Test
  public void buildKeySplitData_correctKeySplitData() throws GeneralSecurityException {
    EncryptionKey baseKey = FakeEncryptionKey.create().toBuilder().clearKeySplitData().build();
    KeysetHandle keysetHandle = KeysetHandle.generateNew(EcdsaSignKeyManager.ecdsaP256Template());
    PublicKeySign publicKeySign = keysetHandle.getPrimitive(PublicKeySign.class);
    PublicKeyVerify publicKeyVerify =
        keysetHandle.getPublicKeysetHandle().getPrimitive(PublicKeyVerify.class);

    KeySplitData keySplitData =
        KeySplitDataUtil.buildKeySplitData(baseKey, "kek-uri", Optional.of(publicKeySign));
    assertThat(keySplitData.getKeySplitKeyEncryptionKeyUri()).isEqualTo("kek-uri");

    // ${key_id}|${iso 8601 creation_time}|${public_key_material}
    Instant creationTime = Instant.ofEpochMilli(baseKey.getCreationTime());
    byte[] expectedMessage =
        Joiner.on("|")
            .join(baseKey.getKeyId(), creationTime.toString(), baseKey.getPublicKeyMaterial())
            .getBytes(StandardCharsets.UTF_8);
    // should throw if key fails to verify
    publicKeyVerify.verify(
        Base64.getDecoder().decode(keySplitData.getPublicKeySignature()), expectedMessage);
  }

  /** Tests format of added keysplit data if there is no signature */
  @Test
  public void buildKeySplitData_correctNoSignature() throws GeneralSecurityException {
    EncryptionKey baseKey = FakeEncryptionKey.create().toBuilder().clearKeySplitData().build();

    KeySplitData keySplitData =
        KeySplitDataUtil.buildKeySplitData(baseKey, "kek-uri", Optional.empty());

    assertThat(keySplitData.getKeySplitKeyEncryptionKeyUri()).isEqualTo("kek-uri");
    assertThat(keySplitData.getPublicKeySignature()).isEmpty();
  }

  /** Tests that the signatures added can be verified */
  @Test
  public void buildKeySplitData_roundtrip() throws GeneralSecurityException {
    EncryptionKey baseKey = FakeEncryptionKey.create().toBuilder().clearKeySplitData().build();
    ImmutableMap<String, KeysetHandle> keysetHandles =
        Stream.generate(KeySplitDataUtilTest::unsafeCreateKeysetHandle)
            .limit(10)
            .collect(
                toImmutableMap(
                    keysetHandle -> UUID.randomUUID().toString(), keysetHandle -> keysetHandle));
    ImmutableMap<String, PublicKeyVerify> publicKeyVerifiers =
        keysetHandles.entrySet().stream()
            .collect(
                toImmutableMap(
                    Map.Entry::<String, KeysetHandle>getKey,
                    entry -> getPublicKeyVerify(entry.getValue())));

    EncryptionKey.Builder finalKey = baseKey.toBuilder();
    for (Map.Entry<String, KeysetHandle> keysetHandle : keysetHandles.entrySet()) {
      PublicKeySign publicKeySign = keysetHandle.getValue().getPrimitive(PublicKeySign.class);
      finalKey.addKeySplitData(
          KeySplitDataUtil.buildKeySplitData(
              baseKey, keysetHandle.getKey(), Optional.of(publicKeySign)));
    }

    // should throw if keys fail to verify
    KeySplitDataUtil.verifyEncryptionKeySignatures(finalKey.build(), publicKeyVerifiers);
  }

  /** Should throw if a signature is invalid */
  @Test
  public void verifyEncryptionKeySignatures_throwIfInvalid() throws GeneralSecurityException {
    EncryptionKey baseKey = FakeEncryptionKey.create();
    KeysetHandle keysetHandle = KeysetHandle.generateNew(EcdsaSignKeyManager.ecdsaP256Template());
    PublicKeyVerify publicKeyVerify =
        keysetHandle.getPublicKeysetHandle().getPrimitive(PublicKeyVerify.class);

    EncryptionKey finalKey =
        baseKey.toBuilder()
            .addKeySplitData(KeySplitDataUtil.buildKeySplitData(baseKey, "blah", Optional.empty()))
            .build();

    GeneralSecurityException ex =
        assertThrows(
            GeneralSecurityException.class,
            () ->
                KeySplitDataUtil.verifyEncryptionKeySignatures(
                    finalKey, ImmutableMap.of("blah", publicKeyVerify)));
    assertThat(ex).hasMessageThat().contains("blah");
    assertThat(ex).hasMessageThat().contains("failed to verify");
  }

  /** Should throw if a signature is not found */
  @Test
  public void verifyEncryptionKeySignatures_throwIfNotFound() throws GeneralSecurityException {
    EncryptionKey baseKey = FakeEncryptionKey.create();
    KeysetHandle keysetHandle = KeysetHandle.generateNew(EcdsaSignKeyManager.ecdsaP256Template());
    PublicKeyVerify publicKeyVerify =
        keysetHandle.getPublicKeysetHandle().getPrimitive(PublicKeyVerify.class);

    GeneralSecurityException ex =
        assertThrows(
            GeneralSecurityException.class,
            () ->
                KeySplitDataUtil.verifyEncryptionKeySignatures(
                    baseKey, ImmutableMap.of("blah", publicKeyVerify)));
    assertThat(ex).hasMessageThat().contains("blah");
    assertThat(ex).hasMessageThat().contains("not found");
  }

  /** Needed for testing with Stream.generate() */
  private static KeysetHandle unsafeCreateKeysetHandle() {
    try {
      return KeysetHandle.generateNew(EcdsaSignKeyManager.ecdsaP256Template());
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  /** Needed for testing with collect() */
  private static PublicKeyVerify getPublicKeyVerify(KeysetHandle keysetHandle) {
    try {
      return keysetHandle.getPublicKeysetHandle().getPrimitive(PublicKeyVerify.class);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }
}
