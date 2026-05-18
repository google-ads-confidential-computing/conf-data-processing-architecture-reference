/**
 * Copyright 2026 Google LLC
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
  description = "Description for the environment, e.g. dev, staging, production"
  type        = string
}

variable "region" {
  description = "Region where all services will be created."
  type        = string
}

variable "region_zone" {
  description = "Region zone where all services will be created."
  type        = string
}

################################################################################
# Network Variables.
################################################################################

variable "network" {
  description = "VPC network name or self-link."
  type        = string
}

variable "collector_subnet_id" {
  description = "Collector subnet id or self-link."
  type        = string
}

variable "proxy_subnet_id" {
  description = "Proxy subnet id or self-link."
  type        = string
}

################################################################################
# OpenTelemetry Collector variables
################################################################################

variable "collector_instance_type" {
  description = "GCE instance type for worker."
  type        = string
  default     = "n2d-standard-2"
}

variable "user_provided_collector_sa_email" {
  description = "User provided service account email for OpenTelemetry Collector."
  type        = string
  default     = ""
}

variable "collector_service_port_name" {
  description = "The name of the http port that receives traffic destined for the OpenTelemetry collector."
  type        = string
  default     = "otlp"
}

variable "collector_service_port" {
  description = "The value of the http port that receives traffic destined for the OpenTelemetry collector."
  type        = number
  default     = 4318
}

variable "collector_domain_name" {
  description = "The dns domain name for OpenTelemetry collector."
  type        = string
  default     = "collector.metrics"
}

variable "collector_dns_name" {
  description = "The dns name for OpenTelemetry collector."
  type        = string
  default     = "scptestings.dev"
}

variable "collector_send_batch_max_size" {
  description = "The upper limit of a single batch. This property ensures that larger batches are split into smaller units. It must be greater than or equal to send_batch_size."
  type        = number
  default     = 200
}

variable "collector_send_batch_size" {
  description = "Number of metric data points after which batching will be started regardless of the timeout. All data points in this buffer will be split to smaller batches based on collector_send_batch_max_size."
  type        = number
  default     = 200
}

variable "collector_send_batch_timeout" {
  description = "Time duration after which a batch will be sent regardless of size."
  type        = string
  default     = "5s"
}

variable "max_collector_instances" {
  description = "The maximum number of running instances for the managed instance group of collector."
  type        = number
  default     = 2
}

variable "min_collector_instances" {
  description = "The minimum number of running instances for the managed instance group of collector."
  type        = number
  default     = 1
}

variable "collector_min_instance_ready_sec" {
  description = "Waiting time for the new instance to be ready."
  type        = number
  default     = 120
}

variable "collector_queue_size" {
  description = "The queue size of the sending queue."
  type        = number
  default     = 5000
}

variable "collector_cpu_utilization_target" {
  description = "Cpu utilization target for the collector."
  type        = number
  default     = 0.8
}

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
  default = {
    enable_alarm : false,
    duration_sec : 300,
    alignment_period_sec : 600,
    threshold : 0.9,
    severity : "moderate",
    auto_close_sec : 1800
  }
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
  default = {
    enable_alarm : false,
    duration_sec : 300,
    alignment_period_sec : 600,
    threshold : 6442450944, # 6 GB
    severity : "moderate",
    auto_close_sec : 1800
  }
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
  default = {
    enable_alarm : false,
    duration_sec : 300,
    alignment_period_sec : 600,
    threshold : 50,
    severity : "moderate",
    auto_close_sec : 1800
  }
}

variable "collector_startup_error_alarm" {
  description = "Configuration for the collector startup error alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
  default = {
    enable_alarm : false,
    duration_sec : 300,
    alignment_period_sec : 600,
    threshold : 50,
    severity : "moderate",
    auto_close_sec : 1800
  }
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
  default = {
    enable_alarm : false,
    duration_sec : 300,
    alignment_period_sec : 600,
    threshold : 50,
    severity : "moderate",
    auto_close_sec : 1800
  }
}

variable "export_metric_to_collector_error_alarm" {
  description = "Configuration for the server exporting metrics error alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
  default = {
    enable_alarm : false,
    duration_sec : 300,
    alignment_period_sec : 600,
    threshold : 50,
    severity : "moderate",
    auto_close_sec : 1800
  }
}

variable "collector_queue_size_alarm" {
  description = "Configuration for the collector queue size alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
  default = {
    enable_alarm : false,
    duration_sec : 300,
    alignment_period_sec : 600,
    threshold : 0.8,
    severity : "moderate",
    auto_close_sec : 1800
  }
}

variable "collector_send_metric_failure_rate_alarm" {
  description = "Configuration for the collector send metric failure alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
  default = {
    enable_alarm : false,
    duration_sec : 300,
    alignment_period_sec : 600,
    threshold : 0.05,
    severity : "moderate",
    auto_close_sec : 1800
  }
}

variable "collector_refuse_metric_rate_alarm" {
  description = "Configuration for the collector refuse metric alarm."
  type = object({
    enable_alarm : bool,
    duration_sec : number,
    alignment_period_sec : number,
    threshold : number,
    severity : string,
    auto_close_sec : number
  })
  default = {
    enable_alarm : false,
    duration_sec : 300,
    alignment_period_sec : 600,
    threshold : 0.05,
    severity : "moderate",
    auto_close_sec : 1800
  }
}

variable "collector_service_name" {
  description = "The name of the service for the load balancer."
  type        = string
  default     = "collector"
}