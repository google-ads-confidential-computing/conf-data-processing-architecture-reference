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

package com.google.scp.operator.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.scp.operator.cpio.blobstorageclient.aws.S3BlobStorageClientModule.S3EndpointOverrideBinding;
import com.google.scp.operator.cpio.blobstorageclient.gcp.Annotations.GcsEndpointUrl;
import com.google.scp.operator.cpio.configclient.Annotations.CoordinatorARegionBindingOverride;
import com.google.scp.operator.cpio.configclient.Annotations.CoordinatorBRegionBindingOverride;
import com.google.scp.operator.cpio.configclient.local.Annotations.CoordinatorARoleArn;
import com.google.scp.operator.cpio.configclient.local.Annotations.CoordinatorBRoleArn;
import com.google.scp.operator.cpio.configclient.local.Annotations.CoordinatorKmsArnParameter;
import com.google.scp.operator.cpio.configclient.local.Annotations.DdbJobMetadataTableNameParameter;
import com.google.scp.operator.cpio.configclient.local.Annotations.MaxJobNumAttemptsParameter;
import com.google.scp.operator.cpio.configclient.local.Annotations.MaxJobProcessingTimeSecondsParameter;
import com.google.scp.operator.cpio.configclient.local.Annotations.ScaleInHookParameter;
import com.google.scp.operator.cpio.configclient.local.Annotations.SqsJobQueueUrlParameter;
import com.google.scp.operator.cpio.cryptoclient.Annotations.CoordinatorAEncryptionKeyServiceBaseUrl;
import com.google.scp.operator.cpio.cryptoclient.Annotations.CoordinatorBEncryptionKeyServiceBaseUrl;
import com.google.scp.operator.cpio.cryptoclient.HttpPrivateKeyFetchingService.PrivateKeyServiceBaseUrl;
import com.google.scp.operator.cpio.cryptoclient.aws.Annotations.KmsEndpointOverride;
import com.google.scp.operator.cpio.cryptoclient.gcp.GcpKmsHybridEncryptionKeyServiceConfig;
import com.google.scp.operator.cpio.cryptoclient.local.LocalFileHybridEncryptionKeyServiceModule.DecryptionKeyFilePath;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.DistributedPrivacyBudgetClientModule.CoordinatorAPrivacyBudgetServiceAuthEndpoint;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.DistributedPrivacyBudgetClientModule.CoordinatorAPrivacyBudgetServiceBaseUrl;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.DistributedPrivacyBudgetClientModule.CoordinatorBPrivacyBudgetServiceAuthEndpoint;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.DistributedPrivacyBudgetClientModule.CoordinatorBPrivacyBudgetServiceBaseUrl;
import com.google.scp.operator.cpio.jobclient.aws.AwsJobHandlerModule.DdbEndpointOverrideBinding;
import com.google.scp.operator.cpio.jobclient.aws.AwsJobHandlerModule.SqsEndpointOverrideBinding;
import com.google.scp.operator.cpio.jobclient.gcp.GcpJobHandlerConfig;
import com.google.scp.operator.cpio.jobclient.local.LocalFileJobHandlerModule.LocalFileJobHandlerPath;
import com.google.scp.operator.cpio.jobclient.local.LocalFileJobHandlerModule.LocalFileJobHandlerResultPath;
import com.google.scp.operator.cpio.jobclient.local.LocalFileJobHandlerModule.LocalFileJobParameters;
import com.google.scp.operator.cpio.lifecycleclient.aws.AwsLifecycleModule.AutoScalingEndpointOverrideBinding;
import com.google.scp.operator.cpio.metricclient.aws.AwsMetricModule.CloudwatchEndpointOverrideBinding;
import com.google.scp.operator.worker.Annotations.BenchmarkMode;
import com.google.scp.operator.worker.decryption.RecordDecrypter;
import com.google.scp.operator.worker.decryption.hybrid.HybridDecryptionModule;
import com.google.scp.operator.worker.decryption.hybrid.HybridDeserializingReportDecrypter;
import com.google.scp.operator.worker.logger.localtocloud.LocalFileToCloudStorageLogger.ResultWorkingDirectory;
import com.google.scp.operator.worker.model.serdes.ReportSerdes;
import com.google.scp.operator.worker.model.serdes.proto.ProtoReportSerdes;
import com.google.scp.operator.worker.perf.StopwatchExporter;
import com.google.scp.operator.worker.perf.exporter.CloudStopwatchExporter.StopwatchBucketName;
import com.google.scp.operator.worker.perf.exporter.CloudStopwatchExporter.StopwatchKeyName;
import com.google.scp.operator.worker.reader.RecordReaderFactory;
import com.google.scp.operator.worker.reader.avro.LocalNioPathAvroReaderFactory;
import com.google.scp.operator.worker.selector.ResultLoggerModuleSelector;
import com.google.scp.shared.clients.configclient.Annotations.ApplicationRegionBindingOverride;
import com.google.scp.shared.clients.configclient.aws.AwsClientConfigModule.AwsCredentialAccessKey;
import com.google.scp.shared.clients.configclient.aws.AwsClientConfigModule.AwsCredentialSecretKey;
import com.google.scp.shared.clients.configclient.aws.AwsClientConfigModule.AwsEc2MetadataEndpointOverride;
import com.google.scp.shared.clients.configclient.aws.AwsParameterModule.Ec2EndpointOverrideBinding;
import com.google.scp.shared.clients.configclient.aws.AwsParameterModule.SsmEndpointOverrideBinding;
import com.google.scp.shared.clients.configclient.aws.StsClientModule;
import com.google.scp.shared.clients.configclient.aws.StsClientModule.StsEndpointOverrideBinding;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpInstanceIdOverride;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpInstanceNameOverride;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpProjectIdOverride;
import com.google.scp.shared.clients.configclient.gcp.Annotations.GcpZoneOverride;
import com.google.scp.shared.clients.configclient.gcp.GcpOperatorClientConfig;
import com.google.scp.shared.mapper.TimeObjectMapper;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Supplier;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

