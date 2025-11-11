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
  alias   = "secondary"
  project = var.project_id
  region  = var.secondary_region
  zone    = var.secondary_region_zone
}

locals {
  private_key_service_addtional_environment = "${var.environment}-pre${var.environment}"

  service_subdomain_suffix      = var.service_subdomain_suffix != null ? var.service_subdomain_suffix : "-${var.environment}"
  key_storage_domain            = var.environment != "prod" ? "${var.key_storage_service_subdomain}${local.service_subdomain_suffix}.${var.parent_domain_name}" : "${var.key_storage_service_subdomain}.${var.parent_domain_name}"
  private_key_domain            = "${var.private_key_service_subdomain}${local.service_subdomain_suffix}.${var.parent_domain_name}"
  private_key_domain_additional = "${var.private_key_service_subdomain}-${local.private_key_service_addtional_environment}.${var.parent_domain_name}"
  key_sets = flatten([
    for key_set in var.key_sets_config.key_sets : key_set.name
  ])

  service_domain_to_address_map = (
    var.private_key_service_addon_container_image_url == ""
    ? {
      (local.key_storage_domain) : module.keystorageservice.load_balancer_ip,
      (local.private_key_domain) : module.private_key_service.loadbalancer_ip
    }
    : {
      (local.key_storage_domain) : module.keystorageservice.load_balancer_ip,
      (local.private_key_domain) : module.private_key_service.loadbalancer_ip
      (local.private_key_domain_additional) : module.private_key_service_addon.loadbalancer_ip
    }
  )

  # TODO: b/428770204 - Update kms base uri to the migration uri post 'generate' phase
  kms_key_base_uri           = "gcp-kms://${module.key_management_service.kms_key_ring_id}/cryptoKeys/${var.environment}_$setName$_kms_key"
  migration_kms_key_base_uri = "gcp-kms://${module.key_management_service.kms_key_ring_id}/cryptoKeys/${var.environment}_$setName$_kms_key"

  # Key Migration Tool Safety Preconditions
  # TODO: b/428770204 - remove disable_key_set_acl check once its dependency has been removed
  base_uris_are_different = (local.kms_key_base_uri != local.migration_kms_key_base_uri)
  key_migration_tool_safe_to_generate = (var.populate_migration_key_data
    && var.key_migration_tool_container_image_url != null
    && var.key_migration_tool_migrator_mode == "generate"
    && length(var.key_sets_vending_config.allowed_migrators) == 0
    && local.base_uris_are_different
    && var.migration_peer_coordinator_kms_key_base_uri != null
    && var.disable_key_set_acl
  )
  key_migration_tool_safe_to_migrate = (var.populate_migration_key_data
    && var.key_migration_tool_container_image_url != null
    && var.key_migration_tool_migrator_mode == "migrate"
    && length(var.key_sets_vending_config.allowed_migrators) > 0
    && !local.base_uris_are_different
    && !var.disable_key_set_acl
  )
  key_migration_tool_safe_to_cleanup = (!var.populate_migration_key_data
    && var.key_migration_tool_container_image_url != null
    && var.key_migration_tool_migrator_mode == "cleanup"
    && length(var.key_sets_vending_config.allowed_migrators) == 0
  )
}

resource "null_resource" "has_valid_migration_configuration" {
  # Ensures that if it is desired to populate the migration key data, that
  # a migration kek uri can be safely constructed.
  lifecycle {
    precondition {
      condition = (!var.populate_migration_key_data ||
        var.location_new_key_ring != null
      )
      error_message = <<EOF
Variable populate_migration_key_data is set to true, but the required migration
data is not available. To enable migration, provide a value for
'location_new_key_ring'.
EOF
    }
  }
}

resource "null_resource" "can_safely_run_key_migration_tool" {
  # Ensures general safety before running the migration tool.
  # Note: For true safety during 'migrate' mode, ALL keys or customers MUST be
  #       in 'key_sets_vending_config.allowed_migrators'
  lifecycle {
    precondition {
      condition = (!var.run_key_migration_tool
        || local.key_migration_tool_safe_to_generate
        || local.key_migration_tool_safe_to_migrate
        || local.key_migration_tool_safe_to_cleanup
      )
      error_message = <<EOF
Invalid configuration for running the Key Migration Tool. Please check the requirements for your chosen 'key_migration_tool_migrator_mode':
  - 'generate': 'populate_migration_key_data' must be true, 'key_sets_vending_config.allowed_migrators' must be empty, AND 'migration_peer_coordinator_kms_key_base_uri' must be provided.
  - 'migrate': 'populate_migration_key_data' must be true AND 'key_sets_vending_config.allowed_migrators' must NOT be empty.
  - 'cleanup': 'populate_migration_key_data' must be false AND 'key_sets_vending_config.allowed_migrators' must be empty.
EOF
    }
  }
}

