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

test {
  parallel = true
}
mock_provider "google" {
  source          = "../../../../../tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tfmocks/google-beta/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment       = "environment"
  project_id        = ""
  region            = ""
  network           = ""
  subnet_id         = ""
  proxy_subnet      = "any"
  instance_group    = ""
  service_name      = "service"
  service_port_name = ""
  service_port      = 0
  domain_name       = "domain"
  dns_name          = "dns"
}

run "load_balancer_uses_proper_health_check" {
  command = plan

  assert {
    condition     = google_compute_backend_service.load_balancer.health_checks == toset(["health_check_id"])
    error_message = "Wrong health check"
  }
}

run "url_map_uses_proper_load_balancer" {
  command = plan

  assert {
    condition     = google_compute_url_map.default_url_map.default_service == "backend_service_id"
    error_message = "Wrong service"
  }
}

run "target_http_proxy_uses_proper_url_map" {
  command = plan

  assert {
    condition     = google_compute_target_http_proxy.proxy.url_map == "url_map"
    error_message = "Wrong url map"
  }
}

run "forwarding_rule_uses_proper_proxy" {
  command = plan

  assert {
    condition     = google_compute_global_forwarding_rule.forwarding_rule.target == "http_proxy_id"
    error_message = "Wrong proxy"
  }
}

run "dns_record_set_uses_proper_zone_and_routing" {
  command = plan

  assert {
    condition     = google_dns_record_set.service_dns.managed_zone == "environment-collector-dns-zone"
    error_message = "Wrong zone"
  }
  assert {
    condition     = google_dns_record_set.service_dns.routing_policy[0].geo[0].rrdatas == tolist(["forwarding_rule_ip_address"])
    error_message = "Wrong rrdatas"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.forwarding_rule.name == "environment-service-forwarding-rule"
    error_message = "Wrong rule"
  }
  assert {
    condition     = output.http_proxy.name == "environment-http-lb-proxy"
    error_message = "Wrong proxy"
  }
  assert {
    condition     = output.loadbalancer_dns_address == "environment.domain.dns:0"
    error_message = "Wrong address"
  }
}
