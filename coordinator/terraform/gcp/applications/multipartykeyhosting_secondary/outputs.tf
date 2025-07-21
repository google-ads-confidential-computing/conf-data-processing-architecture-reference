# Copyright 2024 Google LLC
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

output "private_key_service_loadbalancer_ip" {
  value = module.private_key_service.loadbalancer_ip
}

output "private_key_base_url" {
  value = "https://${local.private_key_domain}"
}

output "key_storage_cloud_run_url" {
  value = module.keystorageservice.key_storage_cloud_run_url
}

output "key_storage_service_loadbalancer_ip" {
  value = module.keystorageservice.load_balancer_ip
}

output "key_storage_base_url" {
  value = "https://${local.key_storage_domain}"
}

output "workload_identity_pool_provider_name" {
  value = module.workload_identity_pool.workload_identity_pool_provider_name
}

output "wip_allowed_service_account" {
  value = module.workload_identity_pool.wip_allowed_service_account
}

output "wip_verified_service_account" {
  value = module.workload_identity_pool.wip_verified_service_account
}

output "allowed_operators_wipp_names" {
  description = "The workload identity pool provider names for each operator group."
  value = [
    for provider in module.key_set_acl_kek_pool :
    provider.workload_identity_pool_provider_name
  ]
}

output "key_encryption_key_id" {
  value = google_kms_crypto_key.key_encryption_key.id
}

output "kms_key_base_uri" {
  value = local.kms_key_base_uri
}

output "migration_kms_key_base_uri" {
  value = local.migration_kms_key_base_uri
}
