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

import com.beust.jcommander.Parameter;
import com.google.common.annotations.Beta;
import com.google.scp.operator.worker.selector.BlobStorageClientSelector;
import com.google.scp.operator.worker.selector.ClientConfigSelector;
import com.google.scp.operator.worker.selector.HybridEncryptionKeyServiceSelector;
import com.google.scp.operator.worker.selector.JobClientSelector;
import com.google.scp.operator.worker.selector.LifecycleClientSelector;
import com.google.scp.operator.worker.selector.MetricClientSelector;
import com.google.scp.operator.worker.selector.NotificationClientSelector;
import com.google.scp.operator.worker.selector.ParameterClientSelector;
import com.google.scp.operator.worker.selector.PrivacyBudgetClientSelector;
import com.google.scp.operator.worker.selector.ResultLoggerModuleSelector;
import com.google.scp.operator.worker.selector.StopwatchExporterSelector;
import java.net.URI;
import java.util.Optional;

/** Provides CLI arguments with which to run the SimpleWorker using {@link SimpleWorkerModule} */
public final class SimpleWorkerArgs {

  @Parameter(names = "--client_config_env", description = "Selects client config environment")
  private ClientConfigSelector clientConfigSelector = ClientConfigSelector.AWS;

  @Parameter(names = "--job_client", description = "Job handler client implementation")
  private JobClientSelector jobClient = JobClientSelector.LOCAL_FILE;

  @Parameter(names = "--blob_storage_client", description = "Data client implementation")
  private BlobStorageClientSelector blobStorageClientSelector =
      BlobStorageClientSelector.AWS_S3_CLIENT;

  @Parameter(names = "--lifecycle_client", description = "Lifecycle client implementation")
  private LifecycleClientSelector lifecycleClient = LifecycleClientSelector.LOCAL;

  @Parameter(names = "--notification_client", description = "Notification client implementation")
  private NotificationClientSelector notificationClient = NotificationClientSelector.LOCAL;

  @Parameter(names = "--metric_client", description = "Metric client implementation")
  private MetricClientSelector metricClient = MetricClientSelector.LOCAL;

  @Parameter(names = "--param_client", description = "Parameter client implementation")
  private ParameterClientSelector paramClient = ParameterClientSelector.ARGS;

  @Parameter(names = "--result_logger", description = "How to log aggregation results")
  private ResultLoggerModuleSelector resultLoggerModuleSelector =
      ResultLoggerModuleSelector.LOCAL_TO_CLOUD;

  @Parameter(names = "--pbs_client", description = "PBS client implementation")
  private PrivacyBudgetClientSelector pbsclient = PrivacyBudgetClientSelector.LOCAL;

  @Parameter(
      names = "--local_file_single_puller_path",
      description =
          "Path to the local file provided by the local file single puller(flag makes "
              + "sense only if that puller is used")
  private String localFileSinglePullerPath = "";

  @Parameter(
      names = "--local_file_job_info_path",
      description = "Path to the local file to dump the job info to in local mode (empty for none)")
  private String localFileJobInfoPath = "";

  @Parameter(names = "--decryption_key_service", description = "How to read the decryption keys")
  private HybridEncryptionKeyServiceSelector hybridEncryptionKeyServiceSelector =
      HybridEncryptionKeyServiceSelector.LOCAL_FILE_DECRYPTION_KEY_SERVICE;

  @Parameter(
      names = "--local_file_decryption_key_path",
      description =
          "Path to the Tink KeysetHandle used for decryption."
              + " This is used only for the LocalFileHybridEncryptionKeyService.")
  private String localFileDecryptionKeyPath = "";

  // TODO(b/230378564): This should not be a hardcoded default url
  @Parameter(
      names = "--private_key_service_base_url",
      description =
          "Full URL (including protocol and api version path fragment) of the private key vending"
              + " service. Do not include trailing slash")
  private String privateKeyServiceUrl =
      "https://privatekeyservice-staging.aws.admcstesting.dev:443/v1alpha"; // "https://us-central1-adhcloud-tp1.cloudfunctions.net";

