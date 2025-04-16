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

variable "environment" {
  description = "Environment where this service is deployed (e.g. dev, prod)."
  type        = string
}

variable "project_id" {
  description = "project id"
  type        = string
}

variable "region" {
  description = "Region where resources will be created."
  type        = string
}

variable "network" {
  description = "VPC Network name or self-link to use for service."
  type        = string
}

variable "subnet_id" {
  description = "Service subnet id."
  type        = string
}

variable "proxy_subnet" {
  description = "Envoy proxy subnet. Used to allow ingress into the service."
  type        = any
}

variable "instance_group" {
  description = "Instance group URL created by instance group managers."
  type        = string
}

variable "service_name" {
  description = "value"
  type        = string
}

variable "service_port_name" {
  description = "The name of the gRPC port that receives traffic destined for the service."
  type        = string
}

variable "service_port" {
  description = "The value of the gRpc port that receives traffic destined for the service."
  type        = number
}

variable "domain_name" {
  description = "The dns domain name for the service"
  type        = string

  validation {
    condition     = length(var.domain_name) > 0
    error_message = "The dns domain name for the service can not be empty"
  }
}

variable "dns_name" {
  description = "The dns name for the service"
  type        = string

  validation {
    condition     = length(var.dns_name) > 0
    error_message = "The dns name for the service can not be empty"
  }
}
