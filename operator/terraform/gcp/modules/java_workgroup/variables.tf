/**
 * Copyright 2025 Google LLC
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

variable "workgroup" {
  description = "Workgroup where this service is deployed."
  type        = string
}

variable "region" {
  description = "Region where resources will be created."
  type        = string
}

################################################################################
# Job Variables.
################################################################################

variable "max_job_processing_time" {
  description = "Maximum job processing time (Seconds)."
  type        = string
}

variable "max_job_num_attempts" {
  description = "Max number of times a job can be picked up by a worker and attempted processing"
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

################################################################################
# Metrics Variables.
################################################################################

variable "enable_remote_metric_aggregation" {
  description = "When true, install the collector module to operator_service"
  type        = bool
}

variable "enable_legacy_metrics" {
  description = "When true, enable legacy metrics created prior to opentelemetry"
  type        = bool
}
