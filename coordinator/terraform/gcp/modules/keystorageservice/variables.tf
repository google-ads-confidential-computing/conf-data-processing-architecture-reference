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
# Global Variables
################################################################################

variable "project_id" {
  description = "GCP Project ID in which this module will be created."
  type        = string
}

variable "environment" {
  type        = string
  description = "Environment where this service is deployed (e.g. dev, prod)."
}

variable "region" {
  type        = string
  description = "Region for load balancer and cloud function."
}

variable "key_storage_domain" {
  description = "Domain to use to create a managed SSL cert for this service."
  type        = string
}

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

variable "load_balancing_scheme" {
  description = "Whether the backend will be used with internal or external load balancing."
  type        = string
}

variable "external_managed_migration_state" {
  description = "Defines what stage of the LB migration in."
  type        = string
}

variable "external_managed_migration_testing_percentage" {
  description = "Defines what percentage of traffic should be routed to upgraded LB."
  type        = number
}

variable "forwarding_rule_load_balancing_scheme" {
  description = "Specifies the load balancing scheme used for the forwarding rule."
  type        = string
}

variable "external_managed_backend_bucket_migration_state" {
  description = "Specifies the canary migration state for the backend buckets attached to this forwarding rule."
  type        = string
}

variable "external_managed_backend_bucket_migration_testing_percentage" {
  description = "Determines the fraction of requests to backend buckets that should be processed by the Global external Application Load Balancer."
  type        = number
}

################################################################################
# Function Variables
################################################################################

variable "source_container_image_url" {
  description = "The URL for the container image to run on this service."
  type        = string
}

variable "key_storage_memory" {
  type        = number
  description = "Memory size in MB for cloud function."
}

variable "min_instances" {
  description = "The minimum number of function instances that may coexist at a given time."
  type        = number
}

variable "max_instances" {
  description = "The maximum number of function instances that may coexist at a given time."
  type        = number
}

variable "execution_environment" {
  description = "The sandbox environment to host the cloud run service."
  type        = string
  nullable    = false
}

variable "spanner_instance_name" {
  type        = string
  description = "Name of the KeyDb Spanner instance."
}

variable "spanner_database_name" {
  type        = string
  description = "Name of the KeyDb Spanner database."
}

################################################################################
# Alarm Variables.
################################################################################

variable "alarms_enabled" {
  description = "Enable alarms for this service."
  type        = bool
}

variable "alarm_eval_period_sec" {
  description = "Amount of time (in seconds) for alarm evaluation. Example: '60'."
  type        = string
}

variable "alarm_duration_sec" {
  description = "Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'."
  type        = string
}

variable "cloud_run_max_execution_time_max" {
  description = "Max execution time in ms to send alarm. Example: 9999."
  type        = number
}

variable "cloud_run_5xx_threshold" {
  description = "Cloud Function 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "cloud_run_alert_on_memory_usage_threshold" {
  description = "Memory usage of the Cloud Function should be higher than this value to alert."
  type        = number
}

variable "lb_max_latency_ms" {
  description = "Load Balancer max latency to send alarm. Measured in milliseconds. Example: 5000."
  type        = string
}

variable "lb_5xx_threshold" {
  description = "Load Balancer 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "lb_5xx_ratio_threshold" {
  description = "Load Balancer ratio of 5xx/all requests greater than this to send alarm. Example: 0."
  type        = number
}

variable "key_storage_severity_map" {
  description = "map that defines severity for alerts"
  type        = map(string)
}

variable "populate_migration_key_data" {
  description = <<EOT
  Controls whether to populate the migration columns when generating keys.

  Note: This should only should only be used in preparation for or during a migration.
  EOT
  type        = string
}

variable "kms_key_base_uri" {
  description = "Kms encryption key base uri for composing kms encryption keys."
  type        = string
}

variable "migration_kms_key_base_uri" {
  description = "Migration kms encryption key base uri for composing kms encryption keys."
  type        = string
}
