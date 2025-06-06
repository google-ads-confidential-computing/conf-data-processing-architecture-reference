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

variable "service_name" {
  description = "The name of the cloud run service. e.g. service-handler. Must be unique."
  type        = string
  nullable    = false
}

variable "deletion_protection" {
  description = "Whether to prevent the instance from being deleted by terraform during apply."
  type        = bool
  nullable    = false
}

variable "region" {
  description = "The GCP region to deploy the service."
  type        = string
  nullable    = false
}

variable "description" {
  description = "The description to show for this cloud run service."
  type        = string
  nullable    = false
}

variable "source_container_image_url" {
  description = "The URL for the container image to run on this service."
  type        = string
  nullable    = false
}

variable "environment_variables" {
  description = "Environment variables to set for the cloud run service."
  type        = map(string)
  nullable    = false
}

variable "min_instance_count" {
  description = "Minimum number of instances in the cloud run pool."
  type        = number
  nullable    = false
}

variable "max_instance_count" {
  description = "Maximum number of instances in the cloud runs pool."
  type        = number
  nullable    = false
}

variable "concurrency" {
  description = "How many concurrent executions each instance can handle."
  type        = number
  nullable    = false
}

variable "cpu_idle" {
  description = "Determines whether the CPU is always allocated (false) or if only allocated for usage (true)."
  type        = bool
  nullable    = false
}

variable "startup_cpu_boost" {
  description = "Whether to over-allocate CPU for faster new instance startup."
  type        = bool
  nullable    = false
}

variable "cpu_count" {
  description = "How many CPUs to give to each instance. Supporter values are 1,2,4 and 8"
  type        = number
  nullable    = false

  validation {
    condition     = contains([1, 2, 4, 8], var.cpu_count)
    error_message = "The cpu_count variable must be one of: 1, 2, 4, or 8."
  }

}

variable "memory_mb" {
  description = "How much memory in MB to give to each instance. e.g. 256."
  type        = number
  nullable    = false
}

variable "timeout_seconds" {
  description = "Number of seconds after which a request to the instance times out."
  type        = number
}

variable "runtime_service_account_email" {
  description = "Service account to use as identity for this cloud run."
  type        = string
  nullable    = false
}

variable "vpc_connector_id" {
  description = "The fully qualified ID of the VPC connector."
  type        = string
  nullable    = true
}

variable "ingress_traffic_setting" {
  description = "Which type of traffic to allow. Options are INGRESS_TRAFFIC_ALL INGRESS_TRAFFIC_INTERNAL_ONLY, INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"
  type        = string
  nullable    = false
}

variable "cloud_run_invoker_iam_members" {
  description = "The identities allowed to invoke this cloud run service. Require the GCP IAM prefixes such as serviceAccount: or user:"
  type        = set(string)
  nullable    = false
}

variable "custom_audiences" {
  description = "The full URL to be used as a custom audience for invoking this Cloud Run."
  type        = set(string)
  nullable    = false
}

variable "binary_authorization" {
  description = "Binary Authorization config."
  type = object({
    breakglass_justification = optional(bool)
    use_default              = optional(bool)
    policy                   = optional(string)
  })
  nullable = true
}
