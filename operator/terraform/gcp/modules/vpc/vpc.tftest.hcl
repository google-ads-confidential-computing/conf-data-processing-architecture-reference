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

test {
  parallel = true
}
mock_provider "google" {
  source          = "../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tools/tftesting/tfmocks/google-beta/"
  override_during = plan
}

# All run blocks should have "command = plan"
variables {
  environment                    = "environment"
  project_id                     = "project_id"
  regions                        = ["us"]
  otel_regions                   = ["eu", "na"]
  network_name_suffix            = "network-name-suffix"
  auto_create_subnetworks        = false
  worker_subnet_cidr             = { "us" : "0.0.0.1/0" }
  collector_subnet_cidr          = { "eu" : "0.0.0.0/0", "na" : "0.0.0.0/0" }
  proxy_subnet_cidr              = { "eu" : "0.0.0.0/0", "na" : "0.0.0.0/0" }
  enable_opentelemetry_collector = true
  create_connectors              = false
  connector_machine_type         = "connector_machine_type"
}

run "cidr_blocks_missing_region_fail_precondition" {
  command = plan

  variables {
    collector_subnet_cidr = { "eu" : "0.0.0.1/0" }
    proxy_subnet_cidr     = { "na" : "0.0.0.1/0" }
    worker_subnet_cidr    = {}
  }

  expect_failures = [
    google_compute_subnetwork.collector_subnet["na"],
    google_compute_subnetwork.proxy_subnet["eu"],
    google_compute_subnetwork.worker_subnet["us"],
  ]
}

run "creates_route_without_auto_subnets" {
  command = plan

  assert {
    condition = module.vpc_network.route_names == [
      "environment-egress-inet"
    ]

    error_message = "VPC network route names are not correct"
  }
}

run "creates_route_with_auto_subnets" {
  command = plan

  variables {
    auto_create_subnetworks = true
  }

  assert {
    condition = module.vpc_network.route_names == [
      "environment-egress-internet"
    ]

    error_message = "VPC network route names are not correct"
  }
  assert {
    condition     = length(output.worker_subnet_ids) == length(var.regions)
    error_message = "# of worker_subnet_ids does not equal the # of regions"
  }
}

run "creates_nat_for_each_region" {
  command = plan

  assert {
    condition = length(module.vpc_nat) == length(var.regions) + length(var.otel_regions)

    error_message = "# of VPC NATs does not equal the # of regions"
  }
  assert {
    condition     = module.vpc_nat["us"].router_name == "environment-router"
    error_message = "Router name for VPC NAT is not correct"
  }
  assert {
    condition     = module.vpc_nat["eu"].router_name == "environment-router"
    error_message = "Router name for VPC NAT is not correct"
  }
  assert {
    condition     = module.vpc_nat["na"].router_name == "environment-router"
    error_message = "Router name for VPC NAT is not correct"
  }
}

run "creates_worker_subnet_for_each_region_if_not_autocreating" {
  command = plan

  assert {
    condition     = length(google_compute_subnetwork.worker_subnet) == length(var.regions)
    error_message = "# of worker_subnet's does not equal the # of regions"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.egress_internet_tag == "egress-internet"
    error_message = "egress_internet_tag is incorrect"
  }
  assert {
    condition     = output.connectors == null
    error_message = "connectors are set"
  }
  assert {
    condition     = output.worker_subnet_ids["us"] == "environment-worker-subnet"
    error_message = "US subnet ID is incorrect"
  }
  assert {
    condition     = length(output.worker_subnet_ids) == length(var.regions)
    error_message = "# of worker_subnet_ids does not equal the # of regions"
  }
  assert {
    condition     = length(output.collector_subnet_ids) == length(var.otel_regions)
    error_message = "# of collector_subnet_ids does not equal the # of regions"
  }
  assert {
    condition     = length(output.proxy_subnet_ids) == length(var.otel_regions)
    error_message = "# of proxy_subnet_ids does not equal the # of regions"
  }
  assert {
    condition     = output.network == "self_link"
    error_message = "Output network is not correct"
  }
}
