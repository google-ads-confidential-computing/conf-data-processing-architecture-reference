package com.google.scp.e2e.shared;

import static com.google.common.base.Preconditions.checkState;
import static com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb.DEFAULT_SET_NAME;
import static com.google.scp.coordinator.keymanagement.testutils.DynamoKeyDbTestUtil.KEY_LIMIT;
import static java.lang.Integer.MAX_VALUE;
import static org.mockito.Mockito.mock;

import com.google.acai.AfterTest;
import com.google.acai.BeforeTest;
import com.google.acai.TestingService;
import com.google.acai.TestingServiceModule;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.scp.coordinator.keymanagement.keygeneration.app.aws.testing.SplitKeyGenerationArgsLocalStackProvider;
import com.google.scp.coordinator.keymanagement.keygeneration.app.aws.testing.SplitKeyGenerationStarter;
import com.google.scp.coordinator.keymanagement.keygeneration.app.aws.testing.SplitKeyQueueTestHelper;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.Annotations.KeyIdTypeName;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeyLimit;
import com.google.scp.coordinator.keymanagement.keyhosting.service.aws.testing.LocalPublicKeyHostingServiceModule;
import com.google.scp.coordinator.keymanagement.keyhosting.service.aws.testing.MultiLocalGetEncryptionKeyHostingServiceModule;
import com.google.scp.coordinator.keymanagement.keyhosting.service.aws.testing.MultiLocalListRecentEncryptionKeysHostingServiceModule;
import com.google.scp.coordinator.keymanagement.keystorage.service.aws.testing.KeyStorageServiceKeysProviderModule;
import com.google.scp.coordinator.keymanagement.keystorage.service.aws.testing.LocalKeyStorageServiceModule;
import com.google.scp.coordinator.keymanagement.shared.dao.aws.Annotations.DynamoKeyDbTableName;
import com.google.scp.coordinator.keymanagement.shared.dao.aws.DynamoKeyDb;
import com.google.scp.coordinator.keymanagement.testutils.aws.Annotations.CoordinatorAEncryptionKeySignatureAlgorithm;
import com.google.scp.coordinator.keymanagement.testutils.aws.Annotations.CoordinatorAEncryptionKeySignatureKeyId;
import com.google.scp.coordinator.keymanagement.testutils.aws.Annotations.CoordinatorAKeyDbTableName;
import com.google.scp.coordinator.keymanagement.testutils.aws.Annotations.CoordinatorAWorkerKekUri;
import com.google.scp.coordinator.keymanagement.testutils.aws.MultiKeyDbIntegrationTestModule;
import com.google.scp.coordinator.keymanagement.testutils.aws.TestKeyGenerationQueueModule;
import com.google.scp.operator.cpio.configclient.Annotations.CoordinatorAHttpClient;
import com.google.scp.operator.cpio.configclient.Annotations.CoordinatorBHttpClient;
import com.google.scp.operator.cpio.configclient.aws.Annotations.CoordinatorACredentialsProvider;
import com.google.scp.operator.cpio.configclient.aws.Annotations.CoordinatorBCredentialsProvider;
import com.google.scp.operator.cpio.cryptoclient.aws.Annotations.KmsEndpointOverride;
import com.google.scp.operator.cpio.cryptoclient.aws.AwsKmsMultiPartyHybridEncryptionKeyServiceModule;
import com.google.scp.operator.cpio.metricclient.MetricClient;
import com.google.scp.operator.cpio.metricclient.model.Annotations.EnableRemoteMetricAggregation;
import com.google.scp.shared.api.util.HttpClientWrapper;
import com.google.scp.shared.aws.credsprovider.AwsSessionCredentialsProvider;
import com.google.scp.shared.clients.configclient.aws.AwsClientConfigModule.AwsCredentialAccessKey;
import com.google.scp.shared.clients.configclient.aws.AwsClientConfigModule.AwsCredentialSecretKey;
import com.google.scp.shared.testutils.aws.AwsIntegrationTestModule;
import com.google.scp.shared.testutils.aws.AwsIntegrationTestUtil;
import com.google.scp.shared.testutils.common.RepoUtil;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Optional;
import javax.inject.Singleton;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * Sets up all necessary dependencies to start up 2 local coordinators and triggers key creation for
 * each test.
 */
public final class MultiCoordinatorTestEnv extends AbstractModule {

  // Optional repo name override for cross-repo testing.
  private final Optional<String> repoNameOverride;

  private final Optional<Network> network;

  private MultiCoordinatorTestEnv(Optional<String> repoNameOverride, Optional<Network> network) {
    this.repoNameOverride = repoNameOverride;
    this.network = network;
  }

  public MultiCoordinatorTestEnv() {
    this(Optional.empty(), Optional.empty());
  }

  public MultiCoordinatorTestEnv(Network network) {
    this(Optional.empty(), Optional.of(network));
  }

  private MultiCoordinatorTestEnv(String repoNameOverride) {
    this(Optional.of(repoNameOverride), Optional.empty());
  }

  private MultiCoordinatorTestEnv(String repoNameOverride, Network network) {
    this(Optional.of(repoNameOverride), Optional.of(network));
  }

