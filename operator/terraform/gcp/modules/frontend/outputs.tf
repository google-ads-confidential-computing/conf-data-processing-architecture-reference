/**
 * Copyright 2022-2025 Google LLC
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

output "frontend_service_cloudfunction_url" {
  value       = var.create_frontend_service_cloud_function ? google_cloudfunctions2_function.frontend_service_cloudfunction[0].service_config[0].uri : ""
  description = "The frontend service cloud function gen2 url."
}

output "frontend_cloud_run_information" {
  value = toset([
    for region, cloud_run_module in module.cloud_run_fe : {
      region = region
      # This isn't actually the service_name, but dependent modules may
      # need it named this way
      service_name = cloud_run_module
    }
  ])
  description = "A set of object containing the Cloud Run region and service name."
}


output "frontend_service_cloud_run_urls" {
  value       = length(var.frontend_service_cloud_run_regions) > 0 ? [for region, cloud_run in module.cloud_run_fe : cloud_run.cloud_run_url] : []
  description = "The frontend service Cloud Run URLs."
}

output "frontend_service_load_balancer_ip_address" {
  value       = length(google_compute_global_address.cloud_run_fe_ip_address) > 0 ? google_compute_global_address.cloud_run_fe_ip_address[0].address : ""
  description = "The external IP address of the entry-point load balancer for the FE service."
}
