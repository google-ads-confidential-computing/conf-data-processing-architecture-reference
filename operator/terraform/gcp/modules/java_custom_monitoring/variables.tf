/**
 * Copyright 2022 Google LLC
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

variable "environment" {
  description = "Environment where this service is deployed (e.g. dev, prod)."
  type        = string
}

variable "workgroup" {
  description = "Workgroup name to associate resources."
  type        = string
}

variable "vm_instance_group_name" {
  description = "Name for the instance group for the worker VM."
  type        = string
}

variable "vm_instance_group_base_instance_name" {
  description = "Base instance name for the worker instance group."
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

variable "notification_channel_id" {
  description = "Notification channel to which to send alarms."
  type        = string
}

variable "java_job_validations_to_alert" {
  description = <<-EOT
      Job validations to alarm for Java CPIO Job Client. Supported validations:
      ["JobValidatorCheckFields", "JobValidatorCheckRetryLimit", "JobValidatorCheckStatus"]
  EOT
  type        = list(string)
}

variable "enable_new_metrics" {
  description = "When true, enable new metrics created after enable remote metric aggregation"
  type        = bool
}

variable "enable_legacy_metrics" {
  description = "When true, disable legacy metrics created before enable remote metric aggregation"
  type        = bool
}

variable "legacy_jobclient_job_validation_failure_metric_type" {
  description = "The metric for legacy JobClient job validation failures."
  type        = string
}

variable "legacy_jobclient_error_metric_type" {
  description = "The metric for legacy JobClient errors."
  type        = string
}

variable "legacy_worker_error_metric_type" {
  description = "The metric for legacy worker errors."
  type        = string
}

variable "new_jobclient_job_validation_failure_metric_type" {
  description = "The metric for legacy JobClient job validation failures."
  type        = string
}

variable "new_jobclient_error_metric_type" {
  description = "The metric for legacy JobClient errors."
  type        = string
}

variable "new_worker_error_metric_type" {
  description = "The metric for legacy worker errors."
  type        = string
}
