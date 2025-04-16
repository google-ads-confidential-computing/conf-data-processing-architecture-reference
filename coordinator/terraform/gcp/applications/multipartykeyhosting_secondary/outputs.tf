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

output "encryption_key_service_cloudfunction_url" {
  value = module.encryptionkeyservice.encryption_key_service_cloudfunction_url
}

output "encryption_key_service_loadbalancer_ip" {
  value = module.encryptionkeyservice.encryption_key_service_loadbalancer_ip
}

output "encryption_key_base_url" {
  value = var.enable_domain_management ? "https://${local.encryption_key_domain}" : "http://${module.encryptionkeyservice.encryption_key_service_loadbalancer_ip}"
}

output "private_key_base_cloudrun_urls" {
  value = var.private_key_service_launch_cloud_run ? module.private_key_service[0].urls : []
}

output "private_key_service_loadbalancer_ip" {
  value = var.private_key_service_launch_cloud_run ? module.private_key_service[0].loadbalancer_ip : ""
}

output "private_key_base_url" {
  value = "https://${local.private_key_domain}"
}

output "key_storage_cloudfunction_url" {
  value = module.keystorageservice.key_storage_cloudfunction_url
}

output "key_storage_service_loadbalancer_ip" {
  value = module.keystorageservice.load_balancer_ip
}

output "key_storage_base_url" {
  value = var.enable_domain_management ? "https://${local.key_storage_domain}" : "http://${module.keystorageservice.load_balancer_ip}"
}

output "key_encryption_key_id" {
  value = google_kms_crypto_key.key_encryption_key.id
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
  value = module.allowed_operators.workload_identity_pool_provider_names
}

output "kms_key_base_uri" {
  value = local.kms_key_base_uri
}