/**
 * Copyright 2024 Google LLC
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
  worker_service_account_email = var.user_provided_worker_sa_email == "" ? google_service_account.worker_service_account[0].email : var.user_provided_worker_sa_email
  input_disk_image             = var.instance_disk_image != null && var.instance_disk_image != ""
}

resource "google_service_account" "worker_service_account" {
  count = var.user_provided_worker_sa_email == "" ? 1 : 0
  # Service account id has a 30 character limit
  account_id   = "${var.environment}-worker"
  display_name = "Worker Service Account"
}

data "google_compute_image" "tee_image" {
  count   = local.input_disk_image ? 0 : 1
  family  = var.instance_disk_image_family.image_family
  project = var.instance_disk_image_family.image_project
}

resource "null_resource" "worker_instance_replace_trigger" {
  triggers = {
    replace = var.worker_instance_force_replace ? timestamp() : ""
  }
}

resource "google_compute_instance_template" "worker_instance_template" {

  name_prefix = "${var.environment}-worker-template"
  # See #on-host-maintenance-migrate in this file when changing this value.
  machine_type = var.instance_type
  # See #on-host-maintenance-migrate in this file when changing this value.
  min_cpu_platform = "AMD Milan"

  disk {
    boot         = true
    device_name  = "${var.environment}-worker"
    source_image = local.input_disk_image ? var.instance_disk_image : data.google_compute_image.tee_image[0].self_link
    disk_type    = var.worker_instance_disk_type
    disk_size_gb = var.worker_instance_disk_size_gb
  }

  # TODO: Add custom VPC configurations
  network_interface {
    network    = var.network
    subnetwork = var.subnet_id
  }

  metadata = {
    google-logging-enabled           = var.worker_logging_enabled,
    google-monitoring-enabled        = var.worker_monitoring_enabled,
    scp-environment                  = var.environment,
    tee-image-reference              = var.worker_image,
    tee-signed-image-repos           = var.worker_image_signature_repos,
    tee-restart-policy               = var.worker_restart_policy,
    tee-impersonate-service-accounts = var.allowed_operator_service_account,
    tee-container-log-redirect       = var.worker_container_log_redirect,
    tee-monitoring-memory-enable     = var.worker_memory_monitoring_enabled
  }

  service_account {
    # Google recommends custom service accounts that have cloud-platform scope and permissions granted via IAM Roles.
    email  = local.worker_service_account_email
    scopes = ["cloud-platform"]
  }

  scheduling {
    # #on-host-maintenance-migrate
    #
    # To prevent Confidential VMs from being terminated for maintenance. The
    # machine types have to be N2D with AMD EPYC Milan CPU platforms running
    # ADM SEV.
    #
    # See:
    #  - https://cloud.google.com/compute/docs/instances/live-migration-process#limitations
    #  - b/368036183
    on_host_maintenance = "MIGRATE"
  }

  confidential_instance_config {
    enable_confidential_compute = true
    # See #on-host-maintenance-migrate in this file when changing this value.
    # Note: `confidential_instance_type` is not yet available on
    # hashicorp/google-beta provider until v5.16.0 and hashicorp/google provider
    # until v5.36.0. Until then the deprecating enable_confidential_compute will
    # have the same effect as `confidential_instance_type = "SEV"`.
    #
    # confidential_instance_type  = "SEV"
  }

  shielded_instance_config {
    enable_secure_boot = true
  }

  tags = compact([ # compact filters out empty strings.
    "environment",
    var.environment,
    var.egress_internet_tag
  ])

  labels = {
    environment = var.environment
  }

  # Create before destroy since template is being used by worker instance group
  lifecycle {
    create_before_destroy = true
    replace_triggered_by = [
      null_resource.worker_instance_replace_trigger
    ]
  }
}

# JobMetadata read/write permissions
resource "google_spanner_database_iam_member" "worker_jobmetadatadb_iam" {
  instance = var.metadatadb_instance_name
  database = var.metadatadb_name
  role     = "roles/spanner.databaseUser"
  member   = "serviceAccount:${local.worker_service_account_email}"
}

# JobQueue read/write permissions
resource "google_pubsub_subscription_iam_member" "worker_jobqueue_iam" {
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:${local.worker_service_account_email}"
  subscription = var.job_queue_sub
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
