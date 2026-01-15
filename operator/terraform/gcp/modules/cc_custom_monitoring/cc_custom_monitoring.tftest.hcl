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
  alarms_enabled                                      = false
  environment                                         = ""
  vm_instance_group_name                              = ""
  alarm_eval_period_sec                               = ""
  alarm_duration_sec                                  = ""
  notification_channel_id                             = ""
  joblifecyclehelper_job_pulling_failure_threshold    = 0
  joblifecyclehelper_job_completion_failure_threshold = 0
  joblifecyclehelper_job_extender_failure_threshold   = 0
  joblifecyclehelper_job_pool_failure_threshold       = 0
  joblifecyclehelper_job_processing_time_threshold    = 0
  joblifecyclehelper_job_release_failure_threshold    = 0
  joblifecyclehelper_job_waiting_time_threshold       = 0
}

run "doesnt_create_alarms" {
  command = plan

  assert {
    condition     = length(google_monitoring_alert_policy.joblifecyclehelper_job_pulling_failure_alert) == 0
    error_message = "Alarm created"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.joblifecyclehelper_job_completion_failure_alert) == 0
    error_message = "Alarm created"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.joblifecyclehelper_job_processing_time_alert) == 0
    error_message = "Alarm created"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.joblifecyclehelper_job_extender_failure_alert) == 0
    error_message = "Alarm created"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.joblifecyclehelper_job_pool_failure_alert) == 0
    error_message = "Alarm created"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.joblifecyclehelper_job_waiting_time_alert) == 0
    error_message = "Alarm created"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.joblifecyclehelper_job_release_failure_alert) == 0
    error_message = "Alarm created"
  }
}
