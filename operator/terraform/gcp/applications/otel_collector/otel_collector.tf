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

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 4.36"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.region_zone
}

module "otel_collector" {
  source      = "../../modules/opentelemetry_collector"
  environment = var.environment
  project_id  = var.project_id
  network     = var.network
  region      = var.region
  subnet_id   = var.collector_subnet_id

  user_provided_collector_sa_email = var.user_provided_collector_sa_email
  collector_instance_type          = var.collector_instance_type
  max_collector_instances          = var.max_collector_instances
  min_collector_instances          = var.min_collector_instances
  collector_service_port_name      = var.collector_service_port_name
  collector_service_port           = var.collector_service_port
  collector_min_instance_ready_sec = var.collector_min_instance_ready_sec
  collector_cpu_utilization_target = var.collector_cpu_utilization_target
  collector_startup_script = templatefile("../../modules/opentelemetry_collector/collector_startup.tftpl", {
    otel_collector_image_uri = "otel/opentelemetry-collector-contrib:0.122.1"

    http_receiver_port   = var.collector_service_port
    metric_prefix        = "custom.googleapis.com"
    send_batch_max_size  = var.collector_send_batch_max_size
    send_batch_size      = var.collector_send_batch_size
    send_batch_timeout   = var.collector_send_batch_timeout
    collector_queue_size = var.collector_queue_size
  })

  collector_exceed_cpu_usage_alarm         = var.collector_exceed_cpu_usage_alarm
  collector_exceed_memory_usage_alarm      = var.collector_exceed_memory_usage_alarm
  collector_export_error_alarm             = var.collector_export_error_alarm
  collector_startup_error_alarm            = var.collector_startup_error_alarm
  collector_crash_error_alarm              = var.collector_crash_error_alarm
  export_metric_to_collector_error_alarm   = var.export_metric_to_collector_error_alarm
  collector_queue_size_alarm               = var.collector_queue_size_alarm
  collector_send_metric_failure_rate_alarm = var.collector_send_metric_failure_rate_alarm
  collector_refuse_metric_rate_alarm       = var.collector_refuse_metric_rate_alarm
}

module "otel_collector_load_balancer" {
  source            = "../../modules/otel_load_balancer"
  environment       = var.environment
  project_id        = var.project_id
  network           = var.network
  region            = var.region
  subnet_id         = var.collector_subnet_id
  proxy_subnet      = var.proxy_subnet_id
  instance_group    = module.otel_collector.collector_instance_group
  service_name      = var.collector_service_name
  service_port_name = var.collector_service_port_name
  service_port      = var.collector_service_port
  dns_name          = var.collector_dns_name
  domain_name       = var.collector_domain_name
}