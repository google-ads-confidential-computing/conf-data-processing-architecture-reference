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

package com.google.scp.coordinator.keymanagement.testutils.gcp.cloudfunction;

import static com.google.scp.shared.testutils.gcp.CloudFunctionEmulatorContainer.startContainerAndConnectToSpannerWithEnvs;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.multibindings.ProvidesIntoOptional;
import com.google.kms.LocalKmsServerContainer;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbConfig;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.KeyStorageCloudFunctionContainer;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.KeyStorageCloudFunctionContainerWithKms;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.KeyStorageEnvironmentVariables;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.LocalKmsEnvironmentVariables;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PrivateKeyCoordinatorAEnvironmentVariables;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PrivateKeyCoordinatorBEnvironmentVariables;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PrivateKeyServiceCloudFunctionContainer;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PrivateKeyServiceCoordinatorBCloudFunctionContainer;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.PublicKeyCloudFunctionContainer;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.TestLocalKmsServerContainer;
import com.google.scp.shared.testutils.gcp.CloudFunctionEmulatorContainer;
import com.google.scp.shared.testutils.gcp.SpannerEmulatorContainer;
import java.util.Map;
import java.util.Optional;

/** Module that defines Cloud Function containers needed for Key Management integration tests. */
public class KeyManagementCloudFunctionContainersModule extends AbstractModule {

  public static final String KMS_ENDPOINT_ENV_VAR_NAME = "KMS_ENDPOINT";

  public KeyManagementCloudFunctionContainersModule() {}

  @Override
  public void configure() {
    OptionalBinder.newOptionalBinder(
            binder(),
            Key.get(
                new TypeLiteral<Optional<Map<String, String>>>() {},
                KeyStorageEnvironmentVariables.class))
        .setDefault()
        .toInstance(Optional.empty());

    // Pass in optional parameters via environment parameters; defaults are used if empty.
    OptionalBinder.newOptionalBinder(
            binder(),
            Key.get(
                new TypeLiteral<Optional<Map<String, String>>>() {},
                PrivateKeyCoordinatorAEnvironmentVariables.class))
        .setDefault()
        .toInstance(Optional.empty());
    OptionalBinder.newOptionalBinder(
            binder(),
            Key.get(
                new TypeLiteral<Optional<Map<String, String>>>() {},
                PrivateKeyCoordinatorBEnvironmentVariables.class))
        .setDefault()
        .toInstance(Optional.empty());
  }

  /** Starts and provides a container for Public Key Cloud Function Integration tests. */
  @Provides
  @Singleton
  @PublicKeyCloudFunctionContainer
  public CloudFunctionEmulatorContainer getFunctionContainer(
      SpannerKeyDbConfig keyDbConfig,
      SpannerEmulatorContainer spannerEmulatorContainer) {
    return startContainerAndConnectToSpannerWithEnvs(
        spannerEmulatorContainer,
        Optional.of(
            ImmutableMap.of(
                "SPANNER_INSTANCE", keyDbConfig.spannerInstanceId(),
                "SPANNER_DATABASE", keyDbConfig.spannerDbName(),
                "PROJECT_ID", keyDbConfig.gcpProjectId())),
        "PublicKeyService_deploy.jar",
        "java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp/",
        "com.google.scp.coordinator.keymanagement.keyhosting.service.gcp.PublicKeyService");
  }

  /**
   * Starts and provides a container for Encryption Key Cloud Function Integration tests. Note: This
   * is for Coordinator A.
   */
  @Provides
  @Singleton
  @PrivateKeyServiceCloudFunctionContainer
  public CloudFunctionEmulatorContainer getEncryptionKeyFunctionContainer(
      SpannerEmulatorContainer spannerEmulatorContainer,
      @PrivateKeyCoordinatorAEnvironmentVariables Optional<Map<String, String>> envVariables) {
    return startContainerAndConnectToSpannerWithEnvs(
        spannerEmulatorContainer,
        envVariables,
        "PrivateKeyService_deploy.jar",
        "java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp/",
        "com.google.scp.coordinator.keymanagement.keyhosting.service.gcp.PrivateKeyService");
  }

