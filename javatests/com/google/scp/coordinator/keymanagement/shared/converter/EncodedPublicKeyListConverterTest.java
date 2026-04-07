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

package com.google.scp.coordinator.keymanagement.shared.converter;

import static com.google.common.truth.Truth.assertThat;
import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.crypto.tink.BinaryKeysetReader;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.subtle.Base64;
import com.google.scp.coordinator.keymanagement.shared.converter.EncodedPublicKeyListConverter.Mode;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.EncodedPublicKeyProto.EncodedPublicKey;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.EncodedPublicKeyProto.EncodedPublicKey.KeyOneofCase;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import org.junit.Test;

public class EncodedPublicKeyListConverterTest {

  @Test
  public void reverse_failure() {
    EncodedPublicKey publicKey =
        EncodedPublicKey.newBuilder().setId(randomUUID().toString()).build();
    var converter = new EncodedPublicKeyListConverter(Mode.TINK);

    assertThrows(
        UnsupportedOperationException.class,
        () -> converter.reverse().convert(ImmutableList.of(publicKey)));
  }

  @Test
  public void testConvert_tinkMode_returnsExpectedFormat() throws Exception {
    // Given
    EncryptionKey key = FakeEncryptionKey.createEncryptionKey();

    // When
    var converter = new EncodedPublicKeyListConverter(Mode.TINK);
    EncodedPublicKey encodedKey = converter.convert(ImmutableList.of(key)).get(0);

    // Then
    assertThat(encodedKey.getId()).isEqualTo(key.getKeyId());
    CleartextKeysetHandle.read(
        BinaryKeysetReader.withBytes(Base64.decode(encodedKey.getTinkBinary())));
  }

  @Test
  public void testConvert_rawMode_returnsExpectedFormat() {
    // Given
    EncryptionKey key = FakeEncryptionKey.createEncryptionKey();

    // When
    var converter = new EncodedPublicKeyListConverter(Mode.RAW);
    EncodedPublicKey encodedKey = converter.convert(ImmutableList.of(key)).get(0);

    // Then
    assertThat(encodedKey.getId()).isEqualTo(key.getKeyId());
    assertThat(encodedKey.getKeyOneofCase()).isEqualTo(KeyOneofCase.HPKE_PUBLIC_KEY);
    assertThat(Base64.encode(encodedKey.getHpkePublicKey().getPublicKey().toByteArray()))
        .isEqualTo(key.getPublicKeyMaterial());
  }
}
