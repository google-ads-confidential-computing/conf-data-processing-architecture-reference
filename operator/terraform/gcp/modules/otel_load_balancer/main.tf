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
  protocol              = "HTTP"
  load_balancing_scheme = "INTERNAL_MANAGED"
  timeout_sec           = 10
  health_checks         = [google_compute_health_check.health_check.id]

  dynamic "backend" {
    for_each = var.instance_groups
    content {
      group          = backend.value
      balancing_mode = "RATE"

      # This is the ceiling rate of requests per instance.
      # This is set to 20k to allow for high traffic and prevent potentially
      # cross-region traffic.
      max_rate_per_instance = 20000
    }
  }
}

resource "google_compute_url_map" "default_url_map" {
  name            = "${var.environment}-http-lb-url-map"
  default_service = google_compute_backend_service.load_balancer.id
}

resource "google_compute_target_http_proxy" "proxy" {
  name    = "${var.environment}-http-lb-proxy"
  url_map = google_compute_url_map.default_url_map.id
}

resource "google_compute_global_forwarding_rule" "forwarding_rules" {
  for_each = var.subnets_per_region
  name     = "${var.environment}-${var.service_name}-${each.key}-forwarding-rule"

  ip_protocol           = "TCP"
  port_range            = var.service_port
  load_balancing_scheme = "INTERNAL_MANAGED"
  target                = google_compute_target_http_proxy.proxy.id
  subnetwork            = each.value

  labels = {
    environment = var.environment
    service     = var.service_name
    region      = each.key
  }

  depends_on = [var.proxy_only_subnets_per_region]
  lifecycle {
    precondition {
      condition     = lookup(var.proxy_only_subnets_per_region, each.key, "") != ""
      error_message = "There must be a proxy-only subnet for the region ${each.key}."
    }
  }
}

resource "google_dns_record_set" "service_dns" {
  name         = "${var.environment}.${var.domain_name}.${google_dns_managed_zone.collector_dns_zone.dns_name}"
  managed_zone = google_dns_managed_zone.collector_dns_zone.name
  type         = "A"
  ttl          = 10
  routing_policy {
    dynamic "geo" {
      for_each = google_compute_global_forwarding_rule.forwarding_rules
      content {
        location = geo.value.labels.region
        rrdatas  = [geo.value.ip_address]
      }
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
  name    = "${var.environment}-${var.service_name}-healthcheck"
  project = var.project_id

  tcp_health_check {
    port_name = var.service_port_name
    port      = var.service_port
  }

  timeout_sec        = 3
  check_interval_sec = 3
  # A so-far unhealthy instance will be marked healthy
  # after this many consecutive successes
  healthy_threshold = 2
  # A so-far healthy instance will be
  # marked unhealthy after this many consecutive failures
  unhealthy_threshold = 4

  log_config {
    enable = true
  }
}