/**
 * Defines dependencies needed to run Simple Worker. Most are populated from build arguments defined
 * in {@link SimpleWorkerArgs}
 */
public final class SimpleWorkerModule extends AbstractModule {

  private final SimpleWorkerArgs args;

  public SimpleWorkerModule(SimpleWorkerArgs args) {
    this.args = args;
  }

  @Override
  protected void configure() {
    install(new WorkerModule());
    bind(SdkHttpClient.class).toInstance(ApacheHttpClient.builder().build());

    switch (args.getBlobStorageClientSelector()) {
      case AWS_S3_CLIENT:
        bind(URI.class)
            .annotatedWith(S3EndpointOverrideBinding.class)
            .toInstance(args.getS3EndpointOverride());
        break;
      case GCP_CS_CLIENT:
        bind(new TypeLiteral<Optional<String>>() {})
            .annotatedWith(GcsEndpointUrl.class)
            .toInstance(args.getGcsEndpoint());
        break;
      case LOCAL_FS_CLIENT:
        bind(FileSystem.class).toInstance(FileSystems.getDefault());
    }
    install(args.getBlobStorageClientSelector().getBlobStorageClientSelectorModule());
    // Binding/installing puller-specific classes and objects, mainly based on the CLI arguments.
    // Ideally this would happen in the relevant modules, but since they cannot have access to the
    // CLI args, it is done here.
    switch (args.getJobClient()) {
      case LOCAL_FILE:
        bind(Path.class)
            .annotatedWith(LocalFileJobHandlerPath.class)
            .toInstance(Paths.get(args.getLocalFileSinglePullerPath()));
        Optional<Path> localJobInfoPath = Optional.empty();
        if (!args.getLocalFileJobInfoPath().isEmpty()) {
          localJobInfoPath = Optional.of(Paths.get(args.getLocalFileJobInfoPath()));
        }
        bind(new TypeLiteral<Optional<Path>>() {})
            .annotatedWith(LocalFileJobHandlerResultPath.class)
            .toInstance(localJobInfoPath);
        bind(new TypeLiteral<Supplier<ImmutableMap<String, String>>>() {})
            .annotatedWith(LocalFileJobParameters.class)
            .toInstance(Suppliers.ofInstance(ImmutableMap.of()));
        bind(ObjectMapper.class).to(TimeObjectMapper.class);
        break;
      case GCP:
        GcpJobHandlerConfig config =
            GcpJobHandlerConfig.builder()
                .setGcpProjectId(args.getGcpProjectId())
                .setPubSubMaxMessageSizeBytes(1000)
                .setPubSubMessageLeaseSeconds(600)
                .setMaxNumAttempts(5)
                .setPubSubTopicId(args.getPubSubTopicId())
                .setPubSubSubscriptionId(args.getPubSubSubscriptionId())
                .setSpannerInstanceId(args.getSpannerInstanceId())
                .setSpannerDbName(args.getSpannerDbName())
                .setSpannerEndpoint(args.getSpannerEndpoint())
                .setPubSubEndpoint(args.getPubSubEndpoint())
                .build();

        bind(GcpJobHandlerConfig.class).toInstance(config);
        bind(ObjectMapper.class).to(TimeObjectMapper.class);
        break;
    }
    install(args.getJobClient().getPullerGuiceModule());

    install(args.getParamClient().getParameterClientModule());
    // Binding/installing parameter store values when providing them from cli args.
    // Ideally this would happen in the relevant modules, but since they cannot have access to the
    // CLI args, it is done here.
    switch (args.getParamClient()) {
      case ARGS:
        bind(String.class)
            .annotatedWith(SqsJobQueueUrlParameter.class)
            .toInstance(args.getAwsSqsQueueUrl());
        bind(String.class)
            .annotatedWith(DdbJobMetadataTableNameParameter.class)
            .toInstance(args.getAwsMetadatadbTableName());
        bind(String.class)
            .annotatedWith(MaxJobNumAttemptsParameter.class)
            .toInstance(args.getMaxJobNumAttempts());
        bind(String.class)
            .annotatedWith(MaxJobProcessingTimeSecondsParameter.class)
            .toInstance(args.getMessageVisibilityTimeoutSeconds());
        bind(String.class)
            .annotatedWith(CoordinatorARoleArn.class)
            .toInstance(args.getCoordinatorARoleArn());
        bind(String.class)
            .annotatedWith(CoordinatorBRoleArn.class)
            .toInstance(args.getCoordinatorBRoleArn());
        bind(String.class)
            .annotatedWith(CoordinatorKmsArnParameter.class)
            .toInstance(args.getKmsSymmetricKey());
        bind(String.class)
            .annotatedWith(ScaleInHookParameter.class)
            .toInstance(args.getScaleInHook());
        break;
      case AWS:
        break;
      case GCP:
        break;
    }

    switch (args.getClientConfigSelector()) {
      case AWS:
        bind(String.class)
            .annotatedWith(AwsCredentialAccessKey.class)
            .toInstance(args.getAccessKey());
        bind(String.class)
            .annotatedWith(AwsCredentialSecretKey.class)
            .toInstance(args.getSecretKey());
        bind(String.class)
            .annotatedWith(AwsEc2MetadataEndpointOverride.class)
            .toInstance(args.getAwsMetadataEndpointOverride());
        bind(URI.class)
            .annotatedWith(CloudwatchEndpointOverrideBinding.class)
            .toInstance(args.getCloudwatchEndpointOverride());
        bind(URI.class)
            .annotatedWith(Ec2EndpointOverrideBinding.class)
            .toInstance(args.getEc2EndpointOverride());
        bind(URI.class)
            .annotatedWith(SqsEndpointOverrideBinding.class)
            .toInstance(args.getSqsEndpointOverride());
        bind(URI.class)
            .annotatedWith(SsmEndpointOverrideBinding.class)
            .toInstance(args.getSsmEndpointOverride());
        bind(URI.class)
            .annotatedWith(DdbEndpointOverrideBinding.class)
            .toInstance(args.getDdbEndpointOverride());
        bind(URI.class)
            .annotatedWith(StsEndpointOverrideBinding.class)
            .toInstance(args.getStsEndpointOverride());
        bind(URI.class)
            .annotatedWith(KmsEndpointOverride.class)
            .toInstance(args.getKmsEndpointOverride());
        bind(String.class)
            .annotatedWith(ApplicationRegionBindingOverride.class)
            .toInstance(args.getAdtechRegionOverride());
        bind(String.class)
            .annotatedWith(CoordinatorARegionBindingOverride.class)
            .toInstance(args.getCoordinatorARegionOverride());
        bind(String.class)
            .annotatedWith(CoordinatorBRegionBindingOverride.class)
            .toInstance(args.getCoordinatorBRegionOverride());

        install(new StsClientModule());
        bind(URI.class)
            .annotatedWith(AutoScalingEndpointOverrideBinding.class)
            .toInstance(args.getAutoScalingEndpointOverride());
        break;
      case GCP:
        bind(String.class)
            .annotatedWith(GcpProjectIdOverride.class)
            .toInstance(args.getGcpProjectId());
        bind(String.class)
            .annotatedWith(GcpInstanceIdOverride.class)
            .toInstance(args.getGcpInstanceIdOverride());
        bind(String.class)
            .annotatedWith(GcpInstanceNameOverride.class)
            .toInstance(args.getGcpInstanceNameOverride());
        bind(String.class)
            .annotatedWith(GcpZoneOverride.class)
            .toInstance(args.getGcpZoneOverride());
        GcpOperatorClientConfig.Builder configBuilder =
            GcpOperatorClientConfig.builder()
                .setCoordinatorAServiceAccountToImpersonate(args.getCoordinatorAServiceAccount())
                .setCoordinatorAWipProvider(args.getCoordinatorAWipProvider())
                .setUseLocalCredentials(args.getCoordinatorAWipProvider().isEmpty())
                .setCoordinatorAEncryptionKeyServiceBaseUrl(
                    args.getPrimaryEncryptionKeyServiceBaseUrl())
                .setCoordinatorAEncryptionKeyServiceCloudfunctionUrl(
                    args.getPrimaryEncryptionKeyServiceCloudfunctionUrl());
        if (!args.getCoordinatorBWipProvider().isEmpty()) {
          configBuilder.setCoordinatorBWipProvider(Optional.of(args.getCoordinatorBWipProvider()));
          configBuilder
              .setCoordinatorBServiceAccountToImpersonate(
                  Optional.of(args.getCoordinatorBServiceAccount()))
              .setCoordinatorBEncryptionKeyServiceBaseUrl(
                  Optional.of(args.getSecondaryEncryptionKeyServiceBaseUrl()))
              .setCoordinatorBEncryptionKeyServiceCloudfunctionUrl(
                  args.getSecondaryEncryptionKeyServiceCloudfunctionUrl());
        }
        bind(GcpOperatorClientConfig.class).toInstance(configBuilder.build());
        break;
    }
    install(args.getClientConfigSelector().getClientConfigGuiceModule());

    install(args.getLifecycleClient().getLifecycleModule());

    install(args.getNotificationClient().getNotificationModule());

    install(args.getMetricClient().getMetricModule());

    // Dependencies for aggregation worker processor
    bind(RecordReaderFactory.class).to(LocalNioPathAvroReaderFactory.class);

    // decryption and deserialization
    bind(String.class)
        .annotatedWith(PrivateKeyServiceBaseUrl.class)
        .toInstance(args.getPrivateKeyServiceBaseUrl());
    bind(String.class)
        .annotatedWith(CoordinatorAEncryptionKeyServiceBaseUrl.class)
        .toInstance(args.getPrimaryEncryptionKeyServiceBaseUrl());
    bind(String.class)
        .annotatedWith(CoordinatorBEncryptionKeyServiceBaseUrl.class)
        .toInstance(args.getSecondaryEncryptionKeyServiceBaseUrl());

    install(new HybridDecryptionModule());
    bind(ReportSerdes.class).to(ProtoReportSerdes.class);
    bind(RecordDecrypter.class).to(HybridDeserializingReportDecrypter.class);

    // determines how/where to read the decryption key.
    switch (args.getHybridEncryptionKeyServiceSelector()) {
      case LOCAL_FILE_DECRYPTION_KEY_SERVICE:
        bind(Path.class)
            .annotatedWith(DecryptionKeyFilePath.class)
            .toInstance(Paths.get(args.getLocalFileDecryptionKeyPath()));
        break;
      case GCP_KMS_DECRYPTION_KEY_SERVICE:
        GcpKmsHybridEncryptionKeyServiceConfig config =
            GcpKmsHybridEncryptionKeyServiceConfig.builder()
                .setCoordinatorAKmsKeyUri(args.getKmsSymmetricKey())
                .setCoordinatorCloudfunctionUrl(
                    args.getPrimaryEncryptionKeyServiceCloudfunctionUrl())
                .build();
        bind(GcpKmsHybridEncryptionKeyServiceConfig.class).toInstance(config);
        break;
      case GCP_KMS_MULTI_PARTY_DECRYPTION_KEY_SERVICE:
        GcpKmsHybridEncryptionKeyServiceConfig.Builder configBuilder =
            GcpKmsHybridEncryptionKeyServiceConfig.builder()
                .setCoordinatorAKmsKeyUri(args.getCoodinatorAKmsKey())
                .setCoordinatorAEncodedKeysetHandle(args.getTestEncodedKeysetHandle())
                .setCoordinatorBEncodedKeysetHandle(args.getTestCoordinatorBEncodedKeysetHandle());
        if (!args.getCoodinatorBKmsKey().isEmpty()) {
          configBuilder.setCoordinatorBKmsKeyUri(Optional.of(args.getCoodinatorBKmsKey()));
        }
        bind(GcpKmsHybridEncryptionKeyServiceConfig.class).toInstance(configBuilder.build());
        break;
    }

    // installs the appropriate pbs client module
    install(args.getPbsclient().getDistributedPrivacyBudgetClientModule());
    switch (args.getPbsclient()) {
      case AWS:
      case GCP:
        bind(String.class)
            .annotatedWith(CoordinatorAPrivacyBudgetServiceBaseUrl.class)
            .toInstance(args.getCoordinatorAPrivacyBudgetingServiceUrl());
        bind(String.class)
            .annotatedWith(CoordinatorBPrivacyBudgetServiceBaseUrl.class)
            .toInstance(args.getCoordinatorBPrivacyBudgetingServiceUrl());
        bind(String.class)
            .annotatedWith(CoordinatorAPrivacyBudgetServiceAuthEndpoint.class)
            .toInstance(args.getCoordinatorAPrivacyBudgetingServiceAuthEndpoint());
        bind(String.class)
            .annotatedWith(CoordinatorBPrivacyBudgetServiceAuthEndpoint.class)
            .toInstance(args.getCoordinatorBPrivacyBudgetingServiceAuthEndpoint());
        break;
      case LOCAL:
      default:
        // No bindings necessary.
    }
    // decryption key service.
    install(args.getHybridEncryptionKeyServiceSelector().getHybridEncryptionKeyServiceModule());

    // Benchmark Mode for perf tests
    bind(boolean.class).annotatedWith(BenchmarkMode.class).toInstance(args.getBenchmarkMode());

    // Stopwatch exporting
    bind(StopwatchExporter.class).to(args.getStopwatchExporterSelector().getExporterClass());
    switch (args.getStopwatchExporterSelector()) {
      case NO_OP:
        break;
      case CLOUD:
        bind(String.class)
            .annotatedWith(StopwatchBucketName.class)
            .toInstance(args.getStopwatchBucketName());
        bind(String.class)
            .annotatedWith(StopwatchKeyName.class)
            .toInstance(args.getStopwatchKeyName());
        break;
    }

    // processor
    bind(JobProcessor.class).to(SimpleProcessor.class);

    // result logger
    install(args.resultLoggerModuleSelector().getResultLoggerModule());
    if (args.resultLoggerModuleSelector() == ResultLoggerModuleSelector.LOCAL_TO_CLOUD) {
      bind(Path.class)
          .annotatedWith(ResultWorkingDirectory.class)
          .toInstance(Paths.get(args.getResultWorkingDirectoryPathString()));
    }
  }
}
