# Copyright 2022 Google LLC
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

locals {
  function_name = google_cloudfunctions2_function.key_storage_cloudfunction.name
}

module "keystorageservice_loadbalancer_alarms" {
  source = "../shared/loadbalancer_alarms"
  count  = var.alarms_enabled ? 1 : 0

  environment        = var.environment
  load_balancer_name = var.load_balancer_name
  service_prefix     = "${var.environment} Key Storage Service"

  eval_period_sec            = var.alarm_eval_period_sec
  error_5xx_threshold        = var.lb_5xx_threshold
  error_5xx_ratio_threshold  = var.lb_5xx_ratio_threshold
  max_latency_ms             = var.lb_max_latency_ms
  duration_sec               = var.alarm_duration_sec
  load_balancer_severity_map = var.key_storage_severity_map
}

module "keystorageservice_cloudfunction_alarms" {
  source = "../shared/cloudfunction_alarms"
  count  = var.alarms_enabled ? 1 : 0

  environment    = var.environment
  function_name  = local.function_name
  service_prefix = "${var.environment} Key Storage Service"

  eval_period_sec                 = var.alarm_eval_period_sec
  error_5xx_threshold             = var.cloudfunction_5xx_threshold
  execution_time_max              = var.cloudfunction_max_execution_time_max
  execution_error_ratio_threshold = var.cloudfunction_error_ratio_threshold
  alert_on_memory_usage_threshold = var.cloudfunction_alert_on_memory_usage_threshold
  duration_sec                    = var.alarm_duration_sec
  cloud_function_severity_map     = var.key_storage_severity_map
}

module "keystorageservice_monitoring_dashboard" {
  source        = "../shared/cloudfunction_dashboards"
  environment   = var.environment
  service_name  = "Key Storage"
  function_name = local.function_name
}
