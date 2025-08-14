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

locals {
  worker_service_account_email = var.user_provided_worker_sa_email == "" ? google_service_account.generated_worker_service_account[0].email : var.user_provided_worker_sa_email
}

# JobMetadata read/write permissions
resource "google_spanner_database_iam_member" "worker_jobmetadatadb_iam" {
  instance = var.metadatadb_instance_name
  database = var.metadatadb_name
  role     = "roles/spanner.databaseUser"
  member   = "serviceAccount:${local.worker_service_account_email}"
}

resource "google_service_account" "generated_worker_service_account" {
  count = var.user_provided_worker_sa_email == "" ? 1 : 0
  # Service account id has a 30 character limit
  account_id   = "${var.environment}-worker"
  display_name = "Worker Service Account"
}

resource "google_project_iam_member" "worker_storage_iam" {
  role    = "roles/storage.admin"
  member  = "serviceAccount:${local.worker_service_account_email}"
  project = var.project_id
}

resource "google_project_iam_member" "worker_secretmanager_iam" {
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${local.worker_service_account_email}"
  project = var.project_id
}

resource "google_project_iam_member" "worker_logging_iam" {
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${local.worker_service_account_email}"
  project = var.project_id
}

resource "google_project_iam_member" "worker_monitoring_iam" {
  role    = "roles/monitoring.editor"
  member  = "serviceAccount:${local.worker_service_account_email}"
  project = var.project_id
}

resource "google_project_iam_member" "worker_instance_group_iam" {
  role    = "roles/compute.networkAdmin"
  member  = "serviceAccount:${local.worker_service_account_email}"
  project = var.project_id
}

resource "google_project_iam_member" "worker_workload_user_iam" {
  role    = "roles/confidentialcomputing.workloadUser"
  member  = "serviceAccount:${local.worker_service_account_email}"
  project = var.project_id
}

resource "google_project_iam_member" "worker_pubsub_publisher_iam" {
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${local.worker_service_account_email}"
  project = var.project_id
}
