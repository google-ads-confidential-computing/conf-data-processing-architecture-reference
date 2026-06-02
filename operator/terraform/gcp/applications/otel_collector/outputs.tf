/**
 * Copyright 2026 Google LLC
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

output "collector_instance_groups" {
  value       = module.otel_collector.collector_instance_groups
  description = "The instance groups of the OpenTelemetry Collector."
}

output "loadbalancer_dns_address" {
  value       = module.otel_collector_load_balancer.loadbalancer_dns_address
  description = "The DNS address of the OpenTelemetry Collector load balancer."
}

output "forwarding_rules" {
  value       = module.otel_collector_load_balancer.forwarding_rules
  description = "The forwarding rules of the OpenTelemetry Collector load balancer."
}

output "http_proxy" {
  value       = module.otel_collector_load_balancer.http_proxy
  description = "The HTTP proxy of the OpenTelemetry Collector load balancer."
}