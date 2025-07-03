/*
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

output "forwarding_rule" {
  value = google_compute_global_forwarding_rule.forwarding_rule
}

output "http_proxy" {
  value = google_compute_target_http_proxy.proxy
}

output "loadbalancer_dns_address" {
  value = format("%s.%s.%s:%s", var.environment, var.domain_name, trimsuffix(google_dns_managed_zone.collector_dns_zone.dns_name, "."), var.service_port)
}
