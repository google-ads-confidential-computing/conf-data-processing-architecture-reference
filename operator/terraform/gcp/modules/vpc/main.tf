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
locals {
  egress_internet_tag = "egress-internet"
}

# Dedicated VPC network.
module "vpc_network" {
  source  = "terraform-google-modules/network/google"
  version = "~> 4.0"

  project_id = var.project_id
  # If auto_create_subnetworks set to false, the network name has to be renamed
  # so terraform can destroy existing one and recreate a new one with customized
  # subnets.
  network_name = var.auto_create_subnetworks ? "${var.environment}-network" : "${var.environment}-${var.network_name}"

  # The subnetworks of the workers are created automatically based on
  # auto_create_subnetworks flag. If it set to true, subnets will be created
  # within the 10.128.0.0/9 range. Each subnet is /20 with 4096 addresses.
  # If it set to false, the worker subnet is customized below.
  auto_create_subnetworks = var.auto_create_subnetworks
  subnets                 = [] # Required argument.

  # Routes for each subnet are automatically created. Delete the default internet
  # gateway route and replace it with one restricted to the
  # `egress_internet_tag`.
  delete_default_internet_gateway_routes = true
  routes = [
    {
      name              = "${var.environment}-egress-internet"
      description       = "Route to the Internet."
      destination_range = "0.0.0.0/0"
      tags = join(",", [
        local.egress_internet_tag,
        "vpc-connector" # Tags of Serverless VPC connectors.
      ])
      next_hop_internet = "true"
    },
  ]
}

# Cloud NAT to provide internet to VMs without external IPs.
module "vpc_nat" {
  source  = "terraform-google-modules/cloud-nat/google"
  version = "~> 1.2"

  for_each = var.regions

  project_id    = var.project_id
  network       = module.vpc_network.network_self_link
  region        = each.value
  create_router = true
  router        = "${var.environment}-router"
}

resource "google_compute_subnetwork" "worker_subnet" {
  count = var.auto_create_subnetworks ? 0 : 1

  name          = var.network_name
  network       = module.vpc_network.network_self_link
  purpose       = "PRIVATE"
  region        = one(var.regions[*])
  ip_cidr_range = var.worker_subnet_cidr
}

resource "google_compute_subnetwork" "collector_subnet" {
  count = var.enable_remote_metric_aggregation ? 1 : 0

  name          = "${var.environment}-backend-subnet"
  network       = module.vpc_network.network_self_link
  purpose       = "PRIVATE"
  region        = one(var.regions[*])
  ip_cidr_range = var.collector_subnet_cidr
}

resource "google_compute_subnetwork" "proxy_subnet" {
  count = var.enable_remote_metric_aggregation ? 1 : 0

  name          = "${var.environment}-proxy-subnet"
  network       = module.vpc_network.network_self_link
  purpose       = "GLOBAL_MANAGED_PROXY"
  region        = one(var.regions[*])
  ip_cidr_range = var.proxy_subnet_cidr
  role          = "ACTIVE"
  lifecycle {
    ignore_changes = [ipv6_access_type]
  }
}