module "vpc" {
  source = "../../modules/vpc"

  environment = var.environment
  project_id  = var.project_id
  regions     = toset([var.primary_region, var.secondary_region])
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

module "keystorageservice" {
  source = "../../modules/keystorageservice"

  project_id                   = var.project_id
  environment                  = var.environment
  region                       = var.primary_region
  allowed_wip_user_group       = var.allowed_wip_user_group
  allowed_wip_service_accounts = var.allowed_wip_service_accounts
  key_sets                     = local.key_sets
  key_encryption_key_ring_id   = google_kms_key_ring.key_encryption_ring.id

  # Migration to external managed LB
  load_balancing_scheme = var.key_storage_service_load_balancing_scheme

  # Function vars
  key_encryption_key_id             = google_kms_crypto_key.key_encryption_key.id
  disable_key_set_acl               = var.disable_key_set_acl
  populate_migration_key_data       = var.populate_migration_key_data
  kms_key_base_uri                  = local.kms_key_base_uri
  migration_kms_key_base_uri        = local.migration_kms_key_base_uri
  spanner_database_name             = module.keydb.keydb_name
  spanner_instance_name             = module.keydb.keydb_instance_name
  key_storage_memory                = var.key_storage_service_memory_mb
  key_storage_service_min_instances = var.key_storage_service_min_instances
  key_storage_service_max_instances = var.key_storage_service_max_instances
  source_container_image_url        = var.key_storage_service_container_image_url

  # Domain Management
  key_storage_domain = local.key_storage_domain

  # Alarms
  alarms_enabled                            = var.alarms_enabled
  alarm_eval_period_sec                     = var.key_storage_service_alarm_eval_period_sec
  alarm_duration_sec                        = var.key_storage_service_alarm_duration_sec
  cloud_run_5xx_threshold                   = var.key_storage_service_cloud_run_5xx_threshold
  cloud_run_max_execution_time_max          = var.key_storage_service_cloud_run_max_execution_time_max
  cloud_run_alert_on_memory_usage_threshold = var.key_storage_service_cloud_run_alert_on_memory_usage_threshold

  lb_5xx_threshold       = var.key_storage_service_lb_5xx_threshold
  lb_5xx_ratio_threshold = var.key_storage_service_lb_5xx_ratio_threshold
  lb_max_latency_ms      = var.key_storage_service_lb_max_latency_ms

  key_storage_severity_map = var.alert_severity_overrides
  depends_on = [
    google_kms_crypto_key.key_set_key_encryption_key,
    google_kms_crypto_key.key_encryption_key,
    google_kms_key_ring.key_encryption_ring
  ]
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

  # Migration to external managed LB
  load_balancing_scheme = var.private_key_service_load_balancing_scheme

  allowed_invoker_service_account_emails = module.allowed_operators.all_service_accounts
  allowed_operator_user_group            = var.allowed_operator_user_group
  source_container_image_url             = var.private_key_service_container_image_url

  # Cloud Run settings
  cpu_count          = var.private_key_service_cloud_run_cpu_count
  memory_mb          = var.private_key_service_cloud_run_memory_mb
  concurrency        = var.private_key_service_cloud_run_concurrency
  max_instance_count = var.private_key_service_cloud_run_max_instances
  min_instance_count = var.private_key_service_cloud_run_min_instances

  # Spanner configs
  spanner_database_name      = module.keydb.keydb_name
  spanner_instance_name      = module.keydb.keydb_instance_name
  spanner_staleness_read_sec = var.spanner_staleness_read_sec

  # Vending parameters
  enable_cache             = var.enable_private_key_service_cache
  cache_refresh_in_minutes = var.private_key_service_cache_refresh_in_minutes
  key_sets_vending_config  = var.key_sets_vending_config
  key_sets_config          = var.key_sets_config

  # Alert settings
  alarms_enabled           = var.alarms_enabled
  alarm_eval_period_sec    = var.private_key_service_alarm_eval_period_sec
  alarm_duration_sec       = var.private_key_service_alarm_duration_sec
  alert_severity_overrides = var.alert_severity_overrides

  get_encrypted_private_key_general_error_threshold = var.get_encrypted_private_key_general_error_threshold
  exception_alert_threshold                         = var.private_key_service_exception_alert_threshold
  config_read_alert_threshold                       = var.private_key_service_config_read_alert_threshold

  cloud_run_5xx_threshold                   = var.private_key_service_cloud_run_5xx_threshold
  cloud_run_alert_on_memory_usage_threshold = var.private_key_service_cloud_run_alert_on_memory_usage_threshold
  cloud_run_max_execution_time_max          = var.private_key_service_cloud_run_max_execution_time_max

