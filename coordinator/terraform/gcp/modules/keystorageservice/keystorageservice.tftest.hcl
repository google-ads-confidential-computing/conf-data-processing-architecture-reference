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
  project_id                              = "project"
  environment                             = "environment"
  region                                  = ""
  key_storage_domain                      = ""
  source_container_image_url              = ""
  key_storage_memory                      = 0
  min_instances                           = 0
  max_instances                           = 0
  execution_environment                   = ""
  spanner_instance_name                   = ""
  spanner_database_name                   = ""
  alarms_enabled                          = false
  alarm_eval_period_sec                   = 300
  alarm_duration_sec                      = 60
  cloud_run_max_execution_time_max        = 0
  cloud_run_5xx_threshold                 = 0
  load_balancer_5xx_threshold             = 0
  load_balancer_max_95_percent_latency_ms = 50
  load_balancer_max_99_percent_latency_ms = 100
  lb_5xx_ratio_threshold                  = 0
  key_storage_severity_map                = {}
  populate_migration_key_data             = ""
  kms_key_base_uri                        = ""
  migration_kms_key_base_uri              = ""

  load_balancer_allowed_paths = ["/*"]

  lb_outlier_detection_enabled                               = false
  lb_outlier_detection_consecutive_errors                    = 0
  lb_outlier_detection_interval_seconds                      = 0
  lb_outlier_detection_base_ejection_time_seconds            = 0
  lb_outlier_detection_max_ejection_percent                  = 0
  lb_outlier_detection_enforcing_consecutive_errors          = 0
  lb_outlier_detection_consecutive_gateway_failure           = 0
  lb_outlier_detection_enforcing_consecutive_gateway_failure = 0

  cloud_run_5xx_error_alarm              = null
  cloud_run_execution_time_alarm         = null
  load_balancer_5xx_error_ratio_alarm    = null
  load_balancer_95_percent_latency_alarm = null
  load_balancer_99_percent_latency_alarm = null
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

run "structured_alarm_objects_propagate_to_cloud_run_and_load_balancer" {
  command = plan

  variables {
    alarms_enabled = true
    cloud_run_5xx_error_alarm = {
      threshold               = 5
      duration_sec            = 120
      alignment_period_sec    = 300
      evaluation_interval_sec = 60
    }
    cloud_run_execution_time_alarm = {
      threshold               = 1500
      duration_sec            = 180
      alignment_period_sec    = 300
      evaluation_interval_sec = 60
    }
    load_balancer_5xx_error_ratio_alarm = {
      threshold               = 0.05
      duration_sec            = 240
      alignment_period_sec    = 300
      evaluation_interval_sec = 60
    }
    load_balancer_95_percent_latency_alarm = {
      threshold               = 400
      duration_sec            = 300
      alignment_period_sec    = 300
      evaluation_interval_sec = 60
    }
    load_balancer_99_percent_latency_alarm = {
      threshold               = 800
      duration_sec            = 600
      alignment_period_sec    = 300
      evaluation_interval_sec = 60
    }
  }

  assert {
    condition     = module.cloud_run.error_5xx_alarm.threshold == 5 && module.cloud_run.error_5xx_alarm.duration_sec == 120
    error_message = "Expected cloud_run_5xx_error_alarm overrides to propagate to module.cloud_run."
  }

  assert {
    condition     = module.cloud_run.execution_time_alarm.threshold == 1500 && module.cloud_run.execution_time_alarm.duration_sec == 180
    error_message = "Expected cloud_run_execution_time_alarm overrides to propagate to module.cloud_run."
  }

  assert {
    condition     = module.load_balancer.load_balancer_5xx_error_ratio_alarm.threshold == 0.05 && module.load_balancer.load_balancer_5xx_error_ratio_alarm.duration_sec == 240
    error_message = "Expected load_balancer_5xx_error_ratio_alarm overrides to propagate to module.load_balancer."
  }

  assert {
    condition     = module.load_balancer.load_balancer_95_percent_latency_alarm.threshold == 400 && module.load_balancer.load_balancer_95_percent_latency_alarm.duration_sec == 300
    error_message = "Expected load_balancer_95_percent_latency_alarm overrides to propagate to module.load_balancer."
  }

  assert {
    condition     = module.load_balancer.load_balancer_99_percent_latency_alarm.threshold == 800 && module.load_balancer.load_balancer_99_percent_latency_alarm.duration_sec == 600
    error_message = "Expected load_balancer_99_percent_latency_alarm overrides to propagate to module.load_balancer."
  }
}
