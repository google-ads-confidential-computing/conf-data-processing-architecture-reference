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

#################################################################################
# Global Variables.
################################################################################

variable "environment" {
  description = "Environment where this service is deployed (e.g. dev, prod)."
  type        = string
}

variable "project_id" {
  description = "project id"
  type        = string
}

################################################################################
# Network Variables.
################################################################################

variable "network" {
  description = "VPC Network name or self-link to use for worker."
  type        = string
}

variable "subnet_id" {
  description = "Service subnet id."
  type        = string
}

variable "region" {
  description = "Region where resources will be created."
  type        = string
}

################################################################################
# Collector Variables.
################################################################################
variable "user_provided_collector_sa_email" {
  description = "User provided service account email for OpenTelemetry Collector."
  type        = string
}

variable "collector_instance_type" {
  description = "GCE instance type for worker."
  type        = string
}

variable "collector_startup_script" {
  description = "Script to configure and start the otel collector."
  type        = string
}

variable "collector_service_port_name" {
  description = "The name of the http port that receives traffic destined for the OpenTelemetry collector."
  type        = string
}

variable "collector_service_port" {
  description = "The value of the http port that receives traffic destined for the OpenTelemetry collector."
  type        = number
}

variable "max_collector_instances" {
  description = "The maximum number of running instances for the managed instance group of collector."
  type        = number
}

variable "min_collector_instances" {
  description = "The minimum number of running instances for the managed instance group of collector."
  type        = number
}

variable "collector_min_instance_ready_sec" {
  description = "Waiting time for the new instance to be ready."
  type        = number
}

variable "collector_cpu_utilization_target" {
  description = "Cpu utilization target for the collector."
  type        = number
}
################################################################################
# Alarm Variables.
################################################################################

variable "collector_exceed_cpu_usage_alarm" {
  description = "Configuration for the exceed CPU usage alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
}

variable "collector_exceed_memory_usage_alarm" {
  description = "Configuration for the exceed memory usage alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
}

variable "collector_export_error_alarm" {
  description = "Configuration for the collector exporting error alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
}

variable "collector_run_error_alarm" {
  description = "Configuration for the collector run error alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
}

variable "collector_crash_error_alarm" {
  description = "Configuration for the collector crash error alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
}

variable "worker_exporting_metrics_error_alarm" {
  description = "Configuration for the worker exporting metrics error alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
}
