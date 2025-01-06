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
      source  = "hashicorp/google"
      version = ">= 4.48"
    }
  }
}

locals {
  cloudfunction_name_suffix            = "encryption-key-service-cloudfunction"
  cloudfunction_package_zip            = "${var.encryption_key_service_jar}.zip"
  encryption_key_service_account_email = google_service_account.encryption_key_service_account.email
}

resource "google_service_account" "encryption_key_service_account" {
  # Service account id has a 30 character limit
  account_id   = "${var.environment}-encryptionkeyuser"
  display_name = "Encryption Key Service Account"
}

# Archives the JAR in a ZIP file
data "archive_file" "encryption_key_service_archive" {
  type        = "zip"
  source_file = var.encryption_key_service_jar
  output_path = local.cloudfunction_package_zip
}

resource "google_storage_bucket_object" "encryption_key_service_package_bucket_object" {
  # Need hash in name so cloudfunction knows to redeploy when code changes
  name   = "${var.environment}_${local.cloudfunction_name_suffix}_${data.archive_file.encryption_key_service_archive.output_md5}"
  bucket = var.package_bucket_name
  source = local.cloudfunction_package_zip
}

moved {
  from = google_cloudfunctions2_function.encryption_key_service_cloudfunction
  to   = google_cloudfunctions2_function.encryption_key_service_cloudfunction["0"]
}

resource "google_cloudfunctions2_function" "encryption_key_service_cloudfunction" {
  for_each = { for idx, region in var.regions : idx => region }
  name     = "${var.environment}-${each.value}-${local.cloudfunction_name_suffix}"
  location = each.value

  build_config {
    runtime     = "java11"
    entry_point = "com.google.scp.coordinator.keymanagement.keyhosting.service.gcp.EncryptionKeyService"
    source {
      storage_source {
        bucket = var.package_bucket_name
        object = google_storage_bucket_object.encryption_key_service_package_bucket_object.name
      }
    }
  }

  service_config {
    available_cpu                    = 2
    max_instance_request_concurrency = 100
    min_instance_count               = var.encryption_key_service_cloudfunction_min_instances
    max_instance_count               = var.encryption_key_service_cloudfunction_max_instances
    timeout_seconds                  = var.cloudfunction_timeout_seconds
    available_memory                 = "${var.encryption_key_service_cloudfunction_memory_mb}M"
    service_account_email            = local.encryption_key_service_account_email
    ingress_settings                 = "ALLOW_INTERNAL_AND_GCLB"
    environment_variables = {
      PROJECT_ID       = var.project_id
      SPANNER_INSTANCE = var.spanner_instance_name
      SPANNER_DATABASE = var.spanner_database_name
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

locals {
  cloud_functions         = [for cf in google_cloudfunctions2_function.encryption_key_service_cloudfunction : cf]
  cloud_function_a_name   = local.cloud_functions[0].name
  cloud_function_a_region = local.cloud_functions[0].location

  b_idx                   = var.add_secondary_region_to_encryption_service ? 1 : 0
  cloud_function_b_name   = local.cloud_functions[local.b_idx].name
  cloud_function_b_region = local.cloud_functions[local.b_idx].location

  cfs = { for idx, cf in local.cloud_functions : idx => cf }

  service_accounts_cfs = [
    for pair in setproduct(local.cloud_functions, var.allowed_operator_service_accounts) : {
      location        = pair[0].location
      name            = pair[0].name
      service_account = pair[1]
    }
  ]

  sa_cfs = { for idx, sa_cf in local.service_accounts_cfs : idx => sa_cf }
}

# IAM entry for service account to read from the database
resource "google_spanner_database_iam_member" "encryption_key_service_spannerdb_iam_policy" {
  instance = var.spanner_instance_name
  database = var.spanner_database_name
  role     = "roles/spanner.databaseReader"
  member   = "serviceAccount:${local.encryption_key_service_account_email}"
}

moved {
  from = google_cloud_run_service_iam_member.encryption_key_service_iam_policy[0]
  to   = google_cloud_run_service_iam_member.encryption_key_service_iam_policy["0"]
}

# IAM entry to invoke the function. Gen 2 cloud functions need CloudRun permissions.
resource "google_cloud_run_service_iam_member" "encryption_key_service_iam_policy" {
  for_each = var.allowed_operator_user_group != null ? local.cfs : {}

  project  = var.project_id
  location = each.value.location
  service  = each.value.name

  role   = "roles/run.invoker"
  member = "group:${var.allowed_operator_user_group}"
}

resource "google_cloud_run_service_iam_member" "encryption_key_service_invoker_service_accounts" {
  for_each = local.sa_cfs

  project  = var.project_id
  location = each.value.location
  service  = each.value.name

  role   = "roles/run.invoker"
  member = "serviceAccount:${each.value.location}"
}
