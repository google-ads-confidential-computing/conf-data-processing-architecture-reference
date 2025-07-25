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

provider "google" {
  project = var.project_id
  region  = var.primary_region
  zone    = var.primary_region_zone
}

provider "google" {
  alias   = "domain"
  project = var.project_id
  region  = var.secondary_region
  zone    = var.secondary_region_zone
}

locals {
  get_public_key_service_jar = var.get_public_key_service_jar != "" ? var.get_public_key_service_jar : "${module.bazel.bazel_bin}/java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp/PublicKeyServiceHttpCloudFunction_deploy.jar"
  encryption_key_service_jar = var.encryption_key_service_jar != "" ? var.encryption_key_service_jar : "${module.bazel.bazel_bin}/java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp/EncryptionKeyServiceHttpCloudFunction_deploy.jar"
  service_subdomain_suffix   = var.service_subdomain_suffix != null ? var.service_subdomain_suffix : "-${var.environment}"
  public_key_domain          = var.environment != "prod" ? "${var.public_key_service_subdomain}${local.service_subdomain_suffix}.${var.parent_domain_name}" : "${var.public_key_service_subdomain}.${var.parent_domain_name}"
  private_key_domain         = "${var.private_key_service_subdomain}${local.service_subdomain_suffix}.${var.parent_domain_name}"
  package_bucket_prefix      = "${var.project_id}_${var.environment}"
  package_bucket_name        = length("${local.package_bucket_prefix}_mpkhs_primary_package_jars") <= 63 ? "${local.package_bucket_prefix}_mpkhs_primary_package_jars" : "${local.package_bucket_prefix}_mpkhs_a_pkg"
  key_sets = flatten([
    for key_set in var.key_sets_config.key_sets : key_set.name
  ])

  service_domain_to_address_map = {
    (local.public_key_domain) : module.public_key_service_load_balancer.loadbalancer_ip,
    (local.private_key_domain) : module.private_key_service.loadbalancer_ip
  }
}

module "bazel" {
  source = "../../modules/bazel"
}

module "vpc" {
  source = "../../modules/vpc"

  environment = var.environment
  project_id  = var.project_id
  regions     = toset([var.primary_region, var.secondary_region])
}

# Storage bucket containing cloudfunction JARs
resource "google_storage_bucket" "mpkhs_primary_package_bucket" {
  # GCS names are globally unique
  name                        = local.package_bucket_name
  location                    = var.mpkhs_primary_package_bucket_location
  uniform_bucket_level_access = true
}

module "keydb" {
  source                                     = "../../modules/keydb"
  project_id                                 = var.project_id
  environment                                = var.environment
  spanner_instance_config                    = var.spanner_instance_config
  spanner_processing_units                   = var.spanner_processing_units
  custom_configuration_name                  = var.spanner_custom_configuration_name
  custom_configuration_display_name          = var.spanner_custom_configuration_display_name
  custom_configuration_base_config           = var.spanner_custom_configuration_base_config
  custom_configuration_read_replica_location = var.spanner_custom_configuration_read_replica_location
}

module "keygenerationservice" {
  source = "../../modules/keygenerationservice"

  project_id  = var.project_id
  environment = var.environment
  network     = module.vpc.network
  zones = [
    var.primary_region_zone, var.secondary_region_zone
  ]
  allow_stopping_for_update      = var.key_generation_allow_stopping_for_update
  egress_internet_tag            = module.vpc.egress_internet_tag
  key_gen_instance_force_replace = var.key_gen_instance_force_replace

  # Data args
  key_generation_image              = var.key_generation_image
  spanner_database_name             = module.keydb.keydb_name
  spanner_instance_name             = module.keydb.keydb_instance_name
  key_generation_logging_enabled    = var.key_generation_logging_enabled
  key_generation_monitoring_enabled = var.key_generation_monitoring_enabled

  # Business Rules Args
  key_generation_cron_schedule  = var.key_generation_cron_schedule
  key_generation_cron_time_zone = var.key_generation_cron_time_zone
  key_generation_tee_allowed_sa = var.key_generation_tee_allowed_sa

  # TEE Args
  instance_disk_image               = var.instance_disk_image
  multiparty                        = true
  key_generation_tee_restart_policy = var.key_generation_tee_restart_policy

  # Monitoring Args
  alarms_enabled                  = var.alarms_enabled
  keydb_instance_name             = module.keydb.keydb_instance_name
  key_generation_alignment_period = var.key_generation_alignment_period
  undelivered_messages_threshold  = var.key_generation_undelivered_messages_threshold
  key_generation_error_threshold  = var.key_generation_error_threshold

  key_sets = local.key_sets
}

### KMS
## Current Key Ring
# Legacy kek pool
module "allowed_operators" {
  source = "../../modules/allowed_operators"

