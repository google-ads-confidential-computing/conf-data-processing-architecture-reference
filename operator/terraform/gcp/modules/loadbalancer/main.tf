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

resource "google_compute_backend_service" "load_balancer" {
  name     = "${var.environment}-${var.service_name}-backendservice"
  provider = google-beta
  project  = var.project_id

  port_name             = var.service_port_name
  protocol              = "TCP"
  load_balancing_scheme = "INTERNAL_MANAGED"
  timeout_sec           = 10
  health_checks         = [google_compute_health_check.health_check.id]

  backend {
    group           = var.instance_group
    balancing_mode  = "UTILIZATION"
    max_utilization = 0.85
  }
}

resource "google_compute_target_tcp_proxy" "proxy" {
  name            = "${var.environment}-${var.service_name}-proxy"
  backend_service = google_compute_backend_service.load_balancer.id
}

resource "google_compute_global_forwarding_rule" "forwarding_rule" {
  name = "${var.environment}-${var.service_name}-forwarding-rule"

  ip_protocol           = "TCP"
  port_range            = var.service_port
  load_balancing_scheme = "INTERNAL_MANAGED"
  target                = google_compute_target_tcp_proxy.proxy.id
  subnetwork            = var.subnet_id

  labels = {
    environment = var.environment
    service     = var.service_name
    region      = var.region
  }

  depends_on = [var.proxy_subnet]
}

resource "google_dns_record_set" "service_dns" {
  name         = "${var.environment}.${var.domain_name}.${google_dns_managed_zone.collector_dns_zone.dns_name}"
  managed_zone = google_dns_managed_zone.collector_dns_zone.name
  type         = "A"
  ttl          = 10
  routing_policy {
    geo {
      location = var.region
      rrdatas  = [google_compute_global_forwarding_rule.forwarding_rule.ip_address]
    }
  }
}

resource "google_dns_managed_zone" "collector_dns_zone" {
  provider   = google-beta
  project    = var.project_id
  name       = "${var.environment}-collector-dns-zone"
  dns_name   = "${var.dns_name}."
  visibility = "private"
  private_visibility_config {
    networks {
      network_url = var.network
    }
  }
}


resource "google_compute_health_check" "health_check" {
  name = "${var.environment}-${var.service_name}-healthcheck"

  grpc_health_check {
    port = var.service_port
  }

  timeout_sec         = 3
  check_interval_sec  = 3
  healthy_threshold   = 2
  unhealthy_threshold = 4

  log_config {
    enable = true
  }
}
