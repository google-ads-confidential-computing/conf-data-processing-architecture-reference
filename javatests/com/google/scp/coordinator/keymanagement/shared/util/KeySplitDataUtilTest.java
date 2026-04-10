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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
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
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KeySplitDataUtilTest {
  private static final String SET_NAME = "test-set-name";

  @Before
  public void setUp() throws GeneralSecurityException {
    SignatureConfig.register();
  }

  /** Tests format of added keysplit data */
  @Test
  public void buildKeySplitData_correctKeySplitData() throws GeneralSecurityException {
    EncryptionKey baseKey =
        FakeEncryptionKey.createEncryptionKeyBuilder(SET_NAME).clearKeySplitData().build();
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
    EncryptionKey baseKey =
        FakeEncryptionKey.createEncryptionKeyBuilder(SET_NAME).clearKeySplitData().build();

    KeySplitData keySplitData =
        KeySplitDataUtil.buildKeySplitData(baseKey, "kek-uri", Optional.empty());

    assertThat(keySplitData.getKeySplitKeyEncryptionKeyUri()).isEqualTo("kek-uri");
    assertThat(keySplitData.getPublicKeySignature()).isEmpty();
  }
}