  environment           = var.environment
  key_encryption_key_id = module.keygenerationservice.key_encryption_key_id
  allowed_operators     = var.allowed_operators
  key_ring_id           = module.keygenerationservice.key_ring_id
}

# Key set acl kek pool
module "key_set_acl_kek_pool" {
  for_each = var.allowed_operators
  source   = "../../modules/allowed_operator_pool"

  environment                = var.environment
  key_encryption_key_ring_id = module.keygenerationservice.key_ring_id
  key_encryption_key_id      = module.keygenerationservice.key_encryption_key_id

  key_sets           = toset(each.value.key_sets)
  global_kms_key_ids = var.location_new_key_ring == null ? {} : module.key_management_service[0].kms_key_ids

  allowed_operator = each.value
  pool_name        = each.key
}

## New Key Ring
module "key_management_service" {
  count  = var.location_new_key_ring == null ? 0 : 1
  source = "../../modules/key_management_service"

  environment           = var.environment
  project_id            = var.project_id
  service_account_email = module.keygenerationservice.key_generation_service_account
  location_new_key_ring = var.location_new_key_ring
  key_sets              = local.key_sets
}

module "publickeyhostingservice" {
  source = "../../modules/publickeyhostingservice"

  project_id  = var.project_id
  environment = var.environment
  regions     = [var.primary_region, var.secondary_region]
  service_id  = "get-public-key"
  ssl_cert_id = "public-key"

  # Function vars
  package_bucket_name                        = var.use_tf_created_bucket_for_binary ? google_storage_bucket.mpkhs_primary_package_bucket.name : var.mpkhs_primary_package_bucket
  spanner_database_name                      = module.keydb.keydb_name
  spanner_instance_name                      = module.keydb.keydb_instance_name
  cloudfunction_timeout_seconds              = var.cloudfunction_timeout_seconds
  get_public_key_service_jar                 = local.get_public_key_service_jar
  get_public_key_service_source_path         = var.get_public_key_service_source_path
  get_public_key_cloudfunction_memory_mb     = var.public_key_service_memory_mb
  get_public_key_cloudfunction_min_instances = var.public_key_service_min_instances
  get_public_key_cloudfunction_max_instances = var.public_key_service_max_instances
  use_java21_runtime                         = var.publickeyservice_use_java21_runtime

  # Domain Management
  public_key_domain = local.public_key_domain

  # Alarms
  alarms_enabled                                      = var.alarms_enabled
  alarm_eval_period_sec                               = var.public_key_service_alarm_eval_period_sec
  alarm_duration_sec                                  = var.public_key_service_alarm_duration_sec
  get_public_key_cloudfunction_5xx_threshold          = var.public_key_service_5xx_threshold
  get_public_key_cloudfunction_max_execution_time_max = var.public_key_service_max_execution_time_max
  cloudfunction_alert_on_memory_usage_threshold       = var.publickeyservice_cloudfunction_alert_on_memory_usage_threshold

  lb_max_latency_ms      = var.public_key_service_lb_max_latency_ms
  lb_5xx_threshold       = var.public_key_service_lb_5xx_threshold
  lb_5xx_ratio_threshold = var.public_key_service_lb_5xx_ratio_threshold

  get_public_key_empty_key_set_error_threshold = var.public_key_service_empty_key_set_error_threshold
  get_public_key_general_error_threshold       = var.public_key_service_general_error_threshold
  public_key_alerts_severity_overrides         = var.alert_severity_overrides
}

module "public_key_service_dashboard" {
  source = "../../modules/public_key_service"

  environment         = var.environment
  project_id          = var.project_id
  service_name_suffix = "public-ks-cr"

  cloud_function_ids = module.publickeyhostingservice.cloud_function_ids
  load_balancer_name = module.public_key_service_load_balancer.loadbalancer_name

  # Alerts
  alarms_enabled                = var.alarms_enabled
  alarm_eval_period_sec         = var.public_key_service_alarm_eval_period_sec
  alarm_duration_sec            = var.public_key_service_alarm_duration_sec
  empty_key_set_error_threshold = var.public_key_service_empty_key_set_error_threshold
  general_error_threshold       = var.public_key_service_general_error_threshold
  alerts_severity_overrides     = var.alert_severity_overrides
}

module "public_key_service_load_balancer" {
  source = "../../modules/load_balancer"

  environment     = var.environment
  project_id      = var.project_id
  service_id      = "get-public-key"
  ssl_cert_id     = "public-key"
  monitoring_name = "Public Key Service"

  cloud_run_ids = (var.public_key_service_use_only_cr
    ? module.public_key_service.cloud_run_ids
    : concat(module.publickeyhostingservice.cloud_function_ids, module.public_key_service.cloud_run_ids)
  )

