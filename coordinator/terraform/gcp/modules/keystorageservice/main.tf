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
      version = "7.15"
    }
  }
}

locals {
  service_id          = "key-ss-service"
  service_name_suffix = "key-ss-cr"
}

module "service_account" {
  source                = "../service_account"
  project_id            = var.project_id
  environment           = var.environment
  sa_display_name       = "KeyStorage"
  sa_account_id         = "keystorageuser"
  spanner_database_name = var.spanner_database_name
  spanner_instance_name = var.spanner_instance_name
  spanner_role          = "roles/spanner.databaseUser"
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
  runtime_service_account_email          = module.service_account.service_account_email
  allowed_all_users                      = false
  allowed_invoker_service_account_emails = var.allowed_wip_service_accounts
  allowed_user_group                     = var.allowed_wip_user_group
  ingress                                = "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"

  # CR settings
  source_container_image_url = var.source_container_image_url
  concurrency                = 2
  cpu_count                  = 2
  cpu_idle                   = true
  min_instance_count         = var.min_instances
  max_instance_count         = var.max_instances
  execution_environment      = var.execution_environment
  memory_mb                  = var.key_storage_memory

  environment_variables = {
    PROJECT_ID                  = var.project_id
    SPANNER_INSTANCE            = var.spanner_instance_name
    SPANNER_DATABASE            = var.spanner_database_name
    GCP_KMS_BASE_URI            = var.kms_key_base_uri
    MIGRATION_GCP_KMS_BASE_URI  = var.migration_kms_key_base_uri
    POPULATE_MIGRATION_KEY_DATA = var.populate_migration_key_data
  }

  # Key Storage Service does not use canary deployments
  enable_revision_pinning   = false
  stable_revision           = null
  canary_revision           = null
  canary_traffic_percentage = 0

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
  protocol        = "HTTP2"
  ssl_cert_id     = "key-ss"
  monitoring_name = "Key Storage Service"

  load_balancer_allowed_paths = var.load_balancer_allowed_paths

  # Outlier Detection
  lb_outlier_detection_enabled                               = var.lb_outlier_detection_enabled
  lb_outlier_detection_consecutive_errors                    = var.lb_outlier_detection_consecutive_errors
  lb_outlier_detection_interval_seconds                      = var.lb_outlier_detection_interval_seconds
  lb_outlier_detection_base_ejection_time_seconds            = var.lb_outlier_detection_base_ejection_time_seconds
  lb_outlier_detection_max_ejection_percent                  = var.lb_outlier_detection_max_ejection_percent
  lb_outlier_detection_enforcing_consecutive_errors          = var.lb_outlier_detection_enforcing_consecutive_errors
  lb_outlier_detection_consecutive_gateway_failure           = var.lb_outlier_detection_consecutive_gateway_failure
  lb_outlier_detection_enforcing_consecutive_gateway_failure = var.lb_outlier_detection_enforcing_consecutive_gateway_failure

  # Key Storage Service is not publicly accessible, so we are not configuring a Cloud Armor Security Policy for it.
  lb_security_policy         = null
  enable_cloud_armor_configs = false
  # The Cloud Armor alerts are disabled for key storage service, but we still need to define the thresholds.
  cloud_armor_high_block_ratio_threshold         = 1
  cloud_armor_rate_limit_denials_alert_threshold = 1000

  enable_cdn                    = false
  cdn_default_ttl_seconds       = 30
  cdn_max_ttl_seconds           = 60
  cdn_serve_while_stale_seconds = 60

  cloud_run_ids = local.cloud_run_ids

  # Custom url/domain
  managed_domain = var.key_storage_domain

  # Alert settings
  alarm_eval_period_sec    = var.alarm_eval_period_sec
  alarm_duration_sec       = var.alarm_duration_sec
  alert_severity_overrides = var.key_storage_severity_map
  lb_5xx_threshold         = var.lb_5xx_threshold
  lb_max_latency_ms        = var.lb_max_latency_ms
}

module "service_monitoring_dashboard" {
  source                        = "../service_dashboards"
  environment                   = var.environment
  service_name                  = "Key Storage Service"
  load_balancer_url_map_name    = "${var.environment}-${local.service_id}-loadbalancer"
  service_name_regex            = ".*${local.service_name_suffix}"
  log_based_metric_service_name = "key_storage_service"
}
