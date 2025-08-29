package com.google.scp.coordinator.testutils.gcp;

import static com.google.scp.shared.gcp.Constants.SPANNER_COORD_B_TEST_DB_NAME;

import com.google.acai.TestingServiceModule;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.storage.Storage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoOptional;
import com.google.protobuf.ByteString;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.listener.Annotations.SubscriptionId;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.testing.KeyGenerationAnnotations.CloudSpannerEndpoint;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.testing.KeyGenerationAnnotations.EncodedKeysetHandle;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.testing.KeyGenerationAnnotations.KeyStorageEndpoint;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.testing.KeyGenerationAnnotations.PubSubEndpoint;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.testing.KeyGenerationArgsLocalEmulatorProvider;
import com.google.scp.coordinator.keymanagement.keystorage.service.gcp.testing.LocalKeyStorageServiceHttpFunction;
import com.google.scp.coordinator.keymanagement.shared.dao.common.Annotations.KeyDbClient;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbConfig;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbTestModule;
import com.google.scp.coordinator.keymanagement.testutils.CloudFunctionEnvironmentVariables;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.KeyStorageCloudFunctionContainer;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.KeyStorageEnvironmentVariables;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PrivateKeyCoordinatorAEnvironmentVariables;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PrivateKeyCoordinatorBEnvironmentVariables;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PrivateKeyServiceCloudFunctionContainer;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PrivateKeyServiceCoordinatorBCloudFunctionContainer;
import com.google.scp.coordinator.keymanagement.testutils.gcp.cloudfunction.KeyManagementCloudFunctionContainersModule;
import com.google.scp.operator.cpio.blobstorageclient.gcp.GcsBlobStorageClient;
import com.google.scp.operator.cpio.cryptoclient.Annotations.CoordinatorAEncryptionKeyServiceBaseUrl;
import com.google.scp.operator.cpio.cryptoclient.Annotations.CoordinatorBEncryptionKeyServiceBaseUrl;
import com.google.scp.operator.shared.dao.metadatadb.common.JobMetadataDb.JobMetadataDbClient;
import com.google.scp.operator.shared.dao.metadatadb.gcp.SpannerMetadataDbTestModule;
import com.google.scp.shared.testutils.gcp.Annotations.KeyGenerationSubscriptionId;
import com.google.scp.shared.testutils.gcp.CloudFunctionEmulatorContainer;
import com.google.scp.shared.testutils.gcp.GcpPubSubIntegrationTestModule;
import com.google.scp.shared.testutils.gcp.LocalGcsContainerTestModule;
import com.google.scp.shared.testutils.gcp.PubSubEmulatorContainer;
import com.google.scp.shared.testutils.gcp.SpannerEmulatorContainer;
import com.google.scp.shared.testutils.gcp.SpannerEmulatorContainerTestModule;
import com.google.scp.shared.testutils.gcp.SpannerLocalService;
import com.google.scp.shared.util.KeysetHandleSerializerUtil;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import javax.inject.Named;

/** Provides environment bindings for the MultiCoordinator integration tests. */
public class GcpMultiCoordinatorTestEnvModule extends AbstractModule {
  private static final ImmutableList<String> ALL_CREATE_TABLE_QUERIES =
      ImmutableList.<String>builder()
          .addAll(SpannerKeyDbTestModule.CREATE_TABLE_STATEMENTS)
          .addAll(SpannerMetadataDbTestModule.CREATE_TABLE_STATEMENTS)
          .build();

  @Provides
  @Inject
  @Singleton
  public GcsBlobStorageClient providesGcsBlobStorageClient(Storage gcsStorage) {
    return new GcsBlobStorageClient(gcsStorage);
  }

  @Provides
  @CoordinatorAEncryptionKeyServiceBaseUrl
  public String providesCoordinatorAEncryptionKeyServiceBaseUrl(
      @PrivateKeyServiceCloudFunctionContainer
          CloudFunctionEmulatorContainer encryptionKeyServiceCloudFunction) {
    return String.format("http://%s", encryptionKeyServiceCloudFunction.getEmulatorEndpoint());
  }

