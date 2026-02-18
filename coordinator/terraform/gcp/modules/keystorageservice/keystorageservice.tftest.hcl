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

// google-beta provider is renamed to google
mock_provider "google" {
  source          = "../../../../../tools/tftesting/tfmocks/google-beta/"
  override_during = plan
}

variables {
  project_id                                = "project"
  environment                               = "environment"
  region                                    = ""
  key_storage_domain                        = ""
  load_balancing_scheme                     = ""
  source_container_image_url                = ""
  key_storage_memory                        = 0
  min_instances                             = 0
  max_instances                             = 0
  execution_environment                     = ""
  spanner_instance_name                     = ""
  spanner_database_name                     = ""
  alarms_enabled                            = false
  alarm_eval_period_sec                     = ""
  alarm_duration_sec                        = ""
  cloud_run_max_execution_time_max          = 0
  cloud_run_5xx_threshold                   = 0
  cloud_run_alert_on_memory_usage_threshold = 0
  lb_max_latency_ms                         = 5
  lb_5xx_threshold                          = 0
  lb_5xx_ratio_threshold                    = 0
  key_storage_severity_map                  = {}
  populate_migration_key_data               = ""
  kms_key_base_uri                          = ""
  migration_kms_key_base_uri                = ""

  external_managed_migration_state              = "PREPARE"
  external_managed_migration_testing_percentage = 0

  forwarding_rule_load_balancing_scheme                        = "EXTERNAL"
  external_managed_backend_bucket_migration_state              = null
  external_managed_backend_bucket_migration_testing_percentage = null

  lb_outlier_detection_enabled                               = false
  lb_outlier_detection_consecutive_errors                    = 0
  lb_outlier_detection_interval_seconds                      = 0
  lb_outlier_detection_base_ejection_time_seconds            = 0
  lb_outlier_detection_max_ejection_percent                  = 0
  lb_outlier_detection_enforcing_consecutive_errors          = 0
  lb_outlier_detection_consecutive_gateway_failure           = 0
  lb_outlier_detection_enforcing_consecutive_gateway_failure = 0
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.load_balancer_ip == "mock_compute_global_address"
    error_message = "Wrong IP"
  }
  assert {
    condition     = output.key_storage_service_account_email == "mock_google_service_account_email"
    error_message = "Wrong email"
  }
  assert {
    condition     = output.key_storage_cloud_run_url == "mock_cloud_run_v2_uri"
    error_message = "Wrong URL"
  }
}
