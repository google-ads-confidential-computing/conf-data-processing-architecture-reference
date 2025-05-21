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
  service_subdomain_suffix   = var.service_subdomain_suffix != null ? var.service_subdomain_suffix : "-${var.environment}"
  encryption_key_service_jar = var.encryption_key_service_jar != "" ? var.encryption_key_service_jar : "${module.bazel.bazel_bin}/java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp/EncryptionKeyServiceHttpCloudFunction_deploy.jar"
  key_storage_service_jar    = var.key_storage_service_jar != "" ? var.key_storage_service_jar : "${module.bazel.bazel_bin}/java/com/google/scp/coordinator/keymanagement/keystorage/service/gcp/KeyStorageServiceHttpCloudFunction_deploy.jar"
  key_storage_domain         = var.environment != "prod" ? "${var.key_storage_service_subdomain}${local.service_subdomain_suffix}.${var.parent_domain_name}" : "${var.key_storage_service_subdomain}.${var.parent_domain_name}"
  encryption_key_domain      = var.environment != "prod" ? "${var.encryption_key_service_subdomain}${local.service_subdomain_suffix}.${var.parent_domain_name}" : "${var.encryption_key_service_subdomain}.${var.parent_domain_name}"
  private_key_domain         = "${var.private_key_service_subdomain}${local.service_subdomain_suffix}.${var.parent_domain_name}"
  package_bucket_prefix      = "${var.project_id}_${var.environment}"
  package_bucket_name        = length("${local.package_bucket_prefix}_mpkhs_secondary_package_jars") <= 63 ? "${local.package_bucket_prefix}_mpkhs_secondary_package_jars" : "${local.package_bucket_prefix}_mpkhs_b_pkg"
  key_sets = flatten([
    for key_set in var.key_sets_config.key_sets : key_set.name
  ])
  kms_key_base_uri = "gcp-kms://${google_kms_key_ring.key_encryption_ring.id}/cryptoKeys/${var.environment}_$setName$_key_encryption_key"

  service_domain_to_address_map = var.delete_encryption_key_service ? {
    (local.key_storage_domain) : module.keystorageservice.load_balancer_ip,
    (local.private_key_domain) : module.private_key_service.loadbalancer_ip
    } : {
    (local.key_storage_domain) : module.keystorageservice.load_balancer_ip,
    (local.encryption_key_domain) : module.encryptionkeyservice[0].encryption_key_service_loadbalancer_ip,
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
resource "google_storage_bucket" "mpkhs_secondary_package_bucket" {
  # GCS names are globally unique
  name                        = local.package_bucket_name
  location                    = var.mpkhs_secondary_package_bucket_location
  uniform_bucket_level_access = true
}

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

module "key_management_service" {
  count  = var.location_new_key_ring == null ? 0 : 1
  source = "../../modules/key_management_service"

  environment           = var.environment
  project_id            = var.project_id
  location_new_key_ring = var.location_new_key_ring
  key_sets              = local.key_sets
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

  # Function vars
  key_encryption_key_id                           = google_kms_crypto_key.key_encryption_key.id
  disable_key_set_acl                             = var.disable_key_set_acl
  kms_key_base_uri                                = local.kms_key_base_uri
  package_bucket_name                             = var.use_tf_created_bucket_for_binary ? google_storage_bucket.mpkhs_secondary_package_bucket.name : var.mpkhs_secondary_package_bucket
  load_balancer_name                              = "keystoragelb"
  spanner_database_name                           = module.keydb.keydb_name
  spanner_instance_name                           = module.keydb.keydb_instance_name
  cloudfunction_timeout_seconds                   = var.cloudfunction_timeout_seconds
  key_storage_cloudfunction_name                  = "key-storage-cloudfunction"
  key_storage_service_jar                         = local.key_storage_service_jar
  key_storage_service_source_path                 = var.key_storage_service_source_path
  key_storage_cloudfunction_memory                = var.key_storage_service_cloudfunction_memory_mb
  key_storage_service_cloudfunction_min_instances = var.key_storage_service_cloudfunction_min_instances
  key_storage_service_cloudfunction_max_instances = var.key_storage_service_cloudfunction_max_instances
  use_java21_runtime                              = var.keystorageservice_use_java21_runtime

  # Domain Management
  key_storage_domain = local.key_storage_domain

  # Alarms
  alarms_enabled                                = var.alarms_enabled
  alarm_eval_period_sec                         = var.keystorageservice_alarm_eval_period_sec
  alarm_duration_sec                            = var.keystorageservice_alarm_duration_sec
  cloudfunction_5xx_threshold                   = var.keystorageservice_cloudfunction_5xx_threshold
  cloudfunction_error_ratio_threshold           = var.keystorageservice_cloudfunction_error_ratio_threshold
  cloudfunction_max_execution_time_max          = var.keystorageservice_cloudfunction_max_execution_time_max
  cloudfunction_alert_on_memory_usage_threshold = var.keystorageservice_cloudfunction_alert_on_memory_usage_threshold

  lb_5xx_threshold       = var.keystorageservice_lb_5xx_threshold
  lb_5xx_ratio_threshold = var.keystorageservice_lb_5xx_ratio_threshold
  lb_max_latency_ms      = var.keystorageservice_lb_max_latency_ms

  key_storage_severity_map = var.alert_severity_overrides
  depends_on = [
    google_kms_crypto_key.key_set_key_encryption_key,
    google_kms_crypto_key.key_encryption_key,
    google_kms_key_ring.key_encryption_ring
  ]
}

moved {
  from = module.private_key_service[0]
  to   = module.private_key_service
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

  # Spanner
  spanner_database_name      = module.keydb.keydb_name
  spanner_instance_name      = module.keydb.keydb_instance_name
  spanner_staleness_read_sec = var.spanner_staleness_read_sec

  # Alert settings
  alarms_enabled           = var.alarms_enabled
  alarm_eval_period_sec    = var.encryptionkeyservice_alarm_eval_period_sec
  alarm_duration_sec       = var.encryptionkeyservice_alarm_duration_sec
  alert_severity_overrides = var.alert_severity_overrides

  get_encrypted_private_key_general_error_threshold = var.get_encrypted_private_key_general_error_threshold

  cloud_run_5xx_threshold                   = var.encryptionkeyservice_cloudfunction_5xx_threshold
  cloud_run_alert_on_memory_usage_threshold = var.encryptionkeyservice_cloudfunction_alert_on_memory_usage_threshold
  cloud_run_error_ratio_threshold           = var.encryptionkeyservice_cloudfunction_error_ratio_threshold
  cloud_run_max_execution_time_max          = var.encryptionkeyservice_cloudfunction_max_execution_time_max

  lb_5xx_threshold       = var.encryptionkeyservice_lb_5xx_threshold
  lb_5xx_ratio_threshold = var.encryptionkeyservice_lb_5xx_ratio_threshold
  lb_max_latency_ms      = var.encryptionkeyservice_lb_max_latency_ms
}

module "encryptionkeyservice" {
  source = "../../modules/encryptionkeyservice"
  count  = var.delete_encryption_key_service ? 0 : 1

  project_id                        = var.project_id
  environment                       = var.environment
  regions                           = [var.primary_region]
  allowed_operator_user_group       = var.allowed_operator_user_group
  allowed_operator_service_accounts = module.allowed_operators.all_service_accounts

  # Function vars
  package_bucket_name                                = var.use_tf_created_bucket_for_binary ? google_storage_bucket.mpkhs_secondary_package_bucket.name : var.mpkhs_secondary_package_bucket
  spanner_database_name                              = module.keydb.keydb_name
  spanner_instance_name                              = module.keydb.keydb_instance_name
  cloudfunction_timeout_seconds                      = var.cloudfunction_timeout_seconds
  encryption_key_service_jar                         = local.encryption_key_service_jar
  encryption_key_service_source_path                 = var.encryption_key_service_source_path
  encryption_key_service_cloudfunction_memory_mb     = var.encryption_key_service_cloudfunction_memory_mb
  encryption_key_service_cloudfunction_min_instances = var.encryption_key_service_cloudfunction_min_instances
  encryption_key_service_cloudfunction_max_instances = var.encryption_key_service_cloudfunction_max_instances
  use_java21_runtime                                 = var.encryptionkeyservice_use_java21_runtime

  # Domain Management
  enable_domain_management = true
  encryption_key_domain    = local.encryption_key_domain

  # Alarms
  alarms_enabled                                = var.alarms_enabled
  alarm_eval_period_sec                         = var.encryptionkeyservice_alarm_eval_period_sec
  alarm_duration_sec                            = var.encryptionkeyservice_alarm_duration_sec
  cloudfunction_5xx_threshold                   = var.encryptionkeyservice_cloudfunction_5xx_threshold
  cloudfunction_error_ratio_threshold           = var.encryptionkeyservice_cloudfunction_error_ratio_threshold
  cloudfunction_max_execution_time_max          = var.encryptionkeyservice_cloudfunction_max_execution_time_max
  cloudfunction_alert_on_memory_usage_threshold = var.encryptionkeyservice_cloudfunction_alert_on_memory_usage_threshold

  lb_5xx_threshold       = var.encryptionkeyservice_lb_5xx_threshold
  lb_5xx_ratio_threshold = var.encryptionkeyservice_lb_5xx_ratio_threshold
  lb_max_latency_ms      = var.encryptionkeyservice_lb_max_latency_ms

  get_encrypted_private_key_general_error_threshold = var.get_encrypted_private_key_general_error_threshold
  encryption_key_service_severity_map               = var.alert_severity_overrides
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
