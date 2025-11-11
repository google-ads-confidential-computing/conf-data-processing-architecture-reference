# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google-beta"
      version = ">= 4.36"
    }
  }
}

locals {
  service_id          = "key-ss-service"
  service_name_suffix = "key-ss-cr"
}

resource "google_service_account" "key_storage_service_account" {
  # Service account id has a 30 character limit
  account_id   = "${var.environment}-keystorageuser"
  display_name = "KeyStorage Service Account"
}

module "cloud_run" {
  source = "../cloud_run"

  environment         = var.environment
  project             = var.project_id
  region              = var.region
  description         = "Key Storage Service Cloud Run"
  service_name_suffix = local.service_name_suffix
  service_domain      = var.key_storage_domain

  # Access variables
  runtime_service_account_email          = google_service_account.key_storage_service_account.email
  allowed_all_users                      = false
  allowed_invoker_service_account_emails = var.allowed_wip_service_accounts
  allowed_user_group                     = var.allowed_wip_user_group
  ingress                                = "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"

  # CR settings
  source_container_image_url = var.source_container_image_url
  concurrency                = 2
  cpu_count                  = 2
  min_instance_count         = var.key_storage_service_min_instances
  max_instance_count         = var.key_storage_service_max_instances
  memory_mb                  = var.key_storage_memory

  environment_variables = {
    PROJECT_ID                  = var.project_id
    GCP_KMS_URI                 = "gcp-kms://${var.key_encryption_key_id}"
    SPANNER_INSTANCE            = var.spanner_instance_name
    SPANNER_DATABASE            = var.spanner_database_name
    GCP_KMS_BASE_URI            = var.kms_key_base_uri
    MIGRATION_GCP_KMS_BASE_URI  = var.migration_kms_key_base_uri
    POPULATE_MIGRATION_KEY_DATA = var.populate_migration_key_data
    DISABLE_KEY_SET_ACL         = var.disable_key_set_acl
  }

  # Alert settings
  alarms_enabled           = var.alarms_enabled
  alert_name_suffix        = "Key Storage Service"
  alarm_eval_period_sec    = var.alarm_eval_period_sec
  alarm_duration_sec       = var.alarm_duration_sec
  alert_severity_overrides = var.key_storage_severity_map

  cloud_run_5xx_threshold                   = var.cloud_run_5xx_threshold
  cloud_run_alert_on_memory_usage_threshold = var.cloud_run_alert_on_memory_usage_threshold
  cloud_run_max_execution_time_max          = var.cloud_run_max_execution_time_max
}

locals {
  cloud_run_ids = [
    {
      name     = module.cloud_run.name
      location = module.cloud_run.location
    }
  ]
}

module "load_balancer" {
  source = "../../modules/load_balancer"

  environment     = var.environment
  project_id      = var.project_id
  service_id      = local.service_id
  ssl_cert_id     = "key-ss"
  monitoring_name = "Key Storage Service"

  # Migration to external managed LB
  load_balancing_scheme = var.load_balancing_scheme

  enable_cdn                    = false
  cdn_default_ttl_seconds       = 30
  cdn_max_ttl_seconds           = 60
  cdn_serve_while_stale_seconds = 60

  cloud_run_ids = local.cloud_run_ids

  # Custom url/domain
  managed_domain = var.key_storage_domain

  # Alert settings
  alarms_enabled           = var.alarms_enabled
  alarm_eval_period_sec    = var.alarm_eval_period_sec
  alarm_duration_sec       = var.alarm_duration_sec
  alert_severity_overrides = var.key_storage_severity_map
  lb_5xx_threshold         = var.lb_5xx_threshold
  lb_5xx_ratio_threshold   = var.lb_5xx_ratio_threshold
  lb_max_latency_ms        = var.lb_max_latency_ms
}

# IAM entry for key storage service account to use the database
resource "google_spanner_database_iam_member" "keydb_iam_policy" {
  instance = var.spanner_instance_name
  database = var.spanner_database_name
  role     = "roles/spanner.databaseUser"
  member   = "serviceAccount:${google_service_account.key_storage_service_account.email}"
}

# IAM entry to allow function to encrypt and decrypt using KMS
resource "google_kms_crypto_key_iam_member" "kms_key_set_level_iam_policy" {
  for_each      = toset(var.key_sets)
  crypto_key_id = "${var.key_encryption_key_ring_id}/cryptoKeys/${var.environment}_${each.value}_key_encryption_key"
  role          = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  member        = "serviceAccount:${google_service_account.key_storage_service_account.email}"
}

resource "google_kms_crypto_key_iam_member" "kms_iam_policy" {
  crypto_key_id = var.key_encryption_key_id
  role          = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  member        = "serviceAccount:${google_service_account.key_storage_service_account.email}"
}

module "service_monitoring_dashboard" {
  source                           = "../shared/service_dashboards"
  environment                      = var.environment
  service_name                     = "Key Storage Service"
  load_balancer_url_map_name_regex = ".*${local.service_id}-loadbalancer"
  service_name_regex               = ".*${local.service_name_suffix}"
  log_based_metric_service_name    = "key_storage_service"
}
