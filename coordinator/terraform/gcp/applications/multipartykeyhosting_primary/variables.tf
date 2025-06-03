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

################################################################################
# Global Variables.
################################################################################

variable "project_id" {
  description = "GCP Project ID in which this module will be created."
  type        = string
}

variable "environment" {
  description = "Description for the environment, e.g. dev, staging, production."
  type        = string
}

variable "allowed_operator_user_group" {
  description = "Google group of allowed operators to which to give API access."
  type        = string
  default     = null
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

variable "key_generation_allow_stopping_for_update" {
  description = "If true, allows Terraform to stop the key generation instances to update their properties. If you try to update a property that requires stopping the instances without setting this field, the update will fail."
  type        = bool
  default     = false
}

variable "mpkhs_primary_package_bucket_location" {
  description = "Location for multiparty keyhosting packages. Example: 'US'."
  type        = string
}

variable "mpkhs_primary_package_bucket" {
  description = "Location for multiparty keyhosting packages. Example: 'US'." #TODO
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
  description = "Config value for the Spanner Instance. Example: 'nam10'."
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
# Key Generation Variables.
################################################################################

variable "key_generation_image" {
  description = "The Key Generation Application docker image."
  type        = string
}

variable "key_generation_count" {
  description = "Number of keys to generate at a time."
  type        = number

  validation {
    condition     = var.key_generation_count > 0
    error_message = "Must be greater than 0."
  }
}

variable "key_generation_validity_in_days" {
  description = "Number of days keys will be valid. Should be greater than generation days for failover validity."
  type        = number

  validation {
    condition     = var.key_generation_validity_in_days > 0
    error_message = "Must be greater than 0."
  }
}

variable "key_generation_ttl_in_days" {
  description = "Keys will be deleted from the database this number of days after creation time."
  type        = number
  validation {
    condition     = var.key_generation_ttl_in_days > 0
    error_message = "Must be greater than 0."
  }
}

variable "key_generation_cron_schedule" {
  description = <<-EOT
    Frequency for key generation cron job. Must be valid cron statement. Default is every Monday at 10AM
    See documentation for more details: https://cloud.google.com/scheduler/docs/configuring/cron-job-schedules
  EOT
  type        = string
}

variable "key_generation_cron_time_zone" {
  description = "Time zone to be used with cron schedule."
  type        = string
}

variable "instance_disk_image" {
  description = "The image from which to initialize the key generation instance disk for TEE."
  type        = string
}

variable "key_generation_logging_enabled" {
  description = "Whether to enable logging for Key Generation instance."
  type        = bool
}

variable "key_generation_monitoring_enabled" {
  description = "Whether to enable monitoring for Key Generation instance."
  type        = bool
}

variable "key_generation_tee_restart_policy" {
  description = "The TEE restart policy. Currently only supports Never."
  type        = string
}

variable "key_generation_tee_allowed_sa" {
  description = "The service account provided by Coordinator B for key generation instance to impersonate."
  type        = string
}

variable "key_generation_undelivered_messages_threshold" {
  description = "Total Queue Messages greater than this to send alert."
  type        = number
}

variable "key_generation_error_threshold" {
  description = "Total key generation errors greater than this to send alert."
  type        = number
}

variable "key_generation_alignment_period" {
  description = "Alignment period of key generation alert metrics in seconds. This value should match the period of the cron schedule."
  type        = number

  validation {
    # This value should match the period of the cron schedule.
    # Used for the alignment period of alert metrics.
    # Max 81,000 seconds due to the max GCP metric-threshold evaluation period
    # (23 hours, 30 minutes) plus an extra hour to allow fluctuations in
    # execution time.
    condition     = var.key_generation_alignment_period > 0 && var.key_generation_alignment_period < 81000
    error_message = "Must be between 0 and 81,000 seconds."
  }
}

variable "key_gen_instance_force_replace" {
  description = "Whether to force key generation instance replacement for every deployment."
  type        = bool
}

variable "peer_coordinator_kms_key_uri" {
  description = "KMS key URI for peer coordinator."
  type        = string
}

variable "key_storage_service_base_url" {
  description = "Base url for key storage service for peer coordinator."
  type        = string
}

// TODO(b/275758643)
variable "key_storage_service_cloudfunction_url" {
  description = "Cloud function url for peer coordinator."
  type        = string
}

variable "peer_coordinator_wip_provider" {
  description = "Peer coordinator wip provider address."
  type        = string
}

variable "peer_coordinator_service_account" {
  description = "Service account generated from peer coordinator."
  type        = string
}

variable "key_id_type" {
  description = "Key ID Type"
  type        = string
}

variable "allowed_operators" {
  description = <<EOT
  Authorized decrypters that have attested access to perform decryption.

  Attributes:
    key                        (string) - The name of the decrypters set.
    value.service_accounts     (string) - The list of service account emails of the decrypters.
    value.image_references     (string) - The list of allowed image references.
    value.image_digests        (string) - The list of allowed image digests.
    value.image_signature_key_ids (string) - The list of required signature algorithm key ids.
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

variable "location_new_key_ring" {
  description = "Location for the global key ring."
  type        = string
  nullable    = true
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

variable "public_key_service_subdomain" {
  description = "Subdomain to use with parent_domain_name to designate the Public Key Service."
  type        = string
}

variable "encryption_key_service_subdomain" {
  description = "Subdomain to use with parent_domain_name to designate the encryption key service."
  type        = string
}

################################################################################
# Cloud Function Variables.
################################################################################

variable "cloudfunction_timeout_seconds" {
  description = "Number of seconds after which a function instance times out."
  type        = number
}

### Public Key Service

variable "public_key_service_launch_cloud_run" {
  description = "Flag to control launching Cloud Run Public Key Service."
  type        = bool
  nullable    = false
}

variable "public_key_service_container_image_url" {
  description = "The full path (registry + tag) to the container image used to deploy Public Key Service."
  type        = string
  nullable    = false
}

variable "get_public_key_service_jar" {
  description = <<-EOT
          Get Public key service cloud function path. If not provided defaults to locally built jar file.
        Build with `bazel build //coordinator/terraform/gcp/applications/multipartykeyhosting_primary:all`.
      EOT
  type        = string
}

variable "get_public_key_service_source_path" {
  description = "GCS path to public Key Service source archive in the package bucket."
  type        = string
}

variable "public_key_service_cloud_run_cpu_count" {
  description = "Number of cpus to allocate for Public Key Service cloud run instance."
  type        = number
}

variable "public_key_service_max_cloud_run_concurrency" {
  description = "Maximum request concurrency for Public Key Service cloud run instance."
  type        = number
}

variable "public_key_service_memory_mb" {
  description = "Memory size in MB for public key service."
  type        = number
}

variable "public_key_service_min_instances" {
  description = "The minimum number of instances that may coexist at a given time."
  type        = number
}

variable "public_key_service_max_instances" {
  description = "The maximum number of function instances that may coexist at a given time."
  type        = number
}

variable "publickeyservice_use_java21_runtime" {
  description = "Whether to use the Java 21 runtime for the cloud function. If false will use Java 11."
  type        = bool
  nullable    = false
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
  description = "The full path (registry + tag) to the container image used to deploy Private Key Service."
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

variable "encryption_key_service_jar" {
  description = <<-EOT
          Encryption key service cloud function path. If not provided defaults to locally built jar file.
        Build with `bazel build //coordinator/terraform/gcp/applications/multipartykeyhosting_primary:all`.
      EOT
  type        = string
}

variable "encryption_key_service_source_path" {
  description = "GCS path to Encryption Key Service source archive in the package bucket."
  type        = string
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

variable "encryptionkeyservice_use_java21_runtime" {
  description = "Whether to use the Java 21 runtime for the cloud function. If false will use Java 11."
  type        = bool
  nullable    = false
}

################################################################################
# Key Management Variables.
################################################################################

variable "enable_public_key_service_cdn" {
  description = "Enable Get Public Key API CDN."
  type        = bool
}

variable "public_key_service_cdn_default_ttl_seconds" {
  description = "Default CDN TTL seconds to use when no cache headers are present."
  type        = number
}

variable "public_key_service_cdn_max_ttl_seconds" {
  description = "Maximum CDN TTL seconds that cache header directive cannot surpass."
  type        = number
}

variable "public_key_service_cdn_serve_while_stale_seconds" {
  description = "Maximum CDN TTL seconds that cache header directive cannot surpass."
  type        = number
}

################################################################################
# Public Key Alarm Variables.
################################################################################

variable "get_public_key_alarm_eval_period_sec" {
  description = "Amount of time (in seconds) for alarm evaluation. Example: '60'."
  type        = string
}

variable "get_public_key_alarm_duration_sec" {
  description = "Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'."
  type        = string
}

variable "get_public_key_cloudfunction_error_ratio_threshold" {
  description = "Error ratio greater than this to send alarm. Must be in decimal form: 10% = 0.10. Example: '0.0'."
  type        = number
}

variable "get_public_key_cloudfunction_max_execution_time_max" {
  description = "Max execution time in ms to send alarm. Example: 9999."
  type        = number
}

variable "get_public_key_cloudfunction_5xx_threshold" {
  description = "Cloud Function 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "publickeyservice_cloudfunction_alert_on_memory_usage_threshold" {
  description = "Memory usage of the Cloud Function should be higher than this value to alert."
  type        = number
}

variable "get_public_key_lb_max_latency_ms" {
  description = "Load Balancer max latency to send alarm. Measured in milliseconds. Example: 5000."
  type        = string
}

variable "get_public_key_lb_5xx_threshold" {
  description = "Load Balancer 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "get_public_key_lb_5xx_ratio_threshold" {
  description = "Load Balancer ratio of 5xx/all requests greater than this to send alarm. Example: 0."
  type        = number
}

variable "get_public_key_empty_key_set_error_threshold" {
  description = "Get Public Key Empty Key Set error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "get_public_key_general_error_threshold" {
  description = "Get Public Key General error count greater than this to send alarm. Example: 0."
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

variable "get_encrypted_private_key_general_error_threshold" {
  description = "Get Encrypted Key General error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "alert_severity_overrides" {
  description = "Alerts severity overrides."
  type        = map(string)
}

variable "disable_key_set_acl" {
  description = "Controls whether to generate keys enforcing key set level acl."
  type        = string
}

variable "peer_coordinator_kms_key_base_uri" {
  description = "Kms key base url from peer coordinator."
  type        = string
}
