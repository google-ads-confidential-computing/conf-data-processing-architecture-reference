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
  type        = string
  description = "Environment where this service is deployed (e.g. dev, prod)."
}

variable "spanner_instance_config" {
  type        = string
  description = "Multi region config value for the Spanner Instance. Example: 'nam10' for North America."
}

variable "spanner_processing_units" {
  description = "Spanner's compute capacity. 1000 processing units = 1 node and must be set as a multiple of 100."
  type        = number
}

variable "custom_configuration_name" {
  description = "Name for the custom spanner configuration to be created."
  type        = string
  nullable    = true
}

variable "custom_configuration_display_name" {
  description = "Display name for the custom spanner configuration to be created."
  type        = string
}

variable "custom_configuration_base_config" {
  description = "Base spanner configuration used as starting basis for custom configuraiton."
  type        = string
}

variable "custom_configuration_read_replica_location" {
  description = "Region used in custom configuration as an additional read replica."
  type        = string
}