  @Parameter(
      names = "--primary_encryption_key_service_base_url",
      description =
          "Full URL (including protocol and api version path fragment) of the primary (Party A)"
              + " encryption key service base url service, used only for multi-party key hosting"
              + " service. Do not include trailing slash")
  private String primaryEncryptionKeyServiceBaseUrl = "";

  @Parameter(
      names = "--secondary_encryption_key_service_base_url",
      description =
          "Full URL (including protocol and api version path fragment) of the secondary (Party B)"
              + " encryption key service base url service, used only for multi-party key hosting"
              + " service. Do not include trailing slash")
  private String secondaryEncryptionKeyServiceBaseUrl = "";

  @Parameter(
      names = "--primary_encryption_key_service_cloudfunction_url",
      description =
          "Full URL of the primary (Party A) encryption key service cloudfunction service, used"
              + " only as audience for GCP authentication. This is temporary and will be replaced"
              + " by encryption key service url in the future. ")
  private String primaryEncryptionKeyServiceCloudfunctionUrl = "";

  @Parameter(
      names = "--secondary_encryption_key_service_cloudfunction_url",
      description =
          "Full URL of the secondary (Party B) encryption key service cloudfunction service, used"
              + " only as audience for GCP authentication.This is temporary and will be replaced by"
              + " encryption key service url in the future. ")
  private String secondaryEncryptionKeyServiceCloudfunctionUrl = "";

  @Parameter(
      names = "--coordinator_a_privacy_budgeting_service_base_url",
      description =
          "Full URL (including protocol and api version path fragment) of coordinator A's privacy"
              + " budgeting service. Do not include trailing slash")
  private String coordinatorAPrivacyBudgetingServiceUrl = null;

  @Parameter(
      names = "--coordinator_a_privacy_budgeting_service_auth_endpoint",
      description = "Auth endpoint of coordinator A's privacy budgeting service.")
  private String coordinatorAPrivacyBudgetingServiceAuthEndpoint = null;

  @Parameter(
      names = "--coordinator_b_privacy_budgeting_service_base_url",
      description =
          "Full URL (including protocol and api version path fragment) of coordinator B's privacy"
              + " budgeting service. Do not include trailing slash")
  private String coordinatorBPrivacyBudgetingServiceUrl = null;

  @Parameter(
      names = "--coordinator_b_privacy_budgeting_service_auth_endpoint",
      description = "Auth endpoint of coordinator B's privacy budgeting service.")
  private String coordinatorBPrivacyBudgetingServiceAuthEndpoint = null;

  @Parameter(
      names = "--coordinator_a_assume_role_arn",
      description =
          "ARN of the role assumed for performing operations in coordinator A. ARGS param"
              + " client should be selected to use this flag.")
  private String coordinatorARoleArn = "";

  @Parameter(
      names = "--coordinator_b_assume_role_arn",
      description =
          "ARN of the role assumed for performing operations in coordinator B. ARGS param"
              + " client should be selected to use this flag.")
  private String coordinatorBRoleArn = "";

  @Parameter(
      names = "--autoscaling_endpoint_override",
      description = "Optional auto scaling service endpoint override URI")
  private String autoScalingEndpointOverride = "";

  @Parameter(
      names = "--kms_endpoint_override",
      description = "KMS Endpoint used by worker in AwsKmsV2Client for PK decryption. ")
  private String kmsEndpointOverride = "";

  @Parameter(names = "--gcp_project_id", description = "Project ID. ")
  private String gcpProjectId = "";

  @Parameter(names = "--pubsub_topic_id", description = "GCP PubSub topic ID. ")
  private String pubSubTopicId = "aggregate-service-jobqueue";

  @Parameter(names = "--pubsub_subscription_id", description = "GCP PubSub subscription ID. ")
  private String pubSubSubscriptionId = "aggregate-service-jobqueue-sub";

