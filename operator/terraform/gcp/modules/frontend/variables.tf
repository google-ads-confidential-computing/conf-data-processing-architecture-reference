/**
 * Copyright 2022-2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

################################################################################
# Global Variables.
################################################################################

variable "project_id" {
  description = "GCP Project ID in which this module will be created."
  type        = string
}

variable "environment" {
  description = "Environment where this service is deployed (e.g. dev, prod)."
  type        = string
}

variable "region" {
  description = "Region where resources will be created."
  type        = string
}

variable "vpc_connector_ids" {
  description = "Serverless VPC Access connector ID to use for all egress traffic. Map with Key: region and Value: the connector ID."
  type        = map(string)
  default     = {}
}

variable "job_version" {
  description = "The version of frontend service. Version 2 supports new job schema from C++ CMRT library."
  type        = string
}

################################################################################
# Cloud Run Variables.
################################################################################

variable "frontend_service_cloud_run_regions" {
  description = "The regions to deploy the Cloud Run FE handlers in."
  type        = set(string)
  nullable    = false
}

variable "frontend_service_cloud_run_deletion_protection" {
  description = "Whether to prevent the instance from being deleted by terraform during apply."
  type        = bool
  nullable    = false
}

variable "frontend_service_cloud_run_source_container_image_url" {
  description = "The URL for the container image to run on this service."
  type        = string
  nullable    = false
}

variable "frontend_service_cloud_run_cpu_idle" {
  description = "Determines whether the CPU is always allocated (false) or if only allocated for usage (true)."
  type        = bool
  nullable    = false
}

variable "frontend_service_cloud_run_startup_cpu_boost" {
  description = "Whether to over-allocate CPU for faster new instance startup."
  type        = bool
  nullable    = false
}

variable "frontend_service_cloud_run_ingress_traffic_setting" {
  description = "Which type of traffic to allow. Options are INGRESS_TRAFFIC_ALL INGRESS_TRAFFIC_INTERNAL_ONLY, INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"
  type        = string
  nullable    = false
}

variable "frontend_service_cloud_run_allowed_invoker_iam_members" {
  description = "The identities allowed to invoke this cloud run service. Require the GCP IAM prefixes such as serviceAccount: or user:"
  type        = set(string)
  nullable    = false
}

variable "frontend_service_cloud_run_binary_authorization" {
  description = "Binary Authorization config."
  type = object({
    breakglass_justification = optional(bool)
    use_default              = optional(bool)
    policy                   = optional(string)
  })
  nullable = true
}

variable "frontend_service_cloud_run_custom_audiences" {
  description = "The full URL to be used as a custom audience for invoking this Cloud Run."
  type        = set(string)
  nullable    = false
}

variable "frontend_service_enable_lb_backend_logging" {
  description = "Whether to enable logging for the load balancer traffic served by the backend service."
  type        = bool
  nullable    = false
}

variable "frontend_service_lb_allowed_request_paths" {
  description = "The requests paths that will be forwarded to the backend."
  type        = set(string)
  nullable    = false
}

variable "frontend_service_lb_domain" {
  description = "The domain name to use to identify the load balancer. e.g. my.service.com. Will be used ot create a new cert."
  type        = string
  nullable    = true
}

variable "frontend_service_parent_domain_name" {
  description = "The parent domain name used for the DNS record for the FE e.g. 'my.domain.com'."
  type        = string
  nullable    = false
}

variable "frontend_service_parent_domain_name_project_id" {
  description = "The ID of the project where the DNS hosted zone for the parent domain exists."
  type        = string
  nullable    = false
}

variable "frontend_service_lb_outlier_detection_interval_seconds" {
  description = "Time interval between ejection sweep analysis. This can result in both new ejections as well as hosts being returned to service."
  type        = number
  nullable    = false
}

variable "frontend_service_lb_outlier_detection_base_ejection_time_seconds" {
  description = "The base time that a host is ejected for. The real time is equal to the base time multiplied by the number of times the host has been ejected."
  type        = number
  nullable    = false
}

variable "frontend_service_lb_outlier_detection_consecutive_errors" {
  description = "Number of errors before a host is ejected from the connection pool. When the backend host is accessed over HTTP, a 5xx return code qualifies as an error."
  type        = number
  nullable    = false
}

variable "frontend_service_lb_outlier_detection_enforcing_consecutive_errors" {
  description = "The percentage chance that a host will be actually ejected when an outlier status is detected through consecutive 5xx. This setting can be used to disable ejection or to ramp it up slowly."
  type        = number
  nullable    = false
}

variable "frontend_service_lb_outlier_detection_consecutive_gateway_failure" {
  description = "The number of consecutive gateway failures (502, 503, 504 status or connection errors that are mapped to one of those status codes) before a consecutive gateway failure ejection occurs."
  type        = number
  nullable    = false
}

variable "frontend_service_lb_outlier_detection_enforcing_consecutive_gateway_failure" {
  description = "The percentage chance that a host will be actually ejected when an outlier status is detected through consecutive gateway failures. This setting can be used to disable ejection or to ramp it up slowly."
  type        = number
  nullable    = false
}

variable "frontend_service_lb_outlier_detection_max_ejection_percent" {
  description = "Maximum percentage of hosts in the load balancing pool for the backend service that can be ejected."
  type        = number
  nullable    = false
}


################################################################################
# Cloud Function Variables.
################################################################################

variable "operator_package_bucket_name" {
  description = "Name of bucket containing cloud function jar."
  type        = string
}

variable "frontend_service_jar" {
  description = "Path to the jar file for cloud function."
  type        = string
}

variable "frontend_service_zip" {
  description = <<-EOT
    Optional. Path to the zip file for lambda function under bucket
    operator_package_bucket_name.
    If this path is missing, the terraform will use path in frontend_service_jar,
    archive the jar in zip file and upload to the bucket.
  EOT
  type        = string
}

variable "frontend_service_cloudfunction_num_cpus" {
  description = "The number of CPU to use for frontend service cloud function. Reused for Cloud Run."
  type        = number
}

variable "frontend_service_cloudfunction_memory_mb" {
  description = "Memory size in MB for frontend service cloud function. Reused for Cloud Run."
  type        = number
}

variable "frontend_service_cloudfunction_min_instances" {
  description = "The minimum number of frontend function instances that may coexist at a given time. Reused for Cloud Run."
  type        = number
}

variable "frontend_service_cloudfunction_max_instances" {
  description = "The maximum number of frontend function instances that may coexist at a given time. Reused for Cloud Run."
  type        = number
}

variable "frontend_service_cloudfunction_max_instance_request_concurrency" {
  description = "The maximum number of concurrent requests that frontend function instances can receive. Reused for Cloud Run."
  type        = number
}

variable "frontend_service_cloudfunction_timeout_sec" {
  description = "Number of seconds after which a frontend function instance times out. Reused for Cloud Run."
  type        = number
}

variable "frontend_service_cloudfunction_runtime_sa_email" {
  description = "Email of the service account to use as the runtime identity of the FE service. Reused for Cloud Run."
  type        = string
  nullable    = true
}

variable "use_java21_runtime" {
  description = "Whether to use the Java 21 runtime for the cloud function. If false will use Java 11."
  type        = bool
  nullable    = false
}

################################################################################
# Spanner Variables.
################################################################################

variable "spanner_database_name" {
  description = "Name of the JobMetadata Spanner database."
  type        = string
}

variable "spanner_instance_name" {
  description = "Name of the JobMetadata Spanner instance."
  type        = string
}

variable "job_metadata_table_ttl_days" {
  description = "The number of days to retain JobMetadata table records."
  type        = number
  validation {
    condition     = var.job_metadata_table_ttl_days > 0
    error_message = "Must be greater than 0."
  }
}

variable "job_table_name" {
  description = "The name of the job table."
  type        = string
}

################################################################################
# Queue Variables.
################################################################################

variable "job_queue_topic" {
  description = "Name of the job queue topic."
  type        = string
}

variable "job_queue_sub" {
  description = "Name of the job queue subscription."
  type        = string
}

################################################################################
# Alarm Variables.
################################################################################

variable "alarms_enabled" {
  description = "Enable alarms for this service."
  type        = bool
}

variable "notification_channel_id" {
  description = "Notification channel to which to send alarms."
  type        = string
}

variable "alarm_eval_period_sec" {
  description = "Amount of time (in seconds) for alarm evaluation. Example: '60'."
  type        = string
}

variable "alarm_duration_sec" {
  description = "Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'."
  type        = string
}

variable "cloudfunction_error_threshold" {
  description = "Error count greater than this to send alarm. Example: 0."
  type        = string
}

variable "cloudfunction_max_execution_time_max" {
  description = "Max execution time in ms to send alarm. Example: 9999."
  type        = string
}

variable "cloudfunction_5xx_threshold" {
  description = "Cloud Function 5xx error count greater than this to send alarm. Example: 0."
  type        = string
}

variable "lb_max_latency_ms" {
  description = "Load Balancer max latency to send alarm. Measured in milliseconds. Example: 5000."
  type        = string
}

variable "lb_5xx_threshold" {
  description = "Load Balancer 5xx error count greater than this to send alarm. Example: 0."
  type        = string
}

variable "cloud_run_error_5xx_alarm_config" {
  description = "The configuration for the 5xx error alarm."

  type = object({
    enable_alarm    = bool   # Whether to enable this alarm
    eval_period_sec = number # Amount of time (in seconds) for alarm evaluation. Example: '60'
    duration_sec    = number # Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'
    error_threshold = number # error count greater than this to send alarm. Example: 0.
  })
}

variable "cloud_run_non_5xx_error_alarm_config" {
  description = "The configuration for the non-5xx error (3xx-4xx) alarm."

  type = object({
    enable_alarm    = bool   # Whether to enable this alarm
    eval_period_sec = number # Amount of time (in seconds) for alarm evaluation. Example: '60'
    duration_sec    = number # Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'
    error_threshold = number # error count greater than this to send alarm. Example: 500.
  })
}

variable "cloud_run_execution_time_alarm_config" {
  description = "The configuration for the execution time alarm."

  type = object({
    enable_alarm    = bool   # Whether to enable this alarm
    eval_period_sec = number # Amount of time (in seconds) for alarm evaluation. Example: '60'
    duration_sec    = number # Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'
    threshold_ms    = number # Execution times greater than this to send alarm. Example: 0.
  })
}
