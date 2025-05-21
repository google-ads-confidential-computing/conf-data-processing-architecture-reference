# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

################################################################################
# Global Variables.
################################################################################

variable "project_id" {
  description = "GCP Project ID in which this module will be created."
  type        = string
}

variable "wipp_project_id_override" {
  description = "Override the GCP Project ID in which workload identity pool (wip) and workload identity pool provider (wipp) will be created in."
  type        = string
}

variable "wip_allowed_service_account_project_id_override" {
  description = "Override the GCP Project ID in which wip allowed service account will be created in."
  type        = string
}

variable "environment" {
  description = "Description for the environment, e.g. dev, staging, production."
  type        = string
}

variable "primary_region" {
  description = "Region where all services will be created."
  type        = string
}

variable "primary_region_zone" {
  description = "Region zone where all services will be created."
  type        = string
}

variable "secondary_region" {
  description = "Region where all services will be replicated."
  type        = string
}

variable "secondary_region_zone" {
  description = "Region zone where all services will be replicated."
  type        = string
}

variable "mpkhs_secondary_package_bucket_location" {
  description = "Location for multiparty keyhosting packages. Example: 'US'."
  type        = string
}

variable "mpkhs_secondary_package_bucket" {
  description = "GCS bucket for multiparty keyhosting packages."
  type        = string
}

variable "use_tf_created_bucket_for_binary" {
  description = "To indicate using google storage created by terraform."
  type        = bool
}

################################################################################
# Global Alarm Variables.
################################################################################

variable "alarms_enabled" {
  description = "Enable alarms for mpkhs services."
  type        = bool
}

################################################################################
# Spanner Variables.
################################################################################

variable "spanner_instance_config" {
  description = "Config value for the Spanner Instance."
  type        = string
}

variable "spanner_processing_units" {
  description = "Spanner's compute capacity. 1000 processing units = 1 node and must be set as a multiple of 100."
  type        = number
}

variable "spanner_staleness_read_sec" {
  description = "Acceptable read staleness in seconds."
  type        = string
}

variable "spanner_custom_configuration_name" {
  description = "Name for the custom spanner configuration to be created."
  type        = string
  nullable    = true
}

variable "spanner_custom_configuration_display_name" {
  description = "Display name for the custom spanner configuration to be created."
  type        = string
}

variable "spanner_custom_configuration_base_config" {
  description = "Base spanner configuration used as starting basis for custom configuraiton."
  type        = string
}

variable "spanner_custom_configuration_read_replica_location" {
  description = "Region used in custom configuration as an additional read replica."
  type        = string
}

################################################################################
# Routing Variables.
################################################################################

variable "parent_domain_name" {
  description = "Custom domain name to use with key hosting APIs."
  type        = string
}

variable "parent_domain_name_project" {
  description = "Project ID where custom domain name hosted zone is located."
  type        = string
}

variable "service_subdomain_suffix" {
  description = "When set, the value replaces `-$${var.environment}` as the service subdomain suffix."
  type        = string
}

variable "encryption_key_service_subdomain" {
  description = "Subdomain to use with parent_domain_name to designate the encryption key service."
  type        = string
}

variable "key_storage_service_subdomain" {
  description = "Subdomain to use with parent_domain_name to designate the key storage service."
  type        = string
}

################################################################################
# Cloud Function Variables.
################################################################################

variable "cloudfunction_timeout_seconds" {
  description = "Number of seconds after which a function instance times out."
  type        = number
}

### Private Key Service

variable "private_key_service_additional_regions" {
  description = "Additional regions beyond primary and secondary that Private KS will run in."
  type        = list(string)
  nullable    = false
}

variable "private_key_service_subdomain" {
  description = "Subdomain to use with parent_domain_name to designate the private key service."
  type        = string
}

variable "private_key_service_container_image_url" {
  description = "The full path (registry + tag) to the container image used to deploy EKS."
  type        = string
  nullable    = false
}

variable "private_key_service_cloud_run_cpu_count" {
  description = "Number of cpus to allocate for private key service cloud run instance."
  type        = number
}

variable "private_key_service_cloud_run_concurrency" {
  description = "Request concurrency for private key service cloud run instance."
  type        = number
}

