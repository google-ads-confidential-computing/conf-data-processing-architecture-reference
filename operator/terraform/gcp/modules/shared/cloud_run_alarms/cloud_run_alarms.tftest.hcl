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
  source          = "../../../../../../tfmocks/google/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment             = ""
  notification_channel_id = ""
  cloud_run_service_names = ["service1", "service2"]
  service_prefix          = ""
  error_5xx_alarm_config = {
    duration_sec    = 0
    enable_alarm    = false
    eval_period_sec = 0
    error_threshold = 0
  }
  non_5xx_error_alarm_config = {
    duration_sec    = 0
    enable_alarm    = false
    eval_period_sec = 0
    error_threshold = 0
  }
  execution_time_alarm_config = {
    duration_sec    = 0
    enable_alarm    = false
    eval_period_sec = 0
    threshold_ms    = 0
  }
}

run "doesnt_enable_alarms" {
  command = plan

  assert {
    condition     = length(google_monitoring_alert_policy.error_5xx_count) == 0
    error_message = "Enabled alarms"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.non_5xx_error_count) == 0
    error_message = "Enabled alarms"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.request_latencies) == 0
    error_message = "Enabled alarms"
  }
}

run "enables_alarms" {
  command = plan

  variables {
    error_5xx_alarm_config = {
      duration_sec    = 0
      enable_alarm    = true
      eval_period_sec = 0
      error_threshold = 0
    }
    non_5xx_error_alarm_config = {
      duration_sec    = 0
      enable_alarm    = true
      eval_period_sec = 0
      error_threshold = 0
    }
    execution_time_alarm_config = {
      duration_sec    = 0
      enable_alarm    = true
      eval_period_sec = 0
      threshold_ms    = 0
    }
  }

  assert {
    condition     = length(google_monitoring_alert_policy.error_5xx_count) == length(var.cloud_run_service_names)
    error_message = "Didn't enable alarms"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.non_5xx_error_count) == length(var.cloud_run_service_names)
    error_message = "Didn't enable alarms"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.request_latencies) == length(var.cloud_run_service_names)
    error_message = "Didn't enable alarms"
  }
}
