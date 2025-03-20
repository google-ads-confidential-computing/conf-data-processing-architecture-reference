/**
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

environment = "dev-mp"
project_id  = "admcloud-adtech1"
region      = "us-central1"
region_zone = "us-central1-c"

# Multi region location
# https://cloud.google.com/storage/docs/locations
operator_package_bucket_location = "US"

spanner_instance_config              = "regional-us-central1"
spanner_processing_units             = 100
spanner_database_deletion_protection = false

worker_image = "us-docker.pkg.dev/admcloud-scp/docker-repo-dev/worker_app_mp_gcp:dev"
# Temporarily use the demo coordinator service until we are ready to integrate dev environments
allowed_operator_service_account = "allowedadtechuser@adhcloud-tp1.iam.gserviceaccount.com,allowedadtechuser@adhcloud-tp2.iam.gserviceaccount.com"
worker_logging_enabled           = true
instance_disk_image_family = {
  image_project = "confidential-space-images",
  image_family  = "confidential-space"
}

frontend_cloudfunction_use_java21_runtime     = true
notification_cloudfunction_use_java21_runtime = true
autoscaling_cloudfunction_use_java21_runtime  = true
