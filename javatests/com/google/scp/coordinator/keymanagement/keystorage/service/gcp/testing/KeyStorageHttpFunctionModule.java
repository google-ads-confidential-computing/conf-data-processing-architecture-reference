/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.scp.coordinator.keymanagement.keystorage.service.gcp.testing;

import static com.google.kms.LocalKmsConstants.DEFAULT_KEY_URI;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KmsClient;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.kms.LocalGcpKmsClient;
import com.google.kms.LocalKmsServerContainer;
import com.google.protobuf.ByteString;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.CacheControlMaximum;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeyLimit;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.CoordinatorKekUri;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.CoordinatorKeyAead;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.DisableKeySetAcl;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.KmsAeadClient;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.KmsKeyAead;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.KmsKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.KmsKeyEncryptionKeyUri;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.MigrationKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.MigrationKmsKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.PopulateMigrationKeyData;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.CreateKeyTask;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.SignDataKeyTask;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.gcp.GcpCreateKeyTask;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.gcp.GcpSignDataKeyTask;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbConfig;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbModule;
import com.google.scp.coordinator.keymanagement.testutils.FakeKmsClient;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.TestLocalKmsServerContainer;
import com.google.scp.coordinator.keymanagement.testutils.gcp.SpannerKeyDbTestUtil;
import com.google.scp.shared.util.KeysetHandleSerializerUtil;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Module for configuring settings inside a local key storage cloud function emulator. This will be
 * used for integration tests only.
 */
public class KeyStorageHttpFunctionModule extends AbstractModule {

  private final String keysetHandleStringName;

  public static final String KMS_ENDPOINT_ENV_VAR_NAME = "KMS_ENDPOINT";
  @Inject @TestLocalKmsServerContainer private LocalKmsServerContainer localKmsServerContainer;

  KeyStorageHttpFunctionModule(String keysetHandleStringName) {
    this.keysetHandleStringName = keysetHandleStringName;
  }

  @Override
  protected void configure() {
    bind(Integer.class).annotatedWith(KeyLimit.class).toInstance(5);
    bind(Long.class).annotatedWith(CacheControlMaximum.class).toInstance(604800L);
    bind(SpannerKeyDbConfig.class).toInstance(SpannerKeyDbTestUtil.getSpannerKeyDbConfig());
    bind(CreateKeyTask.class).to(GcpCreateKeyTask.class);
    bind(SignDataKeyTask.class).to(GcpSignDataKeyTask.class);
    // Note: This is currently unused.
    bind(String.class)
        .annotatedWith(KmsKeyEncryptionKeyUri.class)
        .toInstance("inline-kms://unused_so_far");
    bind(String.class).annotatedWith(CoordinatorKekUri.class).toInstance("");
    bind(String.class).annotatedWith(DisableKeySetAcl.class).toInstance("false");
    bind(String.class).annotatedWith(PopulateMigrationKeyData.class).toInstance("false");
    bind(String.class)
        .annotatedWith(KmsKeyEncryptionKeyBaseUri.class)
        .toInstance(
            "gcp-kms://projects/admcloud-coordinator1/locations/us/keyRings/scp-test/cryptoKeys/$setName$-key-b");
    bind(String.class).annotatedWith(MigrationKmsKeyEncryptionKeyBaseUri.class).toInstance("");
    bind(KmsClient.class).annotatedWith(KmsAeadClient.class).toInstance(getKmsClient());
    bind(KmsClient.class)
        .annotatedWith(MigrationKmsAeadClient.class)
        .toInstance(getMigrationKmsClient());
    // This is not used for GCP but is needed for binding purposes.
    bind(Aead.class).annotatedWith(CoordinatorKeyAead.class).toInstance(getAead());
    install(new SpannerKeyDbModule());
  }

  @Provides
  @Singleton
  @KmsKeyAead
  public Aead providesKmsKeyAead() {
    return getAead();
  }

  /**
   * This returns the Aead that will do the decryption/encryption based on the KeysetHandle
   * represented by the encoded string in the system's environment variable. i.e. The KeysetHandle
   * may do the encryption/decryption inline using a tink generated key rather than going through
   * KMS. Use Local kms service in KeyStorageService test if localKmsServiceStringName is set
   */
  private Aead getAead() {
    String kmsEndpoint = System.getenv(KMS_ENDPOINT_ENV_VAR_NAME);
    if (kmsEndpoint != null) {
      LocalGcpKmsClient client = new LocalGcpKmsClient(kmsEndpoint);
      try {
        return client.withoutCredentials().getAead(DEFAULT_KEY_URI);
      } catch (GeneralSecurityException e) {
        throw new RuntimeException(e);
      }
    }
    String encodedString = System.getenv(keysetHandleStringName);
    ByteString keysetHandleByteString =
        ByteString.copyFrom(Base64.getDecoder().decode(encodedString));
    try {
      return KeysetHandleSerializerUtil.fromBinaryCleartext(keysetHandleByteString)
          .getPrimitive(Aead.class);
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private KmsClient getKmsClient() {
    String encodedKeySetHandle = System.getenv(keysetHandleStringName);
    if (encodedKeySetHandle != null) {
      return new FakeKmsClient(encodedKeySetHandle);
    }
    String kmsEndpoint = System.getenv(KMS_ENDPOINT_ENV_VAR_NAME);
    if (kmsEndpoint != null) {
      LocalGcpKmsClient client = new LocalGcpKmsClient(kmsEndpoint);
      try {
        client.withoutCredentials();
        return client;
      } catch (GeneralSecurityException e) {
        throw new RuntimeException(e);
      }
    }
    return new FakeKmsClient();
  }

  private KmsClient getMigrationKmsClient() {
    String encodedKeySetHandle = System.getenv(keysetHandleStringName);
    if (encodedKeySetHandle != null) {
      return new FakeKmsClient(encodedKeySetHandle);
    }
    String kmsEndpoint = System.getenv(KMS_ENDPOINT_ENV_VAR_NAME);
    if (kmsEndpoint != null) {
      LocalGcpKmsClient client = new LocalGcpKmsClient(kmsEndpoint);
      try {
        client.withoutCredentials();
        return client;
      } catch (GeneralSecurityException e) {
        throw new RuntimeException(e);
      }
    }
    return new FakeKmsClient();
  }
}