  @Parameter(
      names = "--pubsub_endpoint",
      description =
          "GCP pubsub endpoint URL to override the default value. Empty value is ignored.")
  private String pubSubEndpoint = "";

  @Parameter(names = "--spanner_instance_id", description = "GCP Spanner instance ID. ")
  private String spannerInstanceId = "jobmetadatainstance";

  @Parameter(names = "--spanner_db_name", description = "GCP Spanner Database Name. ")
  private String spannerDbName = "jobmetadatadb";

  @Parameter(
      names = "--spanner_endpoint",
      description =
          "GCP Spanner endpoint URL to override the default value. Values that do not start with"
              + " \"https://\" are assumed to be emulators for testing. Empty value is ignored.")
  private String spannerEndpoint = "";

  @Parameter(
      names = "--gcs_endpoint",
      description = "GCS endpoint URL; defaults to using Production GCS if empty.")
  private String gcsEndpoint = "";

  @Parameter(
      names = "--coordinator_a_wip_provider",
      description = "Workload identity pool provider id. ")
  private String coordinatorAWipProvider = "";

  @Parameter(
      names = "--coordinator_a_sa",
      description = "Coordinator service account used for impersonation.")
  private String coordinatorAServiceAccount = "";

  @Parameter(
      names = "--coordinator_b_wip_provider",
      description = "Workload identity pool provider id. ")
  private String coordinatorBWipProvider = "";

  @Parameter(
      names = "--coordinator_b_sa",
      description = "Coordinator service account used for impersonation.")
  private String coordinatorBServiceAccount = "";

  @Parameter(
      names = "--kms_symmetric_key",
      description = "KMS SymmetricKey ARN. ARGS param client should be selected to use this flag.")
  private String kmsSymmetricKey =
      ""; // "gcp-kms://projects/adhcloud-tp1/locations/us/keyRings/keyring1/cryptoKeys/kek1";

  @Parameter(names = "--coordinator_a_kms_key", description = "KMS SymmetricKey ARN.")
  private String coodinatorAKmsKey =
      ""; // "gcp-kms://projects/adhcloud-tp1/locations/us/keyRings/keyring1/cryptoKeys/kek1";

  @Parameter(names = "--coordinator_b_kms_key", description = "KMS SymmetricKey ARN.")
  private String coodinatorBKmsKey =
      ""; // "gcp-kms://projects/adhcloud-tp1/locations/us/keyRings/keyring1/cryptoKeys/kek1";

  @Parameter(
      names = "--aws_sqs_queue_url",
      description =
          "(Optional) Queue url for AWS SQS, if AWS job client is used, if AWS job client is used"
              + " and ARGS param client is used.")
  private String awsSqsQueueUrl = "";

  @Parameter(
      names = "--max_job_num_attempts",
      description =
          "(Optional) Maximum number of times the job can be picked up by workers, if AWS job"
              + " client is used and ARGS param client is used.")
  private String maxJobNumAttempts = "5";

  @Parameter(
      names = "--jobqueue_message_visibility_timeout_seconds",
      description =
          "(Optional) Job queue message visibility timeout (in seconds), if AWS job client"
              + " is used and ARGS param client is used.")
  private String messageVisibilityTimeoutSeconds = "3600";

  @Parameter(
      names = "--scale-in-hook",
      description = "(Optional) Scale in hook used for scaling in the instance.")
  private String scaleInHook = "";

  @Parameter(
      names = "--aws_metadata_endpoint_override",
      description =
          "Optional ec2 metadata endpoint override URI. This is used to get EC2 metadata"
              + " information including tags, and profile credentials. If this parameter is set,"
              + " the instance credentials provider will be used.")
  private String awsMetadataEndpointOverride = "";

  @Parameter(
      names = "--ec2_endpoint_override",
      description = "Optional EC2 service endpoint override URI")
  private String ec2EndpointOverride = "";

