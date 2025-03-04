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
  collector_service_account_email = var.user_provided_collector_sa_email == "" ? google_service_account.collector_service_account[0].email : var.user_provided_collector_sa_email
  egress_internet_tag             = "egress-internet"
}

resource "google_service_account" "collector_service_account" {
  count = var.user_provided_collector_sa_email == "" ? 1 : 0
  # Service account id has a 30 character limit
  account_id   = "${var.environment}-otel-collector"
  display_name = "OpenTelemetry Collector Service Account"
}

resource "google_project_iam_member" "collector_service_account_monitoring_viewer" {
  project = var.project_id
  role    = "roles/monitoring.viewer"
  member  = "serviceAccount:${local.collector_service_account_email}"
}


resource "google_project_iam_member" "collector_service_account_metric_writer_iam" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${local.collector_service_account_email}"
}

resource "google_project_iam_member" "collector_service_account_log_writer_iam" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${local.collector_service_account_email}"
}

resource "google_compute_instance" "collector" {
  name = "${var.environment}-otel-collector"
  # provider    = google-beta
  description = "This is used to create an OpenTelemetry collector for the region."

  tags = compact([
    "environment",
    var.environment,
    "otel-collector",
    local.egress_internet_tag,
  ])

  labels = {
    environment = var.environment
  }

  boot_disk {
    device_name = "${var.environment}-otel-collector"
    initialize_params {
      image = "projects/cos-cloud/global/images/family/cos-stable"
    }
  }

  network_interface {
    network = var.network
  }

  machine_type = var.collector_instance_type

  service_account {
    email  = local.collector_service_account_email
    scopes = ["cloud-platform"]
  }

  metadata = {
    # Use cloud-init to setup the collector
    # with a Cloud config file from the "user-data" metadata field
    # https://cloud.google.com/container-optimized-os/docs/how-to/create-configure-instance#using_cloud-init_with_the_cloud_config_format
    user-data              = var.collector_startup_script
    google-logging-enabled = true
  }

  scheduling {
    on_host_maintenance = "MIGRATE"
  }

  lifecycle {
    create_before_destroy = true
    ignore_changes        = [name]
  }
}

resource "google_compute_firewall" "allow_grpc_otel_collector" {
  name    = "${var.environment}-allow-grpc-otel-collector"
  network = var.network

  allow {
    protocol = "tcp"
    ports    = [tostring(var.collector_service_port)]
  }

  source_tags = [var.environment]
}
