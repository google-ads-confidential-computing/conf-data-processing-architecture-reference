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

resource "google_compute_region_network_endpoint_group" "cr_network_endpoint_group" {
  for_each = { for cr_info in var.cloud_run_information : cr_info.region => cr_info }

  name                  = "${var.environment}-${each.value.region}-${var.backend_id}-cr" # Max 63 chars
  network_endpoint_type = "SERVERLESS"
  region                = each.value.region
  cloud_run {
    service = each.value.service_name
  }
}

resource "google_compute_backend_service" "cloud_run_backend" {
  name                            = "${var.environment}-${var.backend_id}-be" # Max 63 chars
  project                         = var.project
  description                     = "Backend service to point to ${var.backend_id} backend Cloud Runs in env ${var.environment}."
  enable_cdn                      = false
  protocol                        = "HTTPS"
  connection_draining_timeout_sec = 30
  load_balancing_scheme           = "EXTERNAL_MANAGED"

  dynamic "backend" {
    for_each = google_compute_region_network_endpoint_group.cr_network_endpoint_group

    content {
      description = "Backend service for ${backend.value.name}"
      group       = backend.value.id
    }
  }

  log_config {
    enable = var.enable_backend_logging
  }

  outlier_detection {
    interval {
      seconds = var.outlier_detection_interval_seconds
    }
    base_ejection_time {
      seconds = var.outlier_detection_base_ejection_time_seconds
    }

    consecutive_errors           = var.outlier_detection_consecutive_errors
    enforcing_consecutive_errors = var.outlier_detection_enforcing_consecutive_errors

    consecutive_gateway_failure           = var.outlier_detection_consecutive_gateway_failure
    enforcing_consecutive_gateway_failure = var.outlier_detection_enforcing_consecutive_gateway_failure

    max_ejection_percent = var.outlier_detection_max_ejection_percent
  }

  // Needed in the event that a region is removed.
  // In this case, the terraform apply would normally fail,
  // since terraform would first try to delete the network
  // endpoint groups, before updating this resource to
  // remove the backend, so this ensures the backend is updated first.
  lifecycle {
    create_before_destroy = true
  }
}

# The URL map creates the HTTPS LB
resource "google_compute_url_map" "cr_load_balancer" {
  name    = "${var.environment}-${var.backend_id}-cr-lb"
  project = var.project

  host_rule {
    hosts        = ["*"]
    path_matcher = "${var.environment}-cr-lb-allowed"
  }

  # Return a 301 for requests that go to an unmatched path
  default_url_redirect {
    https_redirect         = true
    redirect_response_code = "MOVED_PERMANENTLY_DEFAULT"
    strip_query            = false
  }

  path_matcher {
    name = "${var.environment}-cr-lb-allowed"

    # Return a 301 for requests that go to an unmatched path
    default_url_redirect {
      https_redirect         = true
      redirect_response_code = "MOVED_PERMANENTLY_DEFAULT"
      strip_query            = false
    }

    path_rule {
      # Only route these paths to the backend
      paths   = var.backend_service_paths
      service = google_compute_backend_service.cloud_run_backend.id
    }
  }
}

# Proxy to loadbalancer. HTTPS with custom domain
resource "google_compute_target_https_proxy" "cr_loadbalancer_proxy" {
  project = var.project
  name    = "${var.environment}-${var.backend_id}-cr-proxy"
  url_map = google_compute_url_map.cr_load_balancer.id

  ssl_certificates = [
    google_compute_managed_ssl_certificate.cr_load_balancer_cert.id
  ]
}

# Map IP address and loadbalancer proxy
resource "google_compute_global_forwarding_rule" "cr_loadbalancer_config" {
  project    = var.project
  name       = "${var.environment}-${var.backend_id}-lb-fwd-rule"
  ip_address = var.external_ip_address
  port_range = "443"
  target     = google_compute_target_https_proxy.cr_loadbalancer_proxy.id
}

# Creates SSL cert for given domain. Terraform does not wait for SSL cert to be provisioned before the `apply` operation
# succeeds. As long as the hosted zone exists, it can take up to 20 mins for the cert to be provisioned.
# See console for status: https://console.cloud.google.com/loadbalancing/advanced/sslCertificates/list
# Note: even if status of cert becomes 'Active', it can still take around 10 mins for requests to the domain to work.
resource "google_compute_managed_ssl_certificate" "cr_load_balancer_cert" {
  project = var.project
  name    = "${var.environment}-${var.backend_id}-cr-cert"

  lifecycle {
    create_before_destroy = true
  }

  managed {
    domains = [var.service_domain]
  }
}