  /** Used by external repos to resolve dependencies. */
  public static MultiCoordinatorTestEnv forExternalRepo(String repoName) {
    return new MultiCoordinatorTestEnv(repoName);
  }

  /** Used by external repos to resolve dependencies. */
  public static MultiCoordinatorTestEnv forExternalRepo(String repoName, Network network) {
    return new MultiCoordinatorTestEnv(repoName, network);
  }

  static {
    try {
      HybridConfig.register();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Error initializing tink.");
    }
  }

  @Provides
  @Singleton
  @CoordinatorAWorkerKekUri
  public String provideKekA(KmsClient kmsClient) {
    var keyId = AwsIntegrationTestUtil.createKmsKey(kmsClient);
    return String.format("aws-kms://arn:aws:kms:us-east-1:000000000000:key/%s", keyId);
  }

  @Provides
  @Singleton
  @CoordinatorAEncryptionKeySignatureKeyId
  public Optional<String> provideSignatureKey(KmsClient kmsClient) {
    return Optional.of(AwsIntegrationTestUtil.createSignatureKey(kmsClient));
  }

  @Provides
  @Singleton
  @AwsCredentialAccessKey
  public String getAwsCredentialAccessKey(LocalStackContainer localStack) {
    return localStack.getAccessKey();
  }

  @Provides
  @Singleton
  @AwsCredentialSecretKey
  public String getAwsCredentialSecretKey(LocalStackContainer localStack) {
    return localStack.getSecretKey();
  }

  @Provides
  @Singleton
  @KmsEndpointOverride
  public URI getEndpointOverride(LocalStackContainer localStack) {
    return localStack.getEndpointOverride(LocalStackContainer.Service.KMS);
  }

  /** Provide coordinator A's table name for public key hosting service. */
  @Provides
  @DynamoKeyDbTableName
  public String provideTableNames(@CoordinatorAKeyDbTableName String keyDbTableName) {
    return keyDbTableName;
  }

  @Override
  public void configure() {
    repoNameOverride.ifPresent(s -> bind(RepoUtil.class).toInstance(new RepoUtil(s)));
    bind(Integer.class).annotatedWith(KeyLimit.class).toInstance(KEY_LIMIT);
    bind(AwsSessionCredentialsProvider.class)
        .annotatedWith(CoordinatorACredentialsProvider.class)
        .toInstance(mock(AwsSessionCredentialsProvider.class));
    bind(AwsSessionCredentialsProvider.class)
        .annotatedWith(CoordinatorBCredentialsProvider.class)
        .toInstance(mock(AwsSessionCredentialsProvider.class));
    bind(HttpClientWrapper.class)
        .annotatedWith(CoordinatorAHttpClient.class)
        .toInstance(HttpClientWrapper.createDefault());
    bind(HttpClientWrapper.class)
        .annotatedWith(CoordinatorBHttpClient.class)
        .toInstance(HttpClientWrapper.createDefault());
    if (network.isPresent()) {
      install(new AwsIntegrationTestModule(network.get()));
    } else {
      install(new AwsIntegrationTestModule());
    }
    bind(String.class)
        .annotatedWith(CoordinatorAEncryptionKeySignatureAlgorithm.class)
        .toInstance(AwsIntegrationTestUtil.SIGNATURE_KEY_ALGORITHM);
    bind(new Key<Optional<String>>(KeyIdTypeName.class) {}).toInstance(Optional.empty());
    bind(MetricClient.class).toInstance(mock(MetricClient.class));
    bind(Boolean.class).annotatedWith(EnableRemoteMetricAggregation.class).toInstance(false);

    install(new LocalKeyStorageServiceModule());
    install(new MultiKeyDbIntegrationTestModule());
    install(new KeyStorageServiceKeysProviderModule());
    install(new MultiLocalGetEncryptionKeyHostingServiceModule());
    install(new MultiLocalListRecentEncryptionKeysHostingServiceModule());
    install(new LocalPublicKeyHostingServiceModule());
    install(new TestKeyGenerationQueueModule());
    install(new AwsKmsMultiPartyHybridEncryptionKeyServiceModule());
    install(new SplitKeyGenerationArgsLocalStackProvider());

    install(TestingServiceModule.forServices(CoordinatorRunner.class));
  }

  private static class CoordinatorRunner implements TestingService {
    @Inject SplitKeyGenerationStarter splitKeyGenerationStarter;
    @Inject SplitKeyQueueTestHelper splitKeyQueueHelper;
    @Inject DynamoKeyDb keyDb;

    @BeforeTest
    void createKeys() throws Exception {
      splitKeyGenerationStarter.start();
      splitKeyQueueHelper.triggerKeyGeneration();
      splitKeyQueueHelper.waitForEmptyQueue();
      checkState(
          keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE).size()
              == SplitKeyGenerationArgsLocalStackProvider.KEY_COUNT,
          "Key generation must successfully generate active keys.");
    }

    @AfterTest
    void stopGenerator() {
      splitKeyGenerationStarter.stop();
    }
  }
}