### EKS
variable "delete_encryption_key_service" {
  description = "Flag to control removal of the EKS."
  type        = bool
}

variable "encryption_key_service_cloudfunction_memory_mb" {
  description = "Memory size in MB for encryption key cloud function."
  type        = number
}

variable "encryption_key_service_cloudfunction_min_instances" {
  description = "The minimum number of function instances that may coexist at a given time."
  type        = number
}

variable "encryption_key_service_cloudfunction_max_instances" {
  description = "The maximum number of function instances that may coexist at a given time."
  type        = number
}

variable "encryption_key_service_jar" {
  description = <<-EOT
          Encryption key service cloud function path. If not provided defaults to locally built jar file.
        Build with `bazel build //coordinator/terraform/gcp/applications/multipartykeyhosting_secondary:all`.
      EOT
  type        = string
}

variable "encryption_key_service_source_path" {
  description = "GCS path to Encryption Key Service source archive in the package bucket."
  type        = string
}

variable "encryptionkeyservice_use_java21_runtime" {
  description = "Whether to use the Java 21 runtime for the cloud function. If false will use Java 11."
  type        = bool
  nullable    = false
}

### Key Storage
variable "key_storage_service_jar" {
  description = <<-EOT
          Key storage service cloud function path. If not provided defaults to locally built jar file.
        Build with `bazel build //coordinator/terraform/gcp/applications/multipartykeyhosting_secondary:all`.
      EOT
  type        = string
}

variable "key_storage_service_source_path" {
  description = "GCS path to Key Storage Service source archive in the package bucket."
  type        = string
}

variable "key_storage_service_cloudfunction_memory_mb" {
  description = "Memory size in MB for key storage cloud function."
  type        = number
}

variable "key_storage_service_cloudfunction_min_instances" {
  description = "The minimum number of function instances that may coexist at a given time."
  type        = number
}

variable "key_storage_service_cloudfunction_max_instances" {
  description = "The maximum number of function instances that may coexist at a given time."
  type        = number
}

variable "keystorageservice_use_java21_runtime" {
  description = "Whether to use the Java 21 runtime for the cloud function. If false will use Java 11."
  type        = bool
  nullable    = false
}

variable "location_new_key_ring" {
  description = "Location for the global key ring."
  type        = string
  nullable    = true
}

################################################################################
# Key Management Variables.
################################################################################

# TODO: Manage group in terraform
variable "allowed_wip_user_group" {
  description = "Google Group to manage allowed coordinator users."
  type        = string
  default     = null
}

variable "allowed_wip_service_accounts" {
  description = "List of allowed coordinator service accounts."
  type        = list(string)
  default     = []
}

variable "allowed_operator_user_group" {
  description = "Google group of allowed operators to which to give API access."
  type        = string
  default     = null
}

variable "enable_attestation" {
  description = "Enable attestation requirement for this Workload Identity Pool. Assertion TEE vars required."
  type        = bool
}

variable "assertion_tee_swname" {
  description = "Software Running TEE. Example: 'CONFIDENTIAL_SPACE'."
  type        = string
}

variable "assertion_tee_support_attributes" {
  description = "Required support attributes of the Confidential Space image. Example: \"STABLE\", \"LATEST\". Empty for debug images."
  type        = list(string)
}

variable "assertion_tee_container_image_reference_list" {
  description = "List of acceptable image names TEE can run."
  type        = list(string)
}

variable "assertion_tee_container_image_hash_list" {
  description = "List of acceptable binary hashes TEE can run."
  type        = list(string)
}

variable "allowed_operators" {
  description = <<EOT
  Allowed operators. For attestation, only one of image reference, image digest, or image signature need to match.

  Attributes:
    key                           (string) - The name of the operators set.
    value.service_accounts        (string) - The list of service account emails used by the operators.
    value.image_references        (string) - The list of allowed image references for attested access.
    value.image_digests           (string) - The list of allowed image digests for attested access.
    value.image_signature_key_ids (string) - The list of allowed signature key IDs for attested access.
  EOT
  type = map(object({
    # TODO Leverage optional() when upgrade Terraform to > 1.3.
    service_accounts        = list(string)
    key_sets                = list(string)
    image_references        = list(string)
    image_digests           = list(string)
    image_signature_key_ids = list(string)
  }))
  default = {}
}

