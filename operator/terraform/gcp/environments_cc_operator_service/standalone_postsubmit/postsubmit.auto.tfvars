/**
 * Copyright 2025 Google LLC
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

environment = "s-cc-postsubmit"
project_id  = "admcloud-adtech1"
region      = "us-central1"
region_zone = "us-central1-c"

operator_package_bucket_location = "US"
spanner_instance_config          = "regional-us-central1"
spanner_processing_units         = 300

worker_image                     = "us-docker.pkg.dev/admcloud-scp/docker-repo-dev/worker_app_mp_gcp:s-cc-postsubmit"
worker_logging_enabled           = true
worker_container_log_redirect    = "true"
worker_memory_monitoring_enabled = true
worker_instance_force_replace    = true
instance_disk_image_family = {
  image_project = "confidential-space-images",
  image_family  = "confidential-space"
}
# Needs to be a stable image for proper coordinator attestation
instance_disk_image = "projects/confidential-space-images/global/images/confidential-space-241000"
# SA allow listed in coordinator attestation, as well as worker identity
user_provided_worker_sa_email        = "s-cc-postsubmit-worker@admcloud-adtech1.iam.gserviceaccount.com"
spanner_database_deletion_protection = false
max_worker_instances                 = 1
alarms_enabled                       = true
custom_metrics_alarms_enabled        = true
alarms_notification_email            = "fakeemail@google.com"

auto_create_subnetworks = false
network_name_suffix     = "network-with-custom-subnet"
worker_subnet_cidr      = { "us-central1" = "10.2.0.0/16" }

worker_scale_in_jar  = "/tmp/standalone_postsubmit/jars/WorkerScaleInCloudFunction_deploy.jar"
frontend_service_jar = "/tmp/standalone_postsubmit/jars/FrontendServiceHttpCloudFunction_deploy.jar"

frontend_service_cloudfunction_runtime_sa_email   = "s-cc-postsubmit-fe@admcloud-adtech1.iam.gserviceaccount.com"
frontend_service_cloudfunction_use_java21_runtime = true
autoscaling_cloudfunction_use_java21_runtime      = true
