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
  environment                   = "environment"
  enable_new_metrics            = false
  enable_legacy_metrics         = false
  alarm_eval_period_sec         = ""
  alarm_duration_sec            = ""
  notification_channel_id       = ""
  java_job_validations_to_alert = []
}

run "doesnt_create_legacy_metrics" {
  command = plan

  assert {
    condition     = length(google_monitoring_metric_descriptor.jobclient_job_validation_failure_metric) == 0
    error_message = "Created legacy metric"
  }
  assert {
    condition     = length(google_monitoring_metric_descriptor.jobclient_job_client_error_metric) == 0
    error_message = "Created legacy metric"
  }
  assert {
    condition     = length(google_monitoring_metric_descriptor.worker_job_error_metric) == 0
    error_message = "Created legacy metric"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.jobclient_job_validation_failure_alert) == 0
    error_message = "Created legacy metric"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.worker_job_error_alert) == 0
    error_message = "Created legacy metric"
  }
}

run "creates_legacy_metrics" {
  command = plan

  variables {
    enable_legacy_metrics = true
  }

  assert {
    condition     = length(google_monitoring_metric_descriptor.jobclient_job_validation_failure_metric) == 1
    error_message = "Didn't create legacy metric"
  }
  assert {
    condition     = length(google_monitoring_metric_descriptor.jobclient_job_client_error_metric) == 1
    error_message = "Didn't create legacy metric"
  }
  assert {
    condition     = length(google_monitoring_metric_descriptor.worker_job_error_metric) == 1
    error_message = "Didn't create legacy metric"
  }
  # This one shouldn't be created because there are no validations to alert on
  assert {
    condition     = length(google_monitoring_alert_policy.jobclient_job_validation_failure_alert) == 0
    error_message = "Created legacy metric"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.worker_job_error_alert) == 1
    error_message = "Didn't create legacy metric"
  }
}

run "creates_validation_failure_alert" {
  command = plan

  variables {
    enable_legacy_metrics         = true
    java_job_validations_to_alert = ["Job1", "Job2"]
  }

  assert {
    condition     = length(google_monitoring_alert_policy.jobclient_job_validation_failure_alert) == 1
    error_message = "Didn't create legacy metric"
  }
  assert {
    condition     = strcontains(google_monitoring_alert_policy.jobclient_job_validation_failure_alert[0].conditions[0].condition_threshold[0].filter, "full_match(\"Job1|Job2\")")
    error_message = "Wrong condition filter"
  }
}

run "doesnt_create_new_metrics" {
  command = plan

  assert {
    condition     = length(google_monitoring_metric_descriptor.jobclient_job_validation_failure_counter_metric) == 0
    error_message = "Created metric"
  }
  assert {
    condition     = length(google_monitoring_metric_descriptor.jobclient_job_client_error_counter_metric) == 0
    error_message = "Created metric"
  }
  assert {
    condition     = length(google_monitoring_metric_descriptor.worker_job_error_new_metric) == 0
    error_message = "Created metric"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.jobclient_job_validation_failure_counter_alert) == 0
    error_message = "Created metric"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.worker_job_error_new_alert) == 0
    error_message = "Created metric"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.worker_job_error_new_alert) == 0
    error_message = "Created metric"
  }
}

run "creates_new_metrics" {
  command = plan

  variables {
    enable_new_metrics = true
  }

  assert {
    condition     = length(google_monitoring_metric_descriptor.jobclient_job_validation_failure_counter_metric) == 1
    error_message = "Didn't create metric"
  }
  assert {
    condition     = length(google_monitoring_metric_descriptor.jobclient_job_client_error_counter_metric) == 1
    error_message = "Didn't create metric"
  }
  assert {
    condition     = length(google_monitoring_metric_descriptor.worker_job_error_new_metric) == 1
    error_message = "Didn't create metric"
  }
  # This one should still be empty since there are no jobs to validate
  assert {
    condition     = length(google_monitoring_alert_policy.jobclient_job_validation_failure_counter_alert) == 0
    error_message = "Created metric"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.worker_job_error_new_alert) == 1
    error_message = "Didn't create metric"
  }
  assert {
    condition     = length(google_monitoring_alert_policy.worker_job_error_new_alert) == 1
    error_message = "Didn't create metric"
  }
}

run "creates_new_job_validation_metric" {
  command = plan

  variables {
    enable_new_metrics = true
    # Required since it is linked to a legacy metric
    enable_legacy_metrics         = true
    java_job_validations_to_alert = ["Job1", "Job2"]
  }

  assert {
    condition     = length(google_monitoring_alert_policy.jobclient_job_validation_failure_counter_alert) == 1
    error_message = "Didn't create metric"
  }
  assert {
    condition     = strcontains(google_monitoring_alert_policy.jobclient_job_validation_failure_counter_alert[0].conditions[0].condition_threshold[0].filter, "full_match(\"Job1|Job2\")")
    error_message = "Wrong condition filter"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  variables {
    enable_legacy_metrics = true
    enable_new_metrics    = true
  }

  assert {
    condition     = output.legacy_jobclient_job_validation_failure_metric_type == "custom.googleapis.com/scp/jobclient/environment/jobvalidationfailure"
    error_message = "Wrong metric type"
  }
  assert {
    condition     = output.legacy_jobclient_error_metric_type == "custom.googleapis.com/scp/jobclient/environment/jobclienterror"
    error_message = "Wrong metric type"
  }
  assert {
    condition     = output.legacy_worker_error_metric_type == "custom.googleapis.com/scp/worker/environment/workerjoberror"
    error_message = "Wrong metric type"
  }
  assert {
    condition     = output.new_jobclient_job_validation_failure_metric_type == "custom.googleapis.com/scp/jobclient/environment/jobvalidationfailurecounter"
    error_message = "Wrong metric type"
  }
  assert {
    condition     = output.new_jobclient_error_metric_type == "custom.googleapis.com/scp/jobclient/environment/jobclienterrorcounter"
    error_message = "Wrong metric type"
  }
  assert {
    condition     = output.new_worker_error_metric_type == "custom.googleapis.com/scp/worker/environment/workerjoberrornew"
    error_message = "Wrong metric type"
  }
}
