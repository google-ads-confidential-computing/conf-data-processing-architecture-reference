# Copyright 2025 Google LLC
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

variable "project" {
  description = "The name of the GCP project"
  type        = string
  nullable    = false
}

variable "environment" {
  description = "Name of the environment. e.g. prod or dev."
  type        = string
  nullable    = false
}

variable "backend_id" {
  description = "Short ID to namespace the backend configuration for the Cloud Runs."
  type        = string
  nullable    = false
}

variable "cloud_run_information" {
  description = "The information of the Cloud Runs that this load balancer should connect to."
  type = set(object({
    service_name = string
    region       = string
  }))
  nullable = false
}

variable "enable_backend_logging" {
  description = "Whether to enable logging for the load balancer traffic served by the backend service."
  type        = bool
  nullable    = false
}

variable "backend_service_paths" {
  description = "The request paths that should be routed to the Cloud Run backends."
  type        = set(string)
  nullable    = false
}

variable "service_domain" {
  description = "The domain name to use to identify the load balancer. e.g. my.service.com. Will be used ot create a new cert."
  type        = string
  nullable    = true
}

variable "external_ip_address" {
  description = "The IP address used to reach this load balancer"
  type        = string
  nullable    = false
}

variable "outlier_detection_interval_seconds" {
  description = "Time interval between ejection sweep analysis. This can result in both new ejections as well as hosts being returned to service."
  type        = number
  nullable    = false
}

variable "outlier_detection_base_ejection_time_seconds" {
  description = "The base time that a host is ejected for. The real time is equal to the base time multiplied by the number of times the host has been ejected."
  type        = number
  nullable    = false
}

variable "outlier_detection_consecutive_errors" {
  description = "Number of errors before a host is ejected from the connection pool. When the backend host is accessed over HTTP, a 5xx return code qualifies as an error."
  type        = number
  nullable    = false
}

variable "outlier_detection_enforcing_consecutive_errors" {
  description = "The percentage chance that a host will be actually ejected when an outlier status is detected through consecutive 5xx. This setting can be used to disable ejection or to ramp it up slowly."
  type        = number
  nullable    = false
}

variable "outlier_detection_consecutive_gateway_failure" {
  description = "The number of consecutive gateway failures (502, 503, 504 status or connection errors that are mapped to one of those status codes) before a consecutive gateway failure ejection occurs."
  type        = number
  nullable    = false
}

variable "outlier_detection_enforcing_consecutive_gateway_failure" {
  description = "The percentage chance that a host will be actually ejected when an outlier status is detected through consecutive gateway failures. This setting can be used to disable ejection or to ramp it up slowly."
  type        = number
  nullable    = false
}

variable "outlier_detection_max_ejection_percent" {
  description = "Maximum percentage of hosts in the load balancing pool for the backend service that can be ejected."
  type        = number
  nullable    = false
}