  enable_cdn                    = var.enable_public_key_service_cdn
  cdn_default_ttl_seconds       = var.public_key_service_cdn_default_ttl_seconds
  cdn_max_ttl_seconds           = var.public_key_service_cdn_max_ttl_seconds
  cdn_serve_while_stale_seconds = var.public_key_service_cdn_serve_while_stale_seconds

  regions_to_exclude_from_lb = var.public_key_service_regions_to_exclude_from_lb

  # Custom url/domain
  managed_domain = local.public_key_domain

  # Alert settings
  alarms_enabled           = var.alarms_enabled
  alarm_eval_period_sec    = var.public_key_service_alarm_eval_period_sec
  alarm_duration_sec       = var.public_key_service_alarm_duration_sec
  alert_severity_overrides = var.alert_severity_overrides
  lb_max_latency_ms        = var.public_key_service_lb_max_latency_ms
  lb_5xx_threshold         = var.public_key_service_lb_5xx_threshold
  lb_5xx_ratio_threshold   = var.public_key_service_lb_5xx_ratio_threshold
}

moved {
  from = module.public_key_service[0]
  to   = module.public_key_service
}

module "public_key_service" {
  source = "../public_key_service"

  environment = var.environment
  project_id  = var.project_id
  regions = (var.public_key_service_use_only_cr
    ? concat([var.primary_region, var.secondary_region], var.public_key_service_cr_regions)
    : var.public_key_service_cr_regions
  )
  service_domain = local.public_key_domain

  source_container_image_url = var.public_key_service_container_image_url

  # Cloud Run settings
  cpu_count          = var.public_key_service_cloud_run_cpu_count
  concurrency        = var.public_key_service_max_cloud_run_concurrency
  memory_mb          = var.public_key_service_memory_mb
  min_instance_count = var.public_key_service_min_instances
  max_instance_count = var.public_key_service_max_instances

  # Spanner
  spanner_database_name = module.keydb.keydb_name
  spanner_instance_name = module.keydb.keydb_instance_name

  # Alert
  alarms_enabled           = var.alarms_enabled
  alarm_eval_period_sec    = var.public_key_service_alarm_eval_period_sec
  alarm_duration_sec       = var.public_key_service_alarm_duration_sec
  alert_severity_overrides = var.alert_severity_overrides

  cloud_run_5xx_threshold                   = var.public_key_service_5xx_threshold
  cloud_run_max_execution_time_max          = var.public_key_service_max_execution_time_max
  cloud_run_alert_on_memory_usage_threshold = var.publickeyservice_cloudfunction_alert_on_memory_usage_threshold
}

module "private_key_service" {
  source = "../private_key_service"

  environment = var.environment
  project_id  = var.project_id
  regions = concat(
    [var.primary_region, var.secondary_region],
    var.private_key_service_additional_regions
  )
  service_domain = local.private_key_domain

  allowed_invoker_service_account_emails = module.allowed_operators.all_service_accounts
  allowed_operator_user_group            = var.allowed_operator_user_group
  source_container_image_url             = var.private_key_service_container_image_url

  # Cloud Run settings
  cpu_count          = var.private_key_service_cloud_run_cpu_count
  memory_mb          = var.encryption_key_service_cloudfunction_memory_mb
  concurrency        = var.private_key_service_cloud_run_concurrency
  max_instance_count = var.encryption_key_service_cloudfunction_max_instances
  min_instance_count = var.encryption_key_service_cloudfunction_min_instances

  # Spanner and access configs
  spanner_database_name      = module.keydb.keydb_name
  spanner_instance_name      = module.keydb.keydb_instance_name
  spanner_staleness_read_sec = var.spanner_staleness_read_sec
  enable_cache               = var.enable_private_key_service_cache
  key_sets_vending_config    = var.key_sets_vending_config

  # Alert settings
  alarms_enabled           = var.alarms_enabled
  alarm_eval_period_sec    = var.encryptionkeyservice_alarm_eval_period_sec
  alarm_duration_sec       = var.encryptionkeyservice_alarm_duration_sec
  alert_severity_overrides = var.alert_severity_overrides

  get_encrypted_private_key_general_error_threshold = var.get_encrypted_private_key_general_error_threshold

  cloud_run_5xx_threshold                   = var.encryptionkeyservice_cloudfunction_5xx_threshold
  cloud_run_alert_on_memory_usage_threshold = var.encryptionkeyservice_cloudfunction_alert_on_memory_usage_threshold
  cloud_run_max_execution_time_max          = var.encryptionkeyservice_cloudfunction_max_execution_time_max

  lb_5xx_threshold       = var.encryptionkeyservice_lb_5xx_threshold
  lb_5xx_ratio_threshold = var.encryptionkeyservice_lb_5xx_ratio_threshold
  lb_max_latency_ms      = var.encryptionkeyservice_lb_max_latency_ms
}