  /**
   * Starts and provides a container for Private Key Cloud Function Integration tests. Note: This
   * is for Coordinator B.
   */
  @Provides
  @Singleton
  @PrivateKeyServiceCoordinatorBCloudFunctionContainer
  public CloudFunctionEmulatorContainer getEncryptionKeyCoordinatorBFunctionContainer(
      SpannerEmulatorContainer spannerEmulatorContainer,
      @PrivateKeyCoordinatorBEnvironmentVariables Optional<Map<String, String>> envVariables) {
    return startContainerAndConnectToSpannerWithEnvs(
        spannerEmulatorContainer,
        envVariables,
        "PrivateKeyService_deploy.jar",
        "java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp/",
        "com.google.scp.coordinator.keymanagement.keyhosting.service.gcp.PrivateKeyService");
  }

  /** Starts and provides a container for Key Storage Cloud Function Integration tests. */
  @Provides
  @Singleton
  @KeyStorageCloudFunctionContainer
  public CloudFunctionEmulatorContainer getKeyStorageFunctionContainer(
      SpannerEmulatorContainer spannerEmulatorContainer,
      @KeyStorageEnvironmentVariables Optional<Map<String, String>> envVariables) {
    return startContainerAndConnectToSpannerWithEnvs(
        spannerEmulatorContainer,
        envVariables,
        "LocalKeyStorageServiceHttpCloudFunction_deploy.jar",
        "javatests/com/google/scp/coordinator/keymanagement/keystorage/service/gcp/testing/",
        "com.google.scp.coordinator.keymanagement.keystorage.service.gcp.testing.LocalKeyStorageServiceHttpFunction");
  }

  /* starts a container for local kms service, used by coordinator/keystorage/KeystorageServiceIntegrationTest*/
  @Provides
  @Singleton
  @TestLocalKmsServerContainer
  public LocalKmsServerContainer getLocalKmsServerContainer() {
    return LocalKmsServerContainer.startLocalKmsContainer(
        "LocalGcpKmsServer_deploy.jar",
        "javatests/com/google/kms/",
        "com.google.kms.LocalGcpKmsServer");
  }

  /**
   * Provides the environment variable, which is local kms server's endpoint, the test will use for
   * encryption/decryption.
   */
  @ProvidesIntoOptional(ProvidesIntoOptional.Type.ACTUAL)
  @Singleton
  @LocalKmsEnvironmentVariables
  public Optional<Map<String, String>> providesEnvVariables(
      @TestLocalKmsServerContainer LocalKmsServerContainer container) {
    return Optional.of(
        ImmutableMap.of(
            KMS_ENDPOINT_ENV_VAR_NAME,
            "http://"
                + container
                .getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .entrySet()
                .stream()
                .findFirst()
                .get()
                .getValue()
                .getIpAddress()
                + ":"
                + container.getHttpPort()));
  }

  /** Starts and provides a container for Key Storage Cloud Function Integration tests. */
  @Provides
  @Singleton
  @KeyStorageCloudFunctionContainerWithKms
  public CloudFunctionEmulatorContainer getKeyStorageFunctionContainerWithKms(
      SpannerEmulatorContainer spannerEmulatorContainer,
      @LocalKmsEnvironmentVariables Optional<Map<String, String>> envVariables) {
    return startContainerAndConnectToSpannerWithEnvs(
        spannerEmulatorContainer,
        envVariables,
        "LocalKeyStorageServiceHttpCloudFunction_deploy.jar",
        "javatests/com/google/scp/coordinator/keymanagement/keystorage/service/gcp/testing/",
        "com.google.scp.coordinator.keymanagement.keystorage.service.gcp.testing.LocalKeyStorageServiceHttpFunction");
  }
}