  @Provides
  @CoordinatorBEncryptionKeyServiceBaseUrl
  public String providesCoordinatorBEncryptionKeyServiceBaseUrl(
      @PrivateKeyServiceCoordinatorBCloudFunctionContainer
          CloudFunctionEmulatorContainer encryptionKeyServiceCloudFunction) {
    return String.format("http://%s", encryptionKeyServiceCloudFunction.getEmulatorEndpoint());
  }

  @Provides
  @Singleton
  // Note: This avoids using the real KMS.
  // TODO(b/261029074): Use local KMS implementation when it's ready.
  public KeysetHandle providesKeysetHandle() {
    try {
      AeadConfig.register();
      return KeysetHandle.generateNew(KeyTemplates.get("AES128_GCM"));
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Failed to create keyset handle", e);
    }
  }

  @Provides
  @Singleton
  @EncodedKeysetHandle
  public String providesEncodedKeysetHandle(KeysetHandle keysetHandle) {
    try {
      ByteString keysetHandleByteString =
          KeysetHandleSerializerUtil.toBinaryCleartext(keysetHandle);
      return Base64.getEncoder().encodeToString(keysetHandleByteString.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create keyset handle encoded string", e);
    }
  }

  @ProvidesIntoOptional(ProvidesIntoOptional.Type.ACTUAL)
  @Singleton
  @KeyStorageEnvironmentVariables
  public Optional<Map<String, String>> providesEnvVariables(
      @EncodedKeysetHandle String encodedKeysetHandle,
      @Named("CoordinatorBKeyDbConfig") SpannerKeyDbConfig config) {
    return Optional.of(
        ImmutableMap.of(
            LocalKeyStorageServiceHttpFunction.KEYSET_HANDLE_ENCODE_STRING_ENV_NAME,
            encodedKeysetHandle,
            CloudFunctionEnvironmentVariables.ENV_VAR_GCP_PROJECT_ID,
            config.gcpProjectId(),
            CloudFunctionEnvironmentVariables.ENV_VAR_SPANNER_INSTANCE_ID,
            config.spannerInstanceId(),
            CloudFunctionEnvironmentVariables.ENV_VAR_SPANNER_DB_NAME,
            config.spannerDbName()));
  }

  @ProvidesIntoOptional(ProvidesIntoOptional.Type.ACTUAL)
  @Singleton
  @PrivateKeyCoordinatorAEnvironmentVariables
  public Optional<Map<String, String>> providesEncryptionKeyCoordinatorAEnvVars() {
    return Optional.of(
        ImmutableMap.of(
            CloudFunctionEnvironmentVariables.ENV_VAR_GCP_PROJECT_ID_V2,
            SpannerKeyDbTestModule.TEST_DB_CONFIG.gcpProjectId(),
            CloudFunctionEnvironmentVariables.ENV_VAR_SPANNER_INSTANCE_ID_V2,
            SpannerKeyDbTestModule.TEST_DB_CONFIG.spannerInstanceId(),
            CloudFunctionEnvironmentVariables.ENV_VAR_SPANNER_DB_NAME_V2,
            SpannerKeyDbTestModule.TEST_DB_CONFIG.spannerDbName()));
  }

  @ProvidesIntoOptional(ProvidesIntoOptional.Type.ACTUAL)
  @Singleton
  @PrivateKeyCoordinatorBEnvironmentVariables
  public Optional<Map<String, String>> providesEncryptionKeyCoordinatorBEnvVars(
      @Named("CoordinatorBKeyDbConfig") SpannerKeyDbConfig config) {
    return Optional.of(
        ImmutableMap.of(
            CloudFunctionEnvironmentVariables.ENV_VAR_GCP_PROJECT_ID_V2,
            config.gcpProjectId(),
            CloudFunctionEnvironmentVariables.ENV_VAR_SPANNER_INSTANCE_ID_V2,
            config.spannerInstanceId(),
            CloudFunctionEnvironmentVariables.ENV_VAR_SPANNER_DB_NAME_V2,
            config.spannerDbName()));
  }

  @Provides
  @Singleton
  public SpannerKeyDbConfig providesSpannerKeyDbConfig() {
    return SpannerKeyDbTestModule.TEST_DB_CONFIG;
  }

  @Provides
  @Named("CoordinatorAKeyDbConfig")
  @Singleton
  public SpannerKeyDbConfig providesSpannerKeyDbConfig(@CloudSpannerEndpoint String endpoint) {
    return SpannerKeyDbConfig.builder()
        .setGcpProjectId(SpannerKeyDbTestModule.TEST_DB_CONFIG.gcpProjectId())
        .setSpannerInstanceId(SpannerKeyDbTestModule.TEST_DB_CONFIG.spannerInstanceId())
        .setSpannerDbName(SpannerKeyDbTestModule.TEST_DB_CONFIG.spannerDbName())
        .setEndpointUrl(Optional.of(endpoint))
        .setReadStalenessSeconds(0)
        .build();
  }

  @Provides
  @Named("CoordinatorBKeyDbConfig")
  @Singleton
  public SpannerKeyDbConfig providesCoordinatorBSpannerKeyDbConfig(
      @CloudSpannerEndpoint String endpoint) {
    // Create a new database in the existing Spanner emulator to differentiate CoordinatorA and
    // CoordinatorB.
    SpannerDbCreationHelper creator =
        new SpannerDbCreationHelper(
            endpoint,
            SpannerKeyDbTestModule.TEST_DB_CONFIG.gcpProjectId(),
            SpannerKeyDbTestModule.TEST_DB_CONFIG.spannerInstanceId(),
            SPANNER_COORD_B_TEST_DB_NAME,
            SpannerKeyDbTestModule.CREATE_TABLE_STATEMENTS);
    creator.create();
    return SpannerKeyDbConfig.builder()
        .setGcpProjectId(creator.project)
        .setSpannerInstanceId(creator.instanceName)
        .setSpannerDbName(creator.dbName)
        .setEndpointUrl(Optional.of(creator.endpoint))
        // This is useless but will fail without being set.
        .setReadStalenessSeconds(0)
        .build();
  }

  @Provides
  @Singleton
  @JobMetadataDbClient
  public DatabaseClient providesMetadataDatabaseClient(DatabaseClient client) {
    return client;
  }

  @Provides
  @Singleton
  @KeyDbClient
  public DatabaseClient providesKeyDbDatabaseClient(DatabaseClient client) {
    return client;
  }

  @Provides
  @Singleton
  @SubscriptionId
  public String providesSubscriptionId(@KeyGenerationSubscriptionId String subscriptionId) {
    return subscriptionId;
  }

  @Provides
  @Singleton
  @KeyStorageEndpoint
  public String providesCoordinatorBKeystorageCloudFunctionEndpoint(
      @KeyStorageCloudFunctionContainer
          CloudFunctionEmulatorContainer keyStorageCloudFunctionEmulator) {
    return String.format(
        "http://%s/v1alpha", keyStorageCloudFunctionEmulator.getEmulatorEndpoint());
  }

  @Provides
  @Singleton
  @CloudSpannerEndpoint
  public String providesCloudSpannerEndpoint(SpannerEmulatorContainer spannerEmulatorContainer) {
    return spannerEmulatorContainer.getEmulatorGrpcEndpoint();
  }

  @Provides
  @Singleton
  @PubSubEndpoint
  public String providesPubSubEndpoint(PubSubEmulatorContainer pubusbEmulatorContainer) {
    return pubusbEmulatorContainer.getEmulatorEndpoint();
  }

  @Override
  public void configure() {
    install(new LocalGcsContainerTestModule());
    install(new GcpPubSubIntegrationTestModule());
    install(
        new SpannerEmulatorContainerTestModule(
            SpannerKeyDbTestModule.TEST_DB_CONFIG.gcpProjectId(),
            SpannerKeyDbTestModule.TEST_DB_CONFIG.spannerInstanceId(),
            SpannerKeyDbTestModule.TEST_DB_CONFIG.spannerDbName(),
            ALL_CREATE_TABLE_QUERIES));
    install(TestingServiceModule.forServices(SpannerLocalService.class));
    install(new KeyManagementCloudFunctionContainersModule());
    install(new KeyGenerationArgsLocalEmulatorProvider());
  }
}