  lb_5xx_threshold       = var.private_key_service_lb_5xx_threshold
  lb_5xx_ratio_threshold = var.private_key_service_lb_5xx_ratio_threshold
  lb_max_latency_ms      = var.private_key_service_lb_max_latency_ms
}

module "private_key_service_addon" {
  count  = var.private_key_service_addon_container_image_url == "" ? 0 : 1
  source = "../private_key_service"

  environment = local.private_key_service_addtional_environment
  project_id  = var.project_id
  regions = concat(
    [var.primary_region, var.secondary_region],
    var.private_key_service_additional_regions
  )

  service_domain        = local.private_key_domain_additional
  display_identifier    = "Pre${var.environment}"
  load_balancing_scheme = "EXTERNAL_MANAGED"

  allowed_invoker_service_account_emails = module.allowed_operators.all_service_accounts
  allowed_operator_user_group            = var.allowed_operator_user_group
  source_container_image_url             = var.private_key_service_addon_container_image_url

  # Cloud Run settings
  cpu_count          = var.private_key_service_cloud_run_cpu_count
  memory_mb          = var.private_key_service_cloud_run_memory_mb
  concurrency        = var.private_key_service_cloud_run_concurrency
  max_instance_count = var.private_key_service_cloud_run_max_instances
  min_instance_count = var.private_key_service_cloud_run_min_instances

  # Spanner configs
  spanner_database_name      = module.keydb.keydb_name
  spanner_instance_name      = module.keydb.keydb_instance_name
  spanner_staleness_read_sec = var.spanner_staleness_read_sec

  # Vending parameters
  enable_cache             = var.enable_private_key_service_cache
  cache_refresh_in_minutes = var.private_key_service_cache_refresh_in_minutes
  key_sets_vending_config  = var.key_sets_vending_config
  key_sets_config          = var.key_sets_config

  # Alert settings
  alarms_enabled           = var.alarms_enabled
  alarm_eval_period_sec    = var.private_key_service_alarm_eval_period_sec
  alarm_duration_sec       = var.private_key_service_alarm_duration_sec
  alert_severity_overrides = var.private_key_service_addon_alert_severity_overrides

  get_encrypted_private_key_general_error_threshold = var.get_encrypted_private_key_general_error_threshold
  exception_alert_threshold                         = var.private_key_service_exception_alert_threshold
  config_read_alert_threshold                       = var.private_key_service_config_read_alert_threshold

  cloud_run_5xx_threshold                   = var.private_key_service_cloud_run_5xx_threshold
  cloud_run_alert_on_memory_usage_threshold = var.private_key_service_cloud_run_alert_on_memory_usage_threshold
  cloud_run_max_execution_time_max          = var.private_key_service_cloud_run_max_execution_time_max

  lb_5xx_threshold       = var.private_key_service_lb_5xx_threshold
  lb_5xx_ratio_threshold = var.private_key_service_lb_5xx_ratio_threshold
  lb_max_latency_ms      = var.private_key_service_lb_max_latency_ms
}

module "key_migration_tool" {
  source = "../key_migration_tool"
  count  = var.run_key_migration_tool ? 1 : 0

  environment = var.environment
  project_id  = var.project_id
  region      = var.primary_region

  source_container_image_url = var.key_migration_tool_container_image_url

  # Cloud Run Job settings
  cpu_count            = var.key_migration_tool_cpu_count
  memory_mb            = var.key_migration_tool_memory_mb
  max_retries          = var.key_migration_tool_max_retries
  task_timeout_seconds = var.key_migration_tool_task_timeout_seconds

  # Spanner and access configs
  spanner_database_name = module.keydb.keydb_name
  spanner_instance_name = module.keydb.keydb_instance_name

  # Key Rings
  legacy_migration_key_ring_id = google_kms_key_ring.key_encryption_ring.id
  migration_key_ring_id        = module.key_management_service.kms_key_ring_id

  # Environment variables
  migration_kek_base_uri      = local.migration_kms_key_base_uri
  migration_peer_kek_base_uri = var.migration_peer_coordinator_kms_key_base_uri
  migration_key_sets          = var.key_migration_tool_key_sets
  migrator_mode               = var.key_migration_tool_migrator_mode
  dry_run                     = var.key_migration_tool_dry_run
}

module "domain_a_records" {
  source = "../../modules/domain_a_records"

  primary_region      = var.primary_region
  primary_region_zone = var.primary_region_zone

  parent_domain_name         = var.parent_domain_name
  parent_domain_name_project = var.parent_domain_name_project

  service_domain_to_address_map = local.service_domain_to_address_map
}

# Coordinator WIP Provider
module "workload_identity_pool" {
  source                                          = "../../modules/workloadidentitypoolprovider"
  project_id                                      = var.wipp_project_id_override == null ? var.project_id : var.wipp_project_id_override
  wip_allowed_service_account_project_id_override = var.wip_allowed_service_account_project_id_override == null ? var.project_id : var.wip_allowed_service_account_project_id_override
  environment                                     = var.environment

