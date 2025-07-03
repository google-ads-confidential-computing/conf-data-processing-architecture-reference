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

variable "project_id" {
  description = "GCP Project ID in which this module will be created."
  type        = string
}

variable "regions" {
  description = "Regions with Cloud NAT support."
  type        = set(string)
}

variable "network_name_suffix" {
  description = "The suffix of the name of the VPC network of this module. The network name is a combination of environment name and this suffix. This is required if auto_create_subnetworks is disabled."
  type        = string
}

variable "auto_create_subnetworks" {
  description = "When enabled, the network will create a subnet for each region automatically across the 10.128.0.0/9 address range."
  type        = bool
}

variable "worker_subnet_cidr" {
  description = "The range of internal addresses that are owned by worker subnet.Map with Key: region and Value: cidr range."
  type        = map(string)
}

variable "collector_subnet_cidr" {
  description = "The range of internal addresses that are owned by collector subnet.Map with Key: region and Value: cidr range."
  type        = map(string)
}

variable "proxy_subnet_cidr" {
  description = "The range of internal addresses that are owned by proxy subnet.Map with Key: region and Value: cidr range."
  type        = map(string)
}

variable "enable_opentelemetry_collector" {
  description = "When true, create the collector subnet"
  type        = bool
}

variable "create_connectors" {
  description = "Whether to create Serverless VPC Access connectors in each region."
  type        = bool
}

variable "connector_machine_type" {
  description = "Machine type of the Serverless VPC Access connector."
  type        = string
}
