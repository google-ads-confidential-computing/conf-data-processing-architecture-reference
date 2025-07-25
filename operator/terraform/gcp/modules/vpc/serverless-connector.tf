# Copyright 2024-2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

module "serverless-connector" {
  count      = var.create_connectors ? 1 : 0
  version    = ">= 7.4.0"
  source     = "terraform-google-modules/network/google//modules/vpc-serverless-connector-beta"
  project_id = var.project_id
  vpc_connectors = [
    for index, region in tolist(var.regions) : {
      name    = "${var.environment}-${region}"
      region  = region
      network = module.vpc_network.network_name
      # 16 IPs since max number of connectors is limited to 16 by the VPC
      # service. Here we are using the 10.1.0.0/24 block, enough for up to 16
      # regions. Only 2 are expected.
      ip_cidr_range = "10.1.0.${index * 16}/28"
      subnet_name   = null
      machine_type  = var.connector_machine_type
      min_instances = 2
      max_instances = 10
    }
  ]
}