  @Parameter(names = "--sqs_endpoint_override", description = "Optional Sqs Endpoint override URI")
  private String sqsEndpointOverride = "";

  @Parameter(names = "--ssm_endpoint_override", description = "Optional Ssm Endpoint override URI")
  private String ssmEndpointOverride = "";

  @Parameter(names = "--sts_endpoint_override", description = "Optional STS Endpoint override URI")
  private String stsEndpointOverride = "";

  @Parameter(
      names = "--aws_metadatadb_table_name",
      description =
          "(Optional) Table name for AWS Dynamodb storing job metadata, if AWS job client"
              + " is used, if AWS job client is used and ARGS param client is used.")
  private String awsMetadatadbTableName = "";

  @Parameter(names = "--ddb_endpoint_override", description = "Optional ddb endpoint override URI")
  private String ddbEndpointOverride = "";

  @Parameter(
      names = "--cloudwatch_endpoint_override",
      description = "Optional cloudwatch endpoint override URI")
  private String cloudwatchEndpointOverride = "";

  @Parameter(
      names = "--adtech_region_override",
      description = "Overrides the region of the compute instance.")
  private String adtechRegionOverride = "";

  @Parameter(
      names = "--coordinator_a_region_override",
      description = "Overrides the region of coordinator A's services.")
  // TODO: set default to us-east-1 once services move there.
  private String coordinatorARegionOverride = "us-west-2";

  @Parameter(
      names = "--coordinator_b_region_override",
      description = "Overrides the region of coordinator B's services.")
  // TODO: set default to us-east-1 once services move there.
  private String coordinatorBRegionOverride = "us-west-2";

  @Parameter(
      names = "--result_working_directory_path",
      description =
          "Path to a directory on the local filesystem to use as a working directory for writing"
              + " results before uploading to s3")
  private String resultWorkingDirectoryPath = "";

  @Parameter(
      names = "--simulation_inputs",
      description =
          "Set to true if running the aggregation worker on input from"
              + " java.com.aggregate.simulation. Note this should only be done in a test"
              + " environment")
  private boolean simulationInputs = false;

  @Parameter(names = "--s3_endpoint_override", description = "Optional S3 Endpoint override URI")
  private String s3EndpointOverride = "";

  @Parameter(
      names = "--access_key",
      description =
          "Optional access key for AWS credentials. If this parameter (and --secret_key) is set,"
              + " the static credentials provider will be used.")
  private String accessKey = "";

  @Parameter(
      names = "--secret_key",
      description =
          "Optional secret key for AWS credentials.  If this parameter (and --access_key) is set,"
              + " the static credentials provider will be used.")
  private String secretKey = "";

  @Parameter(
      names = "--gcp_instance_id_override",
      description = "Optional instance id for gce instance in GCP. Only for metric client use.")
  private String gcpInstanceIdOverride = "";

  @Parameter(
      names = "--gcp_instance_name_override",
      description =
          "Optional instance name for gce instance in GCP. Only for lifecycle client use.")
  private String gcpInstanceNameOverride = "";

  @Parameter(names = "--gcp_zone_override", description = "Optional GCP zone for gce instance.")
  private String gcpZoneOverride = "";

  @Parameter(
      names = "--test_encoded_keyset_handle",
      description =
          "Optional base64 encoded string that represents the keyset handle to retrieve an Aead."
              + " This is for coordinatorA if multi-party.")
  private String testEncodedKeysetHandle = "";

  @Parameter(
      names = "--test_coordinator_b_encoded_keyset_handle",
      description =
          "Optional base64 encoded string that represents the keyset handle to retrieve an Aead for"
              + " coordinatorB.")
  private String testCoordinatorBEncodedKeysetHandle = "";

  @Parameter(names = "--stopwatch_exporter", description = "Selector for stopwatch timer exporter")
  private StopwatchExporterSelector stopwatchExporterSelector = StopwatchExporterSelector.NO_OP;

