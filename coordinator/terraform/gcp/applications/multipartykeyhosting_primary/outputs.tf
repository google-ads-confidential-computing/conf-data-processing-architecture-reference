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

output "get_public_key_cloudfunction_urls" {
  value = module.publickeyhostingservice.get_public_key_cloudfunction_urls
}

output "get_public_key_loadbalancer_ip" {
  value = module.public_key_service_load_balancer.loadbalancer_ip
}

output "public_key_base_url" {
  value = "https://${local.public_key_domain}"
}

output "private_key_service_loadbalancer_ip" {
  value = module.private_key_service.loadbalancer_ip
}

output "private_key_base_url" {
  value = "https://${local.private_key_domain}"
}

output "key_encryption_key_id" {
  value = module.keygenerationservice.key_encryption_key_id
}

output "key_generation_service_account" {
  value = module.keygenerationservice.key_generation_service_account
}

output "allowed_operators_wipp_names" {
  description = "The workload identity pool provider names for each operator group."
  value = [
    for provider in module.key_set_acl_kek_pool :
    provider.workload_identity_pool_provider_name
  ]
}
