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
  type        = set(string)
}

variable "enable_domain_management" {
  description = "Manage domain SSL cert creation and routing for this service."
  type        = bool
}

variable "public_key_domain" {
  description = "Domain to use to create a managed SSL cert for this service."
  type        = string
}

variable "package_bucket_name" {
  description = "Name of bucket containing cloudfunction jar."
  type        = string
}

variable "get_public_key_service_jar" {
  description = "Path to the jar file for cloudfunction."
  type        = string
}

variable "get_public_key_service_source_path" {
  description = "GCS path to public Key Service source archive in the package bucket."
  type        = string
}

variable "get_public_key_cloudfunction_memory_mb" {
  description = "Memory size in MB for cloudfunction."
  type        = number
}

variable "get_public_key_cloudfunction_min_instances" {
  description = "The minimum number of function instances that may coexist at a given time."
  type        = number
}

variable "get_public_key_cloudfunction_max_instances" {
  description = "The maximum number of function instances that may coexist at a given time."
  type        = number
}

variable "cloudfunction_timeout_seconds" {
  description = "Number of seconds after which a function instance times out."
  type        = number
}

variable "enable_get_public_key_cdn" {
  description = "Enable Get Public Key API CDN"
  type        = bool
}

variable "get_public_key_cloud_cdn_default_ttl_seconds" {
  description = "Default CDN TTL seconds to use when no cache headers are present."
  type        = number
}

variable "get_public_key_cloud_cdn_max_ttl_seconds" {
  description = "Maximum CDN TTL seconds that cache header directive cannot surpass."
  type        = number
}

variable "get_public_key_cloud_cdn_serve_while_stale_seconds" {
  description = "Maximum CDN TTL seconds that cache header directive cannot surpass."
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

variable "get_public_key_cloudfunction_error_ratio_threshold" {
  description = "Error ratio greater than this to send alarm. Must be in decimal form: 10% = 0.10. Example: '0.0'."
  type        = number
}

variable "get_public_key_cloudfunction_max_execution_time_max" {
  description = "Max execution time in ms to send alarm. Example: 9999."
  type        = number
}

variable "get_public_key_cloudfunction_5xx_ratio_threshold" {
  description = "Cloud Function 5xx error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "cloudfunction_alert_on_memory_usage_threshold" {
  description = "Memory usage of the Cloud Function should be higher than this value to alert."
  type        = number
}

variable "get_public_key_lb_max_latency_ms" {
  description = "Load Balancer max latency to send alarm. Measured in milliseconds. Example: 5000."
  type        = string
}

variable "get_public_key_lb_5xx_ratio_threshold" {
  description = "Load Balancer 5xx error count greater than this to send alarm. Example: 0."
  type        = string
}

variable "get_public_key_empty_key_set_error_threshold" {
  description = "Get Public Key Empty Key Set error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "get_public_key_general_error_threshold" {
  description = "Get Public Key General error count greater than this to send alarm. Example: 0."
  type        = number
}

variable "public_key_alerts_severity_overrides" {
  description = "map that defines severity for alerts"
  type        = map(string)
}
