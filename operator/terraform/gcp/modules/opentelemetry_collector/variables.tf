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

variable "collector_service_port" {
  description = "The gRPC port that receives traffic destined for the OpenTelemetry collector."
  type        = number
}

