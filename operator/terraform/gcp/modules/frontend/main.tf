/**
 * Copyright 2022-2025 Google LLC
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

moved {
  from = google_service_account.frontend_service_account
  to   = google_service_account.frontend_service_account[0]
}

locals {
  cloudfunction_name_suffix = "frontend_service_cloudfunction"
  cloudfunction_package_zip = "${var.frontend_service_jar}.zip"

  use_provided_runtime_sa = (
    var.frontend_service_cloudfunction_runtime_sa_email != null
    && var.frontend_service_cloudfunction_runtime_sa_email != ""
  )

  runtime_service_account = (
    local.use_provided_runtime_sa
    ? var.frontend_service_cloudfunction_runtime_sa_email
    : google_service_account.frontend_service_account[0].email
  )

  service_environment_variables = {
    PROJECT_ID             = var.project_id
    INSTANCE_ID            = var.spanner_instance_name
    DATABASE_ID            = var.spanner_database_name
    PUBSUB_TOPIC_ID        = var.job_queue_topic
    PUBSUB_SUBSCRIPTION_ID = var.job_queue_sub
    JOB_METADATA_TTL       = var.job_metadata_table_ttl_days
    JOB_TABLE_NAME         = var.job_table_name
    JOB_VERSION            = var.job_version
  }

  create_multiple_cloud_run_frontends = length(var.frontend_service_cloud_run_regions) > 0

  lb_fe_https_custom_audience = (var.frontend_service_lb_domain != null && var.frontend_service_lb_domain != "") ? "https://${var.frontend_service_lb_domain}" : ""
}

resource "google_service_account" "frontend_service_account" {
  count = local.use_provided_runtime_sa ? 0 : 1
  # Service account id has a 30 character limit
  account_id   = "${var.environment}-frontend"
  display_name = "Frontend Service Account"
}

# Archives the JAR in a ZIP file
data "archive_file" "frontend_service_archive" {
  count       = var.frontend_service_zip == "" ? 1 : 0
  type        = "zip"
  source_file = var.frontend_service_jar
  output_path = local.cloudfunction_package_zip
}

resource "google_storage_bucket_object" "frontend_service_package_bucket_object" {
  count = var.frontend_service_zip == "" ? 1 : 0
  # Need hash in name so cloudfunction knows to redeploy when code changes
  name   = "${var.environment}_${local.cloudfunction_name_suffix}_${data.archive_file.frontend_service_archive[0].output_md5}"
  bucket = var.operator_package_bucket_name
  source = local.cloudfunction_package_zip
}

resource "google_cloudfunctions2_function" "frontend_service_cloudfunction" {
  name     = "${var.environment}-${var.region}-frontend-service"
  location = var.region

  build_config {
    runtime     = var.use_java21_runtime ? "java21" : "java11"
    entry_point = "com.google.scp.operator.frontend.service.gcp.FrontendServiceHttpFunction"
    source {
      storage_source {
        bucket = var.operator_package_bucket_name
        object = var.frontend_service_zip != "" ? var.frontend_service_zip : google_storage_bucket_object.frontend_service_package_bucket_object[0].name
      }
    }
  }

  service_config {
    min_instance_count               = var.frontend_service_cloudfunction_min_instances
    max_instance_count               = var.frontend_service_cloudfunction_max_instances
    max_instance_request_concurrency = var.frontend_service_cloudfunction_max_instance_request_concurrency
    timeout_seconds                  = var.frontend_service_cloudfunction_timeout_sec
    available_cpu                    = var.frontend_service_cloudfunction_num_cpus
    available_memory                 = "${var.frontend_service_cloudfunction_memory_mb}M"
    service_account_email            = local.runtime_service_account
    vpc_connector                    = length(var.vpc_connector_ids) == 0 ? null : var.vpc_connector_ids[var.region]
    vpc_connector_egress_settings    = length(var.vpc_connector_ids) == 0 ? null : "ALL_TRAFFIC"
    environment_variables            = local.service_environment_variables
  }

  labels = {
    environment = var.environment
  }
}

module "cloud_run_fe" {
  for_each = var.frontend_service_cloud_run_regions

  source                        = "../cloud_run_service"
  project                       = var.project_id
  environment                   = var.environment
  service_name                  = "${var.environment}-${each.key}-cr-frontend-service"
  deletion_protection           = var.frontend_service_cloud_run_deletion_protection
  region                        = each.key
  description                   = "Cloud Run handler for frontend service in region ${each.key}"
  source_container_image_url    = var.frontend_service_cloud_run_source_container_image_url
  environment_variables         = local.service_environment_variables
  min_instance_count            = var.frontend_service_cloudfunction_min_instances
  max_instance_count            = var.frontend_service_cloudfunction_max_instances
  concurrency                   = var.frontend_service_cloudfunction_max_instance_request_concurrency
  cpu_idle                      = var.frontend_service_cloud_run_cpu_idle
  startup_cpu_boost             = var.frontend_service_cloud_run_startup_cpu_boost
  cpu_count                     = var.frontend_service_cloudfunction_num_cpus
  memory_mb                     = var.frontend_service_cloudfunction_memory_mb
  timeout_seconds               = var.frontend_service_cloudfunction_timeout_sec
  runtime_service_account_email = local.runtime_service_account
  vpc_connector_id              = length(var.vpc_connector_ids) == 0 ? null : var.vpc_connector_ids[each.value]
  ingress_traffic_setting       = var.frontend_service_cloud_run_ingress_traffic_setting
  cloud_run_invoker_iam_members = var.frontend_service_cloud_run_allowed_invoker_iam_members
  binary_authorization          = var.frontend_service_cloud_run_binary_authorization
  custom_audiences              = setunion((local.lb_fe_https_custom_audience != "" ? [local.lb_fe_https_custom_audience] : []), var.frontend_service_cloud_run_custom_audiences)
}

# Reserve IP address for FE LB.
resource "google_compute_global_address" "cloud_run_fe_ip_address" {
  count = local.create_multiple_cloud_run_frontends ? 1 : 0

  project = var.project_id
  name    = "${var.environment}-fe-lb-ip-addr"
}

# Map custom URL to LB IP address
module "fe_service_dns_a_records" {
  count = local.create_multiple_cloud_run_frontends ? 1 : 0

  source = "../domain_a_records"

  parent_domain_name         = var.frontend_service_parent_domain_name
  parent_domain_name_project = var.frontend_service_parent_domain_name_project_id

  # Map with Key: Service domain and Value: External IP address."
  service_domain_to_address_map = {
    "${var.frontend_service_lb_domain}" = google_compute_global_address.cloud_run_fe_ip_address[0].address
  }
}

module "cloud_run_fe_load_balancer" {
  count = local.create_multiple_cloud_run_frontends ? 1 : 0

  source      = "../cloud_run_load_balancer"
  project     = var.project_id
  environment = var.environment
  backend_id  = "wrkr-fe"
  cloud_run_information = [for region, cr_info in module.cloud_run_fe :
    {
      service_name = cr_info.service_name
      region       = region
    }
  ]
  enable_backend_logging = var.frontend_service_enable_lb_backend_logging
  backend_service_paths  = var.frontend_service_lb_allowed_request_paths
  service_domain         = var.frontend_service_lb_domain

  external_ip_address = google_compute_global_address.cloud_run_fe_ip_address[0].address

  outlier_detection_interval_seconds                      = var.frontend_service_lb_outlier_detection_interval_seconds
  outlier_detection_base_ejection_time_seconds            = var.frontend_service_lb_outlier_detection_base_ejection_time_seconds
  outlier_detection_consecutive_errors                    = var.frontend_service_lb_outlier_detection_consecutive_errors
  outlier_detection_enforcing_consecutive_errors          = var.frontend_service_lb_outlier_detection_enforcing_consecutive_errors
  outlier_detection_consecutive_gateway_failure           = var.frontend_service_lb_outlier_detection_consecutive_gateway_failure
  outlier_detection_enforcing_consecutive_gateway_failure = var.frontend_service_lb_outlier_detection_enforcing_consecutive_gateway_failure
  outlier_detection_max_ejection_percent                  = var.frontend_service_lb_outlier_detection_max_ejection_percent

  depends_on = [
    module.fe_service_dns_a_records,
  ]
}

# JobMetadata read/write permissions
resource "google_spanner_database_iam_member" "frontend_service_jobmetadatadb_iam" {
  instance = var.spanner_instance_name
  database = var.spanner_database_name
  role     = "roles/spanner.databaseUser"
  member   = "serviceAccount:${local.runtime_service_account}"

  depends_on = [
    google_service_account.frontend_service_account
  ]
}

# JobQueue write permissions
resource "google_pubsub_topic_iam_member" "frontend_service_jobqueue_iam" {
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${local.runtime_service_account}"
  topic  = var.job_queue_topic

  depends_on = [
    google_service_account.frontend_service_account
  ]
}