################################################################################
# Key Storage Alarm Variables.
################################################################################

variable "keystorageservice_alarm_eval_period_sec" {
  description = "Amount of time (in seconds) for alarm evaluation. Example: '60'."
  type        = string
}

variable "keystorageservice_cloudfunction_error_ratio_threshold" {
  description = "Error ratio greater than this to send alarm. Must be in decimal form: 10% = 0.10. Example: '0.0'."
  type        = number
}

variable "keystorageservice_cloudfunction_max_execution_time_max" {
  description = "Max execution time in ms to send alarm. Example: 9999."
  type        = number
}

variable "keystorageservice_cloudfunction_5xx_threshold" {
  description = "Cloud Function 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "keystorageservice_cloudfunction_alert_on_memory_usage_threshold" {
  description = "Memory usage of the Cloud Function should be higher than this value to alert."
  type        = number
}

variable "keystorageservice_lb_max_latency_ms" {
  description = "Load Balancer max latency to send alarm. Measured in milliseconds. Example: 5000."
  type        = string
}

variable "keystorageservice_lb_5xx_threshold" {
  description = "Load Balancer 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "keystorageservice_lb_5xx_ratio_threshold" {
  description = "Load Balancer ratio of 5xx/all requests greater than this to send alarm. Example: 0."
  type        = number
}

variable "keystorageservice_alarm_duration_sec" {
  description = "Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'."
  type        = number
}

################################################################################
# Encryption Key Service Alarm Variables.
################################################################################

variable "encryptionkeyservice_alarm_eval_period_sec" {
  description = "Amount of time (in seconds) for alarm evaluation. Example: '60'."
  type        = string
}

variable "encryptionkeyservice_cloudfunction_error_ratio_threshold" {
  description = "Error ratio greater than this to send alarm. Must be in decimal form: 10% = 0.10. Example: '0.0'."
  type        = number
}

variable "encryptionkeyservice_cloudfunction_max_execution_time_max" {
  description = "Max execution time in ms to send alarm. Example: 9999."
  type        = number
}

variable "encryptionkeyservice_cloudfunction_5xx_threshold" {
  description = "Cloud Function 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "encryptionkeyservice_cloudfunction_alert_on_memory_usage_threshold" {
  description = "Memory usage of the Cloud Function should be higher than this value to alert."
  type        = number
}

variable "encryptionkeyservice_lb_max_latency_ms" {
  description = "Load Balancer max latency to send alarm. Measured in milliseconds. Example: 5000."
  type        = string
}

variable "encryptionkeyservice_lb_5xx_threshold" {
  description = "Load Balancer 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "encryptionkeyservice_lb_5xx_ratio_threshold" {
  description = "Load Balancer ratio of 5xx/all requests greater than this to send alarm. Example: 0."
  type        = number
}

variable "encryptionkeyservice_alarm_duration_sec" {
  description = "Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'."
  type        = number
}

variable "get_encrypted_private_key_general_error_threshold" {
  description = "Get Encrypted Key General error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "alert_severity_overrides" {
  description = "Alerts severity overrides."
  type        = map(string)
}

variable "key_sets_config" {
  description = <<EOT
  Configuration for key sets managed by Key Generation.

  Note: For backward compatibility, if a config is not defined. A default key set with name = "" is created.

  Attributes:
    key_sets                    (list) - The list of individual key set configuration, one for each unique key set.
    key_sets[].name             (string) - The unique set name for the key set, the value should be a valid URL path segment (e.g. ^[a-zA-Z0-9\\-\\._~]+$).
    key_sets[].tink_template    (optional(string)) - The Tink template to be used for key generation.
    key_sets[].count            (optional(number)) - The number of keys to be generated.
    key_sets[].validity_in_days (optional(number)) - Number of days the generated keys will be valid. If set to 0, the keys will never expire.
    key_sets[].ttl_in_days      (optional(number)) - Number of days the generated keys will be kept in the database. If set to 0, the keys will never be deleted.
  EOT
  type = object({
    key_sets = list(object({
      name             = string
      tink_template    = string
      count            = number
      validity_in_days = number
      ttl_in_days      = number
    }))
  })
}

variable "disable_key_set_acl" {
  description = "Controls whether to generate keys enforcing key set level acl."
  type        = string
}
