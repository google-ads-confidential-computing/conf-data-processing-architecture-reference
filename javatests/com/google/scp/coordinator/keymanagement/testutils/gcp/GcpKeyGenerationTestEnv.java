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

package com.google.scp.coordinator.keymanagement.testutils.gcp;

import com.google.crypto.tink.KmsClient;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.kms.LocalKmsServerContainer;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.KeyGenerationArgs;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.KmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerCoordinatorKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerKmsAeadClient;
import com.google.scp.coordinator.keymanagement.testutils.FakeKmsClient;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.TestLocalKmsServerContainer;

public final class GcpKeyGenerationTestEnv extends AbstractModule {
  private final KeyGenerationArgs args;
  private final String kmsUrl;
  @Inject @TestLocalKmsServerContainer private LocalKmsServerContainer localKmsServerContainer;

  public GcpKeyGenerationTestEnv(KeyGenerationArgs args, String kmsUrl) {
    this.args = args;
    this.kmsUrl = kmsUrl;
  }

  @Override
  protected void configure() {
    bind(String.class)
        .annotatedWith(PeerCoordinatorKeyEncryptionKeyBaseUri.class)
        .toInstance(
            "gcp-kms://projects/admcloud-coordinator1/locations/us/keyRings/scp-test/cryptoKeys/$setName$-key-b");
    bind(String.class)
        .annotatedWith(KeyEncryptionKeyBaseUri.class)
        .toInstance(
            "gcp-kms://projects/admcloud-coordinator1/locations/us/keyRings/scp-test/cryptoKeys/$setName$-key");
    bind(KmsClient.class)
        .annotatedWith(KmsAeadClient.class)
        .toInstance(getKmsClient(args.getTestEncodedKeysetHandle().get()));
    bind(KmsClient.class)
        .annotatedWith(PeerKmsAeadClient.class)
        .toInstance(getKmsClient(args.getTestPeerCoordinatorEncodedKeysetHandle().get()));
  }

  private KmsClient getKmsClient(String encodedKeySetHandle) {
    return new FakeKmsClient(encodedKeySetHandle);
  }
}
