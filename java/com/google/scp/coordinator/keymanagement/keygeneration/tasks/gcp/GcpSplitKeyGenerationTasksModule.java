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

package com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp;

import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.KMS_KEY_BASE_URI;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.KMS_KEY_URI;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.MIGRATION_KMS_KEY_BASE_URI;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.MIGRATION_PEER_COORDINATOR_KMS_KEY_BASE_URI;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.PEER_COORDINATOR_KMS_KEY_BASE_URI;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.PEER_COORDINATOR_KMS_KEY_URI;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.crypto.tink.KmsClient;
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.coordinator.clients.configclient.gcp.Annotations.PeerCoordinatorCredentials;
import com.google.scp.coordinator.clients.configclient.gcp.GcpCoordinatorClientConfig;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.DisableKeySetAcl;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.KmsKeyUri;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.PeerCoordinatorKmsKeyBaseUriArg;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.PeerCoordinatorServiceAccount;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.PeerCoordinatorWipProvider;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.MigrationKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.CreateSplitKeyTask;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.KmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationPeerCoordinatorKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.MigrationPeerKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerCoordinatorKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.Annotations.PeerKmsAeadClient;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.ParameterClient.ParameterClientException;
import java.security.GeneralSecurityException;

/** Module for Business Layer bindings used for KeyGeneration. Handles multiparty key generation. */
public final class GcpSplitKeyGenerationTasksModule extends AbstractModule {

  public GcpSplitKeyGenerationTasksModule() {}

  /** Provides the kmsKeyUri for this coordinator from either args or parameter client. */
  @Provides
  @Singleton
  @KeyEncryptionKeyBaseUri
  String provideKeyEncryptionKeyBaseUri(
      ParameterClient parameterClient,
      @KmsKeyUri String kmsKeyUri,
      @DisableKeySetAcl boolean disableKeySetAcl)
      throws ParameterClientException {
    // TODO: b/428770204 - Remove option of KMS_KEY_URI and disableKeySetAcl post migration.
    String kmsParam = disableKeySetAcl ? KMS_KEY_URI : KMS_KEY_BASE_URI;
    return parameterClient.getParameter(kmsParam).orElse(kmsKeyUri);
  }

  /** Provides the migrationKmsKeyUri for this coordinator from parameter client. */
  @Provides
  @Singleton
  @MigrationKeyEncryptionKeyBaseUri
  String provideMigrationKeyEncryptionKeyBaseUri(ParameterClient parameterClient)
      throws ParameterClientException {
    return parameterClient.getParameter(MIGRATION_KMS_KEY_BASE_URI).orElse("");
  }

  @Provides
  @Singleton
  @PeerCoordinatorKeyEncryptionKeyBaseUri
  String providesPeerCoordinatorKeyEncryptionKeyBaseUri(
      ParameterClient parameterClient,
      @PeerCoordinatorKmsKeyBaseUriArg String peerCoordinatorKmsKeyBaseUriArg,
      @DisableKeySetAcl boolean disableKeySetAcl)
      throws ParameterClientException {
    // TODO: b/428770204 - Remove option of PEER_COORDINATOR_KMS_KEY_URI and disableKeySetAcl post
    // migration.
    String kmsParam =
        disableKeySetAcl ? PEER_COORDINATOR_KMS_KEY_URI : PEER_COORDINATOR_KMS_KEY_BASE_URI;
    return parameterClient.getParameter(kmsParam).orElse(peerCoordinatorKmsKeyBaseUriArg);
  }

  @Provides
  @Singleton
  @MigrationPeerCoordinatorKeyEncryptionKeyBaseUri
  String providesMigrationPeerCoordinatorKeyEncryptionKeyBaseUri(ParameterClient parameterClient)
      throws ParameterClientException {
    return parameterClient.getParameter(MIGRATION_PEER_COORDINATOR_KMS_KEY_BASE_URI).orElse("");
  }

  @Provides
  @Singleton
  GcpCoordinatorClientConfig providesGcpCoordinatorClientConfig(
      @PeerCoordinatorWipProvider String peerCoordinatorWipProvider,
      @PeerCoordinatorServiceAccount String peerCoordinatorServiceAccount) {
    GcpCoordinatorClientConfig.Builder configBuilder =
        GcpCoordinatorClientConfig.builder()
            .setPeerCoordinatorServiceAccount(peerCoordinatorServiceAccount)
            .setPeerCoordinatorWipProvider(peerCoordinatorWipProvider)
            .setUseLocalCredentials(peerCoordinatorWipProvider.isEmpty());
    return configBuilder.build();
  }

  @Provides
  @Singleton
  @KmsAeadClient
  KmsClient provideKmsAeadClient() throws GeneralSecurityException {
    GcpKmsClient client = new GcpKmsClient();
    client.withDefaultCredentials();
    return client;
  }

  @Provides
  @Singleton
  @MigrationKmsAeadClient
  KmsClient provideMigrationKmsAeadClient() throws GeneralSecurityException {
    GcpKmsClient client = new GcpKmsClient();
    client.withDefaultCredentials();
    return client;
  }

  @Provides
  @Singleton
  @PeerKmsAeadClient
  KmsClient providePeerKmsAeadClient(@PeerCoordinatorCredentials GoogleCredentials credentials)
      throws GeneralSecurityException {
    GcpKmsClient client = new GcpKmsClient();
    client.withCredentials(credentials);
    return client;
  }

  @Provides
  @Singleton
  @MigrationPeerKmsAeadClient
  KmsClient provideMigrationPeerKmsAeadClient(
      @PeerCoordinatorCredentials GoogleCredentials credentials) throws GeneralSecurityException {
    GcpKmsClient client = new GcpKmsClient();
    client.withCredentials(credentials);
    return client;
  }

  @Override
  protected void configure() {
    bind(CreateSplitKeyTask.class).to(GcpCreateSplitKeyTask.class);
  }
}
