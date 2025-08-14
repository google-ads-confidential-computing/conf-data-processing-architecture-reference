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

resource "google_monitoring_metric_descriptor" "jobclient_job_validation_failure_counter_metric" {
  count        = var.enable_new_metrics ? 1 : 0
  display_name = "Job Client Validation Failures"
  description  = "Custom metric for validation failures in the job client."

  type        = "custom.googleapis.com/scp/jobclient/${var.environment}/jobvalidationfailurecounter"
  metric_kind = "CUMULATIVE"
  value_type  = "DOUBLE"

  labels {
    key = "Validator"
  }
}

resource "google_monitoring_metric_descriptor" "jobclient_job_client_error_counter_metric" {
  count        = var.enable_new_metrics ? 1 : 0
  display_name = "Job Client Errors"
  description  = "Custom metric for errors in the job client."

  type        = "custom.googleapis.com/scp/jobclient/${var.environment}/jobclienterrorcounter"
  metric_kind = "CUMULATIVE"
  value_type  = "DOUBLE"

  labels {
    key = "ErrorReason"
  }
}

resource "google_monitoring_metric_descriptor" "worker_job_error_new_metric" {
  count        = var.enable_new_metrics ? 1 : 0
  display_name = "Worker Job Errors"
  description  = "Custom metric for errors with worker job handling."

  type        = "custom.googleapis.com/scp/worker/${var.environment}/workerjoberrornew"
  metric_kind = "GAUGE"
  value_type  = "DOUBLE"

  labels {
    key = "Type"
  }
}

