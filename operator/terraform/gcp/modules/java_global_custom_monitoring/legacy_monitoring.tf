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

locals {
  job_validation_filter = length(var.java_job_validations_to_alert) == 0 ? "" : format(" AND metric.label.\"Validator\"=monitoring.regex.full_match(\"%s\")", join("|", var.java_job_validations_to_alert))
}

resource "google_monitoring_metric_descriptor" "jobclient_job_validation_failure_metric" {
  count        = var.enable_legacy_metrics ? 1 : 0
  display_name = "Job Client Validation Failures"
  description  = "Custom metric for validation failures in the job client."

  type        = "custom.googleapis.com/scp/jobclient/${var.environment}/jobvalidationfailure"
  metric_kind = "GAUGE"
  value_type  = "DOUBLE"

  labels {
    key = "Validator"
  }
}

resource "google_monitoring_metric_descriptor" "jobclient_job_client_error_metric" {
  count        = var.enable_legacy_metrics ? 1 : 0
  display_name = "Job Client Errors"
  description  = "Custom metric for errors in the job client."

  type        = "custom.googleapis.com/scp/jobclient/${var.environment}/jobclienterror"
  metric_kind = "GAUGE"
  value_type  = "DOUBLE"

  labels {
    key = "ErrorReason"
  }
}

resource "google_monitoring_metric_descriptor" "worker_job_error_metric" {
  count        = var.enable_legacy_metrics ? 1 : 0
  display_name = "Worker Job Errors"
  description  = "Custom metric for errors with worker job handling."

  type        = "custom.googleapis.com/scp/worker/${var.environment}/workerjoberror"
  metric_kind = "GAUGE"
  value_type  = "DOUBLE"

  labels {
    key = "Type"
  }
}

resource "google_monitoring_alert_policy" "jobclient_job_validation_failure_alert" {
  count        = length(var.java_job_validations_to_alert) > 0 && var.enable_legacy_metrics ? 1 : 0
  display_name = "${var.environment} Job Client Validation Failure Alert"
  combiner     = "OR"
  conditions {
    display_name = "Validation Failures"
    condition_threshold {
      filter     = "metric.type=\"${google_monitoring_metric_descriptor.jobclient_job_validation_failure_metric[0].type}\" AND resource.type=\"gce_instance\"${local.job_validation_filter}"
      duration   = "${var.alarm_duration_sec}s"
      comparison = "COMPARISON_GT"
      aggregations {
        alignment_period     = "${var.alarm_eval_period_sec}s"
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
        group_by_fields      = ["metadata.user_labels.\"workgroup\""]
      }
    }
  }
  notification_channels = [var.notification_channel_id]

  user_labels = {
    environment = var.environment,
  }
  alert_strategy {
    auto_close           = "604800s"
    notification_prompts = ["OPENED"]
  }

  lifecycle {
    replace_triggered_by = [google_monitoring_metric_descriptor.jobclient_job_validation_failure_metric]
  }
}

resource "google_monitoring_alert_policy" "worker_job_error_alert" {
  count        = var.enable_legacy_metrics ? 1 : 0
  display_name = "${var.environment} Worker Job Errors Alert"
  combiner     = "OR"
  conditions {
    display_name = "Worker Job Errors"
    condition_threshold {
      filter     = "metric.type=\"${google_monitoring_metric_descriptor.worker_job_error_metric[0].type}\" AND resource.type=\"gce_instance\""
      duration   = "${var.alarm_duration_sec}s"
      comparison = "COMPARISON_GT"
      aggregations {
        alignment_period     = "${var.alarm_eval_period_sec}s"
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
        group_by_fields      = ["metadata.user_labels.\"workgroup\""]
      }
    }
  }
  notification_channels = [var.notification_channel_id]

  user_labels = {
    environment = var.environment,
  }

  alert_strategy {
    auto_close           = "604800s"
    notification_prompts = ["OPENED"]
  }

  lifecycle {
    replace_triggered_by = [google_monitoring_metric_descriptor.worker_job_error_metric]
  }
}