  @Parameter(
      names = "--stopwatch_bucket_name",
      description = "Cloud bucket to write stopwatches to (relevant if CLOUD exporter is used).")
  private String stopwatchBucketName = "";

  @Parameter(
      names = "--stopwatch_key_name",
      description =
          "Cloud bucket key path for stopwatch data (relevant if CLOUD exporter is used).")
  private String stopwatchKeyName = "";

  @Parameter(names = "--benchmark", description = "Set to true to run in benchmark mode.")
  private boolean benchmark = false;

  ClientConfigSelector getClientConfigSelector() {
    return clientConfigSelector;
  }

  NotificationClientSelector getNotificationClient() {
    return notificationClient;
  }

  JobClientSelector getJobClient() {
    return jobClient;
  }

  ParameterClientSelector getParamClient() {
    return paramClient;
  }

  LifecycleClientSelector getLifecycleClient() {
    return lifecycleClient;
  }

  MetricClientSelector getMetricClient() {
    return metricClient;
  }

  ResultLoggerModuleSelector resultLoggerModuleSelector() {
    return resultLoggerModuleSelector;
  }

  String getLocalFileSinglePullerPath() {
    return localFileSinglePullerPath;
  }

  String getLocalFileJobInfoPath() {
    return localFileJobInfoPath;
  }

  String getAwsSqsQueueUrl() {
    return awsSqsQueueUrl;
  }

  String getScaleInHook() {
    return scaleInHook;
  }

  String getMaxJobNumAttempts() {
    return maxJobNumAttempts;
  }

  String getMessageVisibilityTimeoutSeconds() {
    return messageVisibilityTimeoutSeconds;
  }

  String getAwsMetadatadbTableName() {
    return awsMetadatadbTableName;
  }

  public URI getDdbEndpointOverride() {
    return URI.create(ddbEndpointOverride);
  }

  public URI getCloudwatchEndpointOverride() {
    return URI.create(cloudwatchEndpointOverride);
  }

  public String getAwsMetadataEndpointOverride() {
    return awsMetadataEndpointOverride;
  }

  public URI getEc2EndpointOverride() {
    return URI.create(ec2EndpointOverride);
  }

  public URI getSqsEndpointOverride() {
    return URI.create(sqsEndpointOverride);
  }

  public URI getSsmEndpointOverride() {
    return URI.create(ssmEndpointOverride);
  }

  public URI getStsEndpointOverride() {
    return URI.create(stsEndpointOverride);
  }

  public URI getKmsEndpointOverride() {
    return URI.create(kmsEndpointOverride);
  }

  public String getGcpProjectId() {
    return gcpProjectId;
  }

  public String getGcpInstanceIdOverride() {
    return gcpInstanceIdOverride;
  }

  public String getGcpInstanceNameOverride() {
    return gcpInstanceNameOverride;
  }

  public String getGcpZoneOverride() {
    return gcpZoneOverride;
  }

  public String getPubSubTopicId() {
    return pubSubTopicId;
  }

  public String getPubSubSubscriptionId() {
    return pubSubSubscriptionId;
  }

  public String getSpannerInstanceId() {
    return spannerInstanceId;
  }

  public String getSpannerDbName() {
    return spannerDbName;
  }

  public String getCoordinatorAWipProvider() {
    return coordinatorAWipProvider;
  }

  public String getCoordinatorAServiceAccount() {
    return coordinatorAServiceAccount;
  }

  public String getCoordinatorBWipProvider() {
    return coordinatorBWipProvider;
  }

  public String getCoordinatorBServiceAccount() {
    return coordinatorBServiceAccount;
  }

  public URI getAutoScalingEndpointOverride() {
    return URI.create(autoScalingEndpointOverride);
  }

  public String getKmsSymmetricKey() {
    return kmsSymmetricKey;
  }

  public String getCoodinatorAKmsKey() {
    return coodinatorAKmsKey;
  }

