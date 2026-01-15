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
  source          = "../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  project                                                 = ""
  environment                                             = "environment"
  backend_id                                              = "backend-id"
  cloud_run_information                                   = []
  enable_backend_logging                                  = false
  backend_service_paths                                   = []
  service_domain                                          = ""
  external_ip_address                                     = ""
  outlier_detection_interval_seconds                      = 0
  outlier_detection_base_ejection_time_seconds            = 0
  outlier_detection_consecutive_errors                    = 0
  outlier_detection_enforcing_consecutive_errors          = 0
  outlier_detection_consecutive_gateway_failure           = 0
  outlier_detection_enforcing_consecutive_gateway_failure = 0
  outlier_detection_max_ejection_percent                  = 0
}

run "creates_endpoint_group_for_each_cloud_run_info" {
  command = plan

  variables {
    cloud_run_information = [
      {
        region       = "us"
        service_name = "service1"
      },
      {
        region       = "eu"
        service_name = "service2"
      }
    ]
  }

  assert {
    condition     = google_compute_region_network_endpoint_group.cr_network_endpoint_group["us"].name == "environment-us-backend-id-cr"
    error_message = "Wrong group"
  }
  assert {
    condition     = google_compute_region_network_endpoint_group.cr_network_endpoint_group["us"].cloud_run[0].service == "service1"
    error_message = "Wrong cloud run service"
  }

  assert {
    condition     = google_compute_region_network_endpoint_group.cr_network_endpoint_group["eu"].name == "environment-eu-backend-id-cr"
    error_message = "Wrong group"
  }
  assert {
    condition     = google_compute_region_network_endpoint_group.cr_network_endpoint_group["eu"].cloud_run[0].service == "service2"
    error_message = "Wrong cloud run service"
  }
}

run "creates_backend_service_with_backend_for_each_group" {
  command = plan

  variables {
    cloud_run_information = [
      {
        region       = "us"
        service_name = "service1"
      },
      {
        region       = "eu"
        service_name = "service2"
      }
    ]
  }

  assert {
    # Since each backend uses the same mock for the endpoint group, they will
    # have the same ID.
    condition     = [for backend in google_compute_backend_service.cloud_run_backend.backend : backend["group"]] == ["endpoint-group-id", "endpoint-group-id"]
    error_message = "Wrong groups"
  }
}

run "url_map_only_routes_to_the_backend" {
  command = plan

  assert {
    condition     = google_compute_url_map.cr_load_balancer.path_matcher[0].path_rule[0].service == "backend_id"
    error_message = "Map routes to wrong service"
  }
}

run "target_https_proxy_uses_the_url_map_and_ssl_cert" {
  command = plan

  assert {
    condition     = google_compute_target_https_proxy.cr_loadbalancer_proxy.url_map == "url_map"
    error_message = "Uses wrong URL map"
  }
  assert {
    condition     = google_compute_target_https_proxy.cr_loadbalancer_proxy.ssl_certificates == tolist(["ssl_certificate_id"])
    error_message = "Uses wrong SSL cert"
  }
}

run "forwarding_rule_targets_the_lb_proxy" {
  command = plan

  assert {
    condition     = google_compute_global_forwarding_rule.cr_loadbalancer_config.target == "https_proxy_id"
    error_message = "Wrong forwarding target"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.url_map_name == "environment-backend-id-cr-lb"
    error_message = "Wrong URL map name"
  }
}