  # Limited to 32 characters
  workload_identity_pool_id          = "${var.environment}-cowip"
  workload_identity_pool_description = "Workload Identity Pool to manage Peer KEK access using attestation."

  # Limited to 32 characters
  workload_identity_pool_provider_id = "${var.environment}-cowip-pvdr"
  wip_provider_description           = "WIP Provider to manage OIDC tokens and attestation."

  # Limited to 30 characters
  wip_verified_service_account_id           = "${var.environment}-coverifiedusr"
  wip_verified_service_account_display_name = "${var.environment} Verified Coordinator User"

  # Limited to 30 characters
  wip_allowed_service_account_id           = "${var.environment}-coallowedusr"
  wip_allowed_service_account_display_name = "${var.environment} Allowed Coordinator User"

  key_encryption_key_id                        = google_kms_crypto_key.key_encryption_key.id
  allowed_wip_user_group                       = var.allowed_wip_user_group
  allowed_wip_service_accounts                 = var.allowed_wip_service_accounts
  enable_attestation                           = var.enable_attestation
  assertion_tee_swname                         = var.assertion_tee_swname
  assertion_tee_support_attributes             = var.assertion_tee_support_attributes
  assertion_tee_container_image_reference_list = var.assertion_tee_container_image_reference_list
  assertion_tee_container_image_hash_list      = var.assertion_tee_container_image_hash_list
}

### KMS
## Current Key Ring
# Cloud KMS encryption ring and key encryption key (KEK)
resource "google_kms_key_ring" "key_encryption_ring" {
  name     = "${var.environment}_key_encryption_ring"
  location = "us"
}

resource "google_kms_crypto_key" "key_encryption_key" {
  name     = "${var.environment}_key_encryption_key"
  key_ring = google_kms_key_ring.key_encryption_ring.id

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_kms_crypto_key" "key_set_key_encryption_key" {
  for_each = toset(local.key_sets)
  name     = "${var.environment}_${each.value}_key_encryption_key"
  key_ring = google_kms_key_ring.key_encryption_ring.id

  lifecycle {
    prevent_destroy = true
  }
}

# Allow Verified Service Account to encrypt with given KEK
resource "google_kms_crypto_key_iam_member" "workload_identity_member" {
  crypto_key_id = google_kms_crypto_key.key_encryption_key.id
  role          = "roles/cloudkms.cryptoKeyEncrypter"
  member        = "serviceAccount:${module.workload_identity_pool.wip_verified_service_account}"
  depends_on    = [module.workload_identity_pool]
}

resource "google_kms_crypto_key_iam_member" "key_set_level_workload_identity_member" {
  for_each      = google_kms_crypto_key.key_set_key_encryption_key
  crypto_key_id = each.value.id
  member        = "serviceAccount:${module.workload_identity_pool.wip_verified_service_account}"
  role          = "roles/cloudkms.cryptoKeyEncrypter"
  depends_on = [
    module.workload_identity_pool,
    google_kms_crypto_key.key_set_key_encryption_key
  ]
}

module "allowed_operators" {
  source = "../../modules/allowed_operators"

  environment           = var.environment
  key_encryption_key_id = google_kms_crypto_key.key_encryption_key.id
  allowed_operators     = var.allowed_operators
  key_ring_id           = google_kms_key_ring.key_encryption_ring.id
}

# Key set acl kek pool
module "key_set_acl_kek_pool" {
  for_each = var.allowed_operators
  source   = "../../modules/allowed_operator_pool"

  environment                = var.environment
  key_encryption_key_ring_id = google_kms_key_ring.key_encryption_ring.id
  key_encryption_key_id      = google_kms_crypto_key.key_encryption_key.id

  key_sets           = toset(each.value.key_sets)
  global_key_ring_id = module.key_management_service.kms_key_ring_id

  allowed_operator = each.value
  pool_name        = each.key
}

## New Key Ring
moved {
  from = module.key_management_service[0]
  to   = module.key_management_service
}
module "key_management_service" {
  source = "../../modules/key_management_service"

  environment           = var.environment
  project_id            = var.project_id
  service_account_email = module.keystorageservice.key_storage_service_account_email
  location_new_key_ring = var.location_new_key_ring
  key_sets              = local.key_sets
}

resource "google_kms_crypto_key_iam_member" "key_set_wip_sa" {
  for_each      = module.key_management_service.kms_key_ids
  crypto_key_id = each.value
  member        = "serviceAccount:${module.workload_identity_pool.wip_verified_service_account}"
  role          = "roles/cloudkms.cryptoKeyEncrypter"
  depends_on    = [module.workload_identity_pool, module.key_management_service]
}