  public String getCoodinatorBKmsKey() {
    return coodinatorBKmsKey;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public HybridEncryptionKeyServiceSelector getHybridEncryptionKeyServiceSelector() {
    return hybridEncryptionKeyServiceSelector;
  }

  String getLocalFileDecryptionKeyPath() {
    return localFileDecryptionKeyPath;
  }

  String getPrivateKeyServiceBaseUrl() {
    return privateKeyServiceUrl;
  }

  String getPrimaryEncryptionKeyServiceBaseUrl() {
    return primaryEncryptionKeyServiceBaseUrl;
  }

  String getSecondaryEncryptionKeyServiceBaseUrl() {
    return secondaryEncryptionKeyServiceBaseUrl;
  }

  Optional<String> getPrimaryEncryptionKeyServiceCloudfunctionUrl() {
    return Optional.ofNullable(primaryEncryptionKeyServiceCloudfunctionUrl)
        .filter(id -> !id.isEmpty());
  }

  Optional<String> getSecondaryEncryptionKeyServiceCloudfunctionUrl() {
    return Optional.ofNullable(secondaryEncryptionKeyServiceCloudfunctionUrl)
        .filter(id -> !id.isEmpty());
  }

  String getCoordinatorAPrivacyBudgetingServiceUrl() {
    return coordinatorAPrivacyBudgetingServiceUrl;
  }

  String getCoordinatorAPrivacyBudgetingServiceAuthEndpoint() {
    return coordinatorAPrivacyBudgetingServiceAuthEndpoint;
  }

  String getCoordinatorBPrivacyBudgetingServiceUrl() {
    return coordinatorBPrivacyBudgetingServiceUrl;
  }

  String getCoordinatorBPrivacyBudgetingServiceAuthEndpoint() {
    return coordinatorBPrivacyBudgetingServiceAuthEndpoint;
  }

  String getCoordinatorARoleArn() {
    return coordinatorARoleArn;
  }

  String getCoordinatorBRoleArn() {
    return coordinatorBRoleArn;
  }

  String getAdtechRegionOverride() {
    return adtechRegionOverride;
  }

  String getCoordinatorARegionOverride() {
    return coordinatorARegionOverride;
  }

  String getCoordinatorBRegionOverride() {
    return coordinatorBRegionOverride;
  }

  String getResultWorkingDirectoryPathString() {
    return resultWorkingDirectoryPath;
  }

  public boolean isSimulationInputs() {
    return simulationInputs;
  }

  BlobStorageClientSelector getBlobStorageClientSelector() {
    return blobStorageClientSelector;
  }

  public URI getS3EndpointOverride() {
    return URI.create(s3EndpointOverride);
  }

  public Optional<String> getSpannerEndpoint() {
    return Optional.ofNullable(spannerEndpoint).filter(endpoint -> !endpoint.isEmpty());
  }

  public Optional<String> getPubSubEndpoint() {
    return Optional.ofNullable(pubSubEndpoint).filter(endpoint -> !endpoint.isEmpty());
  }

  public Optional<String> getGcsEndpoint() {
    return Optional.ofNullable(gcsEndpoint).filter(endpoint -> !endpoint.isEmpty());
  }

  @Beta
  public Optional<String> getTestEncodedKeysetHandle() {
    return Optional.ofNullable(testEncodedKeysetHandle).filter(s -> !s.isEmpty());
  }

  @Beta
  public Optional<String> getTestCoordinatorBEncodedKeysetHandle() {
    return Optional.ofNullable(testCoordinatorBEncodedKeysetHandle).filter(s -> !s.isEmpty());
  }

  public StopwatchExporterSelector getStopwatchExporterSelector() {
    return stopwatchExporterSelector;
  }

  public String getStopwatchBucketName() {
    return stopwatchBucketName;
  }

  public String getStopwatchKeyName() {
    return stopwatchKeyName;
  }

  public boolean getBenchmarkMode() {
    return benchmark;
  }
}
