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

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google-beta"
      version = ">= 4.36"
    }
  }
}

locals {
  key_storage_package_zip = "${var.key_storage_service_jar}.zip"
}

# Archives the JAR in a ZIP file
data "archive_file" "function_archive" {
  type        = "zip"
  source_file = var.key_storage_service_jar
  output_path = local.key_storage_package_zip
}

resource "google_service_account" "key_storage_service_account" {
  # Service account id has a 30 character limit
  account_id   = "${var.environment}-keystorageuser"
  display_name = "KeyStorage Service Account"
}

resource "google_storage_bucket_object" "key_storage_archive" {
  # Need hash in name so cloudfunction knows to redeploy when code changes
  name   = "${var.environment}_key_storage_${data.archive_file.function_archive.output_md5}"
  bucket = var.package_bucket_name
  source = local.key_storage_package_zip
}

resource "google_cloudfunctions2_function" "key_storage_cloudfunction" {
  name        = "${var.environment}-${var.region}-${var.key_storage_cloudfunction_name}"
  location    = var.region
  description = "Cloud Function for key storage service"

  build_config {
    runtime     = var.use_java21_runtime ? "java21" : "java11"
    entry_point = "com.google.scp.coordinator.keymanagement.keystorage.service.gcp.KeyStorageServiceHttpFunction"
    source {
      storage_source {
        bucket = var.package_bucket_name
        object = google_storage_bucket_object.key_storage_archive.name
      }
    }
  }

  service_config {
    min_instance_count    = var.key_storage_service_cloudfunction_min_instances
    max_instance_count    = var.key_storage_service_cloudfunction_max_instances
    timeout_seconds       = var.cloudfunction_timeout_seconds
    available_memory      = "${var.key_storage_cloudfunction_memory}M"
    service_account_email = google_service_account.key_storage_service_account.email
    ingress_settings      = "ALLOW_INTERNAL_AND_GCLB"
    environment_variables = {
      PROJECT_ID                  = var.project_id
      GCP_KMS_URI                 = "gcp-kms://${var.key_encryption_key_id}"
      SPANNER_INSTANCE            = var.spanner_instance_name
      SPANNER_DATABASE            = var.spanner_database_name
      GCP_KMS_BASE_URI            = var.kms_key_base_uri
      MIGRATION_GCP_KMS_BASE_URI  = var.migration_kms_key_base_uri
      DISABLE_KEY_SET_ACL         = var.disable_key_set_acl
      POPULATE_MIGRATION_KEY_DATA = var.populate_migration_key_data
    }
  }

  labels = {
    environment = var.environment
  }

  lifecycle {
    ignore_changes = [
      # ATTOW these attributes always have detected changes even after apply.
      # Ignoring these for now until it's no longer an issue or we need use them
      # explicitly.
      service_config[0].environment_variables["LOG_EXECUTION_ID"],
      build_config[0].docker_repository
    ]
  }
}

# IAM entry for key storage service account to use the database
resource "google_spanner_database_iam_member" "keydb_iam_policy" {
  instance = var.spanner_instance_name
  database = var.spanner_database_name
  role     = "roles/spanner.databaseUser"
  member   = "serviceAccount:${google_service_account.key_storage_service_account.email}"
}

# IAM entry to invoke the function. Gen 2 cloud functions need CloudRun permissions.
resource "google_cloud_run_service_iam_member" "cloud_function_iam_policy" {
  count = var.allowed_wip_user_group != null ? 1 : 0

  project  = var.project_id
  location = google_cloudfunctions2_function.key_storage_cloudfunction.location
  service  = google_cloudfunctions2_function.key_storage_cloudfunction.name

  role   = "roles/run.invoker"
  member = "group:${var.allowed_wip_user_group}"
}

resource "google_cloud_run_service_iam_member" "cloud_function_iam_invokers" {
  for_each = toset(var.allowed_wip_service_accounts)

  project  = var.project_id
  location = google_cloudfunctions2_function.key_storage_cloudfunction.location
  service  = google_cloudfunctions2_function.key_storage_cloudfunction.name

  role   = "roles/run.invoker"
  member = "serviceAccount:${each.key}"
}

# IAM entry to allow function to encrypt and decrypt using KMS
resource "google_kms_crypto_key_iam_member" "kms_key_set_level_iam_policy" {
  for_each      = toset(var.key_sets)
  crypto_key_id = "${var.key_encryption_key_ring_id}/cryptoKeys/${var.environment}_${each.value}_key_encryption_key"
  role          = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  member        = "serviceAccount:${google_service_account.key_storage_service_account.email}"
}

resource "google_kms_crypto_key_iam_member" "kms_iam_policy" {
  crypto_key_id = var.key_encryption_key_id
  role          = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  member        = "serviceAccount:${google_service_account.key_storage_service_account.email}"
}