module "domain_a_records" {
  source = "../../modules/domain_a_records"

  primary_region      = var.primary_region
  primary_region_zone = var.primary_region_zone

  parent_domain_name         = var.parent_domain_name
  parent_domain_name_project = var.parent_domain_name_project

  service_domain_to_address_map = local.service_domain_to_address_map
}

# parameters

module "keydb_instance_id" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "SPANNER_INSTANCE"
  parameter_value = module.keydb.keydb_instance_name
}

module "keydb_name" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "KEY_DB_NAME"
  parameter_value = module.keydb.keydb_name
}

# TODO: b/428770204 - This URI should no longer be used in GCP post migration.
module "kms_key_uri" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "KMS_KEY_URI"
  parameter_value = "gcp-kms://${module.keygenerationservice.key_encryption_key_id}"
}

module "pubsub_id" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "SUBSCRIPTION_ID"
  parameter_value = module.keygenerationservice.subscription_id
}

module "key_generation_count" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "NUMBER_OF_KEYS_TO_CREATE"
  parameter_value = var.key_generation_count
}

module "key_generation_validity_in_days" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "KEYS_VALIDITY_IN_DAYS"
  parameter_value = var.key_generation_validity_in_days
}

module "key_generation_ttl_in_days" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "KEY_TTL_IN_DAYS"
  parameter_value = var.key_generation_ttl_in_days
}

# TODO: b/428770204 - This URI should no longer be used in GCP post migration.
module "peer_coordinator_kms_key_uri" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "PEER_COORDINATOR_KMS_KEY_URI"
  parameter_value = "gcp-kms://${var.peer_coordinator_kms_key_uri}"
}

module "key_storage_service_base_url" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "KEY_STORAGE_SERVICE_BASE_URL"
  parameter_value = var.key_storage_service_base_url
}

# TODO: b/275758643
module "key_storage_service_cloudfunction_url" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "KEY_STORAGE_SERVICE_CLOUDFUNCTION_URL"
  parameter_value = var.key_storage_service_cloudfunction_url
}

module "peer_coordinator_wip_provider" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "PEER_COORDINATOR_WIP_PROVIDER"
  parameter_value = var.peer_coordinator_wip_provider
}

module "peer_coordinator_service_account" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "PEER_COORDINATOR_SERVICE_ACCOUNT"
  parameter_value = var.peer_coordinator_service_account
}

module "key_id_type" {
  count           = var.key_id_type == "" ? 0 : 1
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "KEY_ID_TYPE"
  parameter_value = var.key_id_type
}

module "key_sets_config" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "KEY_SETS_CONFIG"
  parameter_value = jsonencode(var.key_sets_config)
}

module "populate_migration_key_data" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "POPULATE_MIGRATION_KEY_DATA"
  parameter_value = var.populate_migration_key_data
}

# TODO: b/428770204 - Remove this flag post migration.
module "disable_key_set_acl" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "DISABLE_KEY_SET_ACL"
  parameter_value = var.disable_key_set_acl
}

# TODO: b/428770204 - Parameter value should change to migration_kms_key_ring_uri parameter value post migration.
module "kms_key_ring_uri" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "KMS_KEY_BASE_URI"
  parameter_value = "gcp-kms://${module.keygenerationservice.key_ring_id}/cryptoKeys/${var.environment}_$setName$_key_encryption_key"
}

module "migration_kms_key_ring_uri" {
  source         = "../../modules/parameters"
  environment    = var.environment
  parameter_name = "MIGRATION_KMS_KEY_BASE_URI"
  parameter_value = (var.location_new_key_ring == null || !var.populate_migration_key_data
    ? "gcp-kms://${module.keygenerationservice.key_ring_id}/cryptoKeys/${var.environment}_$setName$_key_encryption_key"
  : "gcp-kms://${module.key_management_service[0].kms_key_ring.id}/cryptoKeys/${var.environment}_$setName$_key")
}

module "peer_coordinator_kms_key_ring_uri" {
  source          = "../../modules/parameters"
  environment     = var.environment
  parameter_name  = "PEER_COORDINATOR_KMS_KEY_BASE_URI"
  parameter_value = var.peer_coordinator_kms_key_base_uri
}

module "migration_peer_coordinator_kms_key_ring_uri" {
  source         = "../../modules/parameters"
  environment    = var.environment
  parameter_name = "MIGRATION_PEER_COORDINATOR_KMS_KEY_BASE_URI"
  parameter_value = (var.migration_peer_coordinator_kms_key_base_uri == null || !var.populate_migration_key_data
    ? var.peer_coordinator_kms_key_base_uri
  : var.migration_peer_coordinator_kms_key_base_uri)
}
