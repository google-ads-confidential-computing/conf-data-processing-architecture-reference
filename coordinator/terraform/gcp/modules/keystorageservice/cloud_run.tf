# Copyright 2025 Google LLC
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


module "cloud_run" {
  count  = var.source_container_image_url == null ? 0 : 1
  source = "../cloud_run"

  environment         = var.environment
  project             = var.project_id
  region              = var.region
  description         = "Key Storage Service Cloud Run"
  service_name_suffix = "key-ss-cr"
  service_domain      = var.key_storage_domain

  # Access variables
  runtime_service_account_email          = google_service_account.key_storage_service_account
  allowed_all_users                      = false
  allowed_invoker_service_account_emails = var.allowed_wip_service_accounts
  allowed_user_group                     = var.allowed_wip_user_group
  ingress                                = "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"

  # CR settings
  source_container_image_url = var.source_container_image_url
  concurrency                = 2
  cpu_count                  = 2
  min_instance_count         = var.key_storage_service_cloudfunction_min_instances
  max_instance_count         = var.key_storage_service_cloudfunction_max_instances
  memory_mb                  = var.key_storage_cloudfunction_memory

  environment_variables = {
    PROJECT_ID          = var.project_id
    GCP_KMS_URI         = "gcp-kms://${var.key_encryption_key_id}"
    SPANNER_INSTANCE    = var.spanner_instance_name
    SPANNER_DATABASE    = var.spanner_database_name
    GCP_KMS_BASE_URI    = var.kms_key_base_uri
    DISABLE_KEY_SET_ACL = var.disable_key_set_acl
  }
}
