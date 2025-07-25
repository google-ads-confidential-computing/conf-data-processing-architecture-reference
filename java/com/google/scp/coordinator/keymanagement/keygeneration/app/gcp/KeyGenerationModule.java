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

package com.google.scp.coordinator.keymanagement.keygeneration.app.gcp;

import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.DISABLE_KEY_SET_ACL;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.KEYS_VALIDITY_IN_DAYS;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.KEY_DB_NAME;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.KEY_ID_TYPE;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.KEY_STORAGE_SERVICE_BASE_URL;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.KEY_STORAGE_SERVICE_CLOUDFUNCTION_URL;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.KEY_TTL_IN_DAYS;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.KMS_KEY_URI;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.NUMBER_OF_KEYS_TO_CREATE;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.PEER_COORDINATOR_KMS_KEY_BASE_URI;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.PEER_COORDINATOR_KMS_KEY_URI;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.PEER_COORDINATOR_SERVICE_ACCOUNT;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.PEER_COORDINATOR_WIP_PROVIDER;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.SPANNER_INSTANCE;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyGenerationParameter.SUBSCRIPTION_ID;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.coordinator.clients.configclient.gcp.GcpCoordinatorClientConfigModule;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.DisableKeySetAcl;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.KeyGenerationKeyCount;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.KeyGenerationTtlInDays;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.KeyGenerationValidityInDays;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.KeyIdTypeName;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.KeyStorageServiceBaseUrl;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.KeyStorageServiceCloudfunctionUrl;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.KmsKeyUri;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.PeerCoordinatorKmsKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.PeerCoordinatorServiceAccount;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.PeerCoordinatorWipProvider;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.listener.Annotations.SubscriptionId;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.listener.CreateSplitKeysPubSubListener;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.listener.PubSubListener;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.listener.PubSubListenerConfig;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid.KeyIdFactory;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid.KeyIdType;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyset.KeySetManager.KeySetsJson;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.gcp.GcpSplitKeyGenerationTasksModule;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbConfig;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbModule;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.ParameterClient.ParameterClientException;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpInstanceIdOverride;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpInstanceNameOverride;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpProjectId;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpProjectIdOverride;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpZoneOverride;
import com.google.scp.shared.clients.configclient.gcp.DefaultParameterModule;
import com.google.scp.shared.clients.configclient.gcp.GcpParameterModule;
import com.google.scp.shared.clients.configclient.model.ErrorReason;
import java.util.Optional;

/** Module for Key Generation Application. */
public final class KeyGenerationModule extends AbstractModule {

  private final KeyGenerationArgs args;

  public KeyGenerationModule(KeyGenerationArgs args) {
    this.args = args;
  }

  @Provides
  @Singleton
  @SubscriptionId
  String provideSubscriptionId(ParameterClient parameterClient) throws ParameterClientException {
    return parameterClient.getParameter(SUBSCRIPTION_ID).orElse(args.getSubscriptionId());
  }

  @Provides
  @Singleton
  @KeyGenerationKeyCount
  Integer provideKeyGenerationKeyCount(ParameterClient parameterClient)
      throws ParameterClientException {
    return parameterClient
        .getParameter(NUMBER_OF_KEYS_TO_CREATE)
        .map(Integer::valueOf)
        .orElseGet(args::getNumberOfKeysToCreate);
  }

  @Provides
  @Singleton
  @KeyGenerationValidityInDays
  Integer provideKeyGenerationValidityInDays(ParameterClient parameterClient)
      throws ParameterClientException {
    return parameterClient
        .getParameter(KEYS_VALIDITY_IN_DAYS)
        .map(Integer::valueOf)
        .orElseGet(args::getKeysValidityInDays);
  }

  @Provides
  @Singleton
  @KeyGenerationTtlInDays
  Integer provideKeyGenerationTtlInDays(ParameterClient parameterClient)
      throws ParameterClientException {
    return parameterClient
        .getParameter(KEY_TTL_IN_DAYS)
        .map(Integer::valueOf)
        .orElseGet(args::getTtlInDays);
  }

  @Provides
  @Singleton
  SpannerKeyDbConfig providerSpannerKeyDbConfig(
      @GcpProjectId String projectId, ParameterClient parameterClient)
      throws ParameterClientException {
    String spannerInstanceIdParam =
        parameterClient.getParameter(SPANNER_INSTANCE).orElse(args.getSpannerInstanceId());
    String spannerDbNameParam =
        parameterClient.getParameter(KEY_DB_NAME).orElse(args.getDatabaseId());

    return SpannerKeyDbConfig.builder()
        .setGcpProjectId(projectId)
        .setSpannerInstanceId(spannerInstanceIdParam)
        .setSpannerDbName(spannerDbNameParam)
        .setReadStalenessSeconds(0)
        .setEndpointUrl(args.getSpannerEndpoint())
        .build();
  }

