package com.google.scp.e2e.shared;

import com.google.inject.AbstractModule;
import com.google.scp.coordinator.privacy.budgeting.utils.aws.PbsTestConfig;
import com.google.scp.coordinator.privacy.budgeting.utils.aws.PbsTestEnv;
import com.google.scp.shared.testutils.aws.AwsIntegrationTestModule;
import java.util.Optional;
import org.testcontainers.containers.Network;

public class MultiCoordinatorWithPbsTestEnv extends AbstractModule {

  // Optional repo name override for cross-repo testing.
  private final Optional<String> repoNameOverride;

  public MultiCoordinatorWithPbsTestEnv() {
    this.repoNameOverride = Optional.empty();
  }

  private MultiCoordinatorWithPbsTestEnv(String repoNameOverride) {
    this.repoNameOverride = Optional.of(repoNameOverride);
  }

  /** Used by external repos to resolve dependencies. */
  public static MultiCoordinatorWithPbsTestEnv forExternalRepo(String repoName) {
    return new MultiCoordinatorWithPbsTestEnv(repoName);
  }

  @Override
  public void configure() {
    var network = Network.newNetwork();
    install(
        repoNameOverride
            .map(s -> MultiCoordinatorTestEnv.forExternalRepo(s, network))
            .orElseGet(() -> new MultiCoordinatorTestEnv(network)));
    install(
        new PbsTestEnv(
            PbsTestConfig.builder()
                .setLocalstackInternalEndpoint(
                    AwsIntegrationTestModule.getInternalEndpoint()) // "http://localstack:4566")
                .build()));
  }
}
