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

variable "project_id" {
  description = "GCP Project ID in which this module will be created."
  type        = string
}

variable "environment" {
  description = "Environment where this service is deployed (e.g. dev, prod)."
  type        = string
}

variable "regions" {
  description = "Regions for the created resources."
  type        = list(string)
}

variable "package_bucket_name" {
  description = "Name of bucket containing cloudfunction jar."
  type        = string
}

variable "enable_domain_management" {
  description = "Manage domain SSL cert creation and routing for this service."
  type        = bool
}

variable "encryption_key_domain" {
  description = "Domain to use to create a managed SSL cert for this service."
  type        = string
}

variable "encryption_key_service_jar" {
  description = "Path to the jar file for cloudfunction."
  type        = string
}

variable "encryption_key_service_source_path" {
  description = "GCS path to Encryption Key Service source archive in the package bucket."
  type        = string
}

variable "encryption_key_service_cloudfunction_memory_mb" {
  description = "Memory size in MB for cloudfunction."
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

variable "cloudfunction_timeout_seconds" {
  description = "Number of seconds after which a function instance times out."
  type        = number
}

variable "spanner_database_name" {
  description = "Name of the KeyDb Spanner database."
  type        = string
}

variable "spanner_instance_name" {
  description = "Name of the KeyDb Spanner instance."
  type        = string
}

variable "allowed_operator_user_group" {
  description = "Google group of allowed operators to which to give API access."
  type        = string
}

variable "allowed_operator_service_accounts" {
  description = "List of operator service accounts allowed to invoke the API."
  type        = list(string)
  default     = []
}

variable "use_java21_runtime" {
  description = "Whether to use the Java 21 runtime for the cloud function. If false will use Java 11."
  type        = bool
  nullable    = false
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

variable "cloudfunction_error_ratio_threshold" {
  description = "Error ratio greater than this to send alarm. Must be in decimal form: 10% = 0.10. Example: '0.0'."
  type        = number
}

variable "cloudfunction_max_execution_time_max" {
  description = "Max execution time in ms to send alarm. Example: 9999."
  type        = number
}

variable "cloudfunction_5xx_threshold" {
  description = "Cloud Function 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "cloudfunction_alert_on_memory_usage_threshold" {
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

variable "get_encrypted_private_key_general_error_threshold" {
  description = "Get Encrypted Key General error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "encryption_key_service_severity_map" {
  description = "map that defines severity for alerts"
  type        = map(string)
}