  @Provides
  @Singleton
  PubSubListenerConfig providesPubSubListenerConfig() {
    return PubSubListenerConfig.newBuilder().setEndpointUrl(args.getPubSubEndpoint()).build();
  }

  @Provides
  @Singleton
  @KeyStorageServiceCloudfunctionUrl
  Optional<String> providesKeyStorageServiceCloudfunctionUrl(ParameterClient parameterClient)
      throws ParameterClientException {
    return Optional.ofNullable(parameterClient.getParameter(KEY_STORAGE_SERVICE_CLOUDFUNCTION_URL))
        .orElse(args.getKeyStorageServiceCloudfunctionUrl());
  }

  @Provides
  @DisableKeySetAcl
  Boolean providesDisableKeySetAcl(ParameterClient parameterClient)
      throws ParameterClientException {
    return Boolean.valueOf(parameterClient.getParameter(DISABLE_KEY_SET_ACL).orElse("true"));
  }

  @Provides
  @Singleton
  @PeerCoordinatorKmsKeyBaseUri
  String providesPeerCoordinatorKmsKeyUri(
      ParameterClient parameterClient, @DisableKeySetAcl Boolean disableKeySetAcl)
      throws ParameterClientException {
    String kmsParam =
        disableKeySetAcl ? PEER_COORDINATOR_KMS_KEY_URI : PEER_COORDINATOR_KMS_KEY_BASE_URI;
    return parameterClient.getParameter(kmsParam).orElse(args.getPeerCoordinatorKmsKeyUri());
  }

  @Provides
  @Singleton
  @KmsKeyUri
  String providesKmsKeyUri(ParameterClient parameterClient) throws ParameterClientException {
    return parameterClient.getParameter(KMS_KEY_URI).orElse(args.getKmsKeyUri());
  }

  @Provides
  @Singleton
  @PeerCoordinatorServiceAccount
  String providesPeerCoordinatorServiceAccount(ParameterClient parameterClient)
      throws ParameterClientException {
    return parameterClient
        .getParameter(PEER_COORDINATOR_SERVICE_ACCOUNT)
        .orElse(args.getPeerCoordinatorServiceAccount());
  }

  @Provides
  @Singleton
  @PeerCoordinatorWipProvider
  String providesPeerCoordinatorWipProvider(ParameterClient parameterClient)
      throws ParameterClientException {
    return parameterClient
        .getParameter(PEER_COORDINATOR_WIP_PROVIDER)
        .orElse(args.getPeerCoordinatorWipProvider());
  }

  @Provides
  @Singleton
  @KeyStorageServiceBaseUrl
  String providesKeyStorageServiceBaseUrl(ParameterClient parameterClient)
      throws ParameterClientException {
    return parameterClient
        .getParameter(KEY_STORAGE_SERVICE_BASE_URL)
        .orElse(args.getKeyStorageServiceBaseUrl());
  }

  @Provides
  @KeyIdTypeName
  Optional<String> provideKeyIdTypeProvider(ParameterClient paramClient)
      throws ParameterClientException {
    return paramClient.getParameter(KEY_ID_TYPE).or(args::getKeyIdType);
  }

  @Provides
  @Singleton
  KeyIdFactory provideKeyIdFactoryProvider(@KeyIdTypeName Optional<String> keyIdType)
      throws ParameterClientException {
    return KeyIdType.fromString(keyIdType)
        .orElseThrow(
            () ->
                new ParameterClientException(
                    "Key ID Type %s not found", ErrorReason.MISSING_REQUIRED_PARAMETER))
        .getKeyIdFactory();
  }

  @Provides
  @KeySetsJson
  Optional<String> provideKeySetsConfig(ParameterClient parameterClient)
      throws ParameterClientException {
    return parameterClient.getLatestParameter("KEY_SETS_CONFIG");
  }

  @Provides
  LogMetricHelper provideLogMetricHelper() {
    return new LogMetricHelper("key_service/key_generation");
  }

  @Override
  protected void configure() {
    // TODO(b/282204533)
    if (args.isMultiparty()) {
      bind(PubSubListener.class).to(CreateSplitKeysPubSubListener.class);

      // Key Storage Bindings
      install(new GcpKeyStorageConfigModule());

      install(new GcpSplitKeyGenerationTasksModule());
    }

    // Data layer bindings
    install(new SpannerKeyDbModule());

    // Client Bindings
    install(new GcpCoordinatorClientConfigModule());
    if (args.getTestUseDefaultParametersOnGcp()) {
      install(new DefaultParameterModule());
    } else {
      install(new GcpParameterModule());
    }
    bind(String.class).annotatedWith(GcpProjectIdOverride.class).toInstance(args.getProjectId());

    // Necessary but empty bindings
    bind(String.class).annotatedWith(GcpInstanceIdOverride.class).toInstance("");
    bind(String.class).annotatedWith(GcpZoneOverride.class).toInstance("");
    bind(String.class).annotatedWith(GcpInstanceNameOverride.class).toInstance("");
  }
}
