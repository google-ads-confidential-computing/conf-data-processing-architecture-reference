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
  startup_script_hash             = sha256(var.collector_startup_script)
}

resource "null_resource" "server_instance_replace_trigger" {
  triggers = {
    replace = local.startup_script_hash
  }
}


resource "null_resource" "collector_template_mig_replace_trigger" {
  triggers = {
    # To work around the google_compute_region_instance_group_manager update
    # error "Networks specified in new and old network interfaces must be the
    # same." when network is changed in the template. This creates a resource
    # trigger for replacement.
    #
    # Using an express that considers the complete network_interface list does
    # not seem to work and always forces replacement, perhaps due to some non-
    # determinism of the list. Direct indexing into the first element works as
    # desired.
    network   = length(google_compute_instance_template.collector.network_interface) > 0 ? google_compute_instance_template.collector.network_interface[0].network : ""
    subnet_id = length(var.subnet_id) > 0 ? var.subnet_id : ""
  }
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

resource "google_compute_region_instance_group_manager" "collector_instance" {
  provider           = google-beta
  region             = var.region
  project            = var.project_id
  name               = "${var.environment}-${var.region}-collector-mig"
  description        = "The managed instance group for SCP worker instances."
  base_instance_name = "${var.environment}-${var.region}-collector"

  named_port {
    name = var.collector_service_port_name
    port = var.collector_service_port
  }

  auto_healing_policies {
    health_check      = google_compute_health_check.collector.id
    initial_delay_sec = var.collector_min_instance_ready_sec
  }

  version {
    instance_template = google_compute_instance_template.collector.id
  }

  update_policy {
    minimal_action = "REPLACE"
    type           = "PROACTIVE"
    # Avoid collector downtime during update by setting max_unavailable_fixed
    # to 0. The default value of this field is the number of the zone in the
    # region.
    max_unavailable_fixed = 0
    # max_surge_fixed needs to be >= the number of zones in the region, so we
    # set it to a relative large number.
    max_surge_fixed = 10
    # Waiting time for the new instance to be ready.
    min_ready_sec = var.collector_min_instance_ready_sec
  }

  lifecycle {
    create_before_destroy = true
    replace_triggered_by = [
      null_resource.collector_template_mig_replace_trigger
    ]
  }
}

resource "google_compute_instance_template" "collector" {
  region      = var.region
  project     = var.project_id
  name_prefix = "${var.environment}-${var.region}-collector"
  provider    = google-beta
  description = "This is used to create an OpenTelemetry collector for the region."

  tags = compact([
    "environment",
    var.environment,
    "allow-otlp",
    local.egress_internet_tag,
  ])

  labels = {
    environment = var.environment
  }

  disk {
    device_name  = "${var.environment}-otel-collector"
    source_image = "projects/cos-cloud/global/images/family/cos-121-lts"
  }

  network_interface {
    network            = var.network
    subnetwork         = var.subnet_id
    subnetwork_project = var.project_id
  }

  machine_type = var.collector_instance_type

  shielded_instance_config {
    enable_secure_boot = true
  }

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
    replace_triggered_by  = [null_resource.server_instance_replace_trigger]
  }

}

resource "google_compute_region_autoscaler" "collector_autoscaler" {
  provider = google-beta

  name    = "${var.environment}-collector-autoscaler"
  project = var.project_id
  region  = var.region
  target  = google_compute_region_instance_group_manager.collector_instance.id

  autoscaling_policy {
    max_replicas    = var.max_collector_instances
    min_replicas    = var.min_collector_instances
    cooldown_period = var.collector_min_instance_ready_sec
    # Only scale up to avoid collector flush metric data too often
    mode = "ONLY_UP"

    cpu_utilization {
      target = var.collector_cpu_utilization_target
    }
  }

  lifecycle {
    replace_triggered_by = [google_compute_region_instance_group_manager.collector_instance.id]
  }
}

resource "google_compute_health_check" "collector" {
  name = "${var.environment}-otel-collector-auto-heal-hc"

  tcp_health_check {
    port_name = var.collector_service_port_name
    port      = var.collector_service_port
  }

  timeout_sec         = 3
  check_interval_sec  = 3
  healthy_threshold   = 2
  unhealthy_threshold = 4
}

resource "google_compute_firewall" "allow_grpc_otel_collector" {
  name    = "${var.environment}-allow-grpc-otel-collector"
  network = var.network

  allow {
    protocol = "tcp"
    ports    = [tostring(var.collector_service_port)]
  }

  target_tags   = ["allow-otlp"]
  source_ranges = ["0.0.0.0/0"]
}
