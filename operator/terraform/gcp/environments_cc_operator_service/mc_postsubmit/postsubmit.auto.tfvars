/**
 * Copyright 2024 Google LLC
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

# Example values required by operator_service.tf
#
# These values should be modified for each of your environments.

environment = "mc-postsubmit"
project_id  = "admcloud-adtech1"
region      = "us-central1"
region_zone = "us-central1-c"

operator_package_bucket_location = "US"
spanner_instance_config          = "regional-us-central1"
spanner_processing_units         = 300

worker_image                     = "us-docker.pkg.dev/admcloud-scp/docker-repo-dev/worker_app_mp_gcp:mc-postsubmit"
allowed_operator_service_account = ""
worker_logging_enabled           = true
worker_container_log_redirect    = "true"
worker_memory_monitoring_enabled = true
worker_instance_force_replace    = true
instance_disk_image_family = {
  image_project = "confidential-space-images",
  image_family  = "confidential-space"
}
user_provided_worker_sa_email        = "mc-postsubmit-worker@admcloud-adtech1.iam.gserviceaccount.com"
spanner_database_deletion_protection = false
max_worker_instances                 = 1
alarms_enabled                       = true
custom_metrics_alarms_enabled        = true
alarms_notification_email            = "fakeemail@google.com"
job_lifecycle_helper_parameter_values = {
  job_lifecycle_helper_retry_limit                     = "3",
  job_lifecycle_helper_visibility_timeout_extend_time  = "30",
  job_lifecycle_helper_job_processing_timeout          = "120",
  job_lifecycle_helper_job_extending_worker_sleep_time = "15",
  job_lifecycle_helper_enable_metric_recording         = true,
  job_lifecycle_helper_metric_namespace                = "mc-postsubmit",
}
metric_client_parameter_values = {
  enable_remote_metric_aggregation    = false,
  enable_native_metric_aggregation    = false,
  metric_exporter_interval_in_ms      = null,
  enable_batch_recording              = true,
  namespace_for_batch_recording       = "mc-postsubmit",
  batch_recording_time_duration_in_ms = "5000",
}

auto_create_subnetworks = false
network_name_suffix     = "network-with-custom-subnet"
worker_subnet_cidr      = { "us-central1" = "10.2.0.0/16" }

# TODO: uncomment them when switch to use build and deploy script.
# worker_scale_in_jar  = "/tmp/mc_postsubmit/oper_tar/jars/WorkerScaleInCloudFunction_deploy.jar"
# frontend_service_jar = "/tmp/mc_postsubmit/oper_tar/jars/FrontendServiceHttpCloudFunction_deploy.jar"

frontend_service_cloudfunction_runtime_sa_email   = "mc-postsubmit-fe@admcloud-adtech1.iam.gserviceaccount.com"
frontend_service_cloudfunction_use_java21_runtime = true
autoscaling_cloudfunction_use_java21_runtime      = true
