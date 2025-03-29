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

resource "google_logging_metric" "collector_export_error_metric" {
  name        = "${var.environment}-collector-export-error-counter"
  filter      = "resource.type=\"gce_instance\" AND jsonPayload.message=~\".*Exporting failed. Dropping data.*\""
  description = "Error counter of exporting metric data from collector container"

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"

    labels {
      key         = "instanceId"
      value_type  = "STRING"
      description = "Id of the collector instance ."
    }
  }
  label_extractors = {
    "instanceId" = "EXTRACT(resource.labels.\"instance_id\")"
  }
}

resource "google_monitoring_alert_policy" "collector_export_error_alert" {
  count        = var.collector_export_error_alarm.enable_alarm ? 1 : 0
  display_name = "${var.environment} Collector Exporting Metric Data Error Alert"
  combiner     = "OR"
  conditions {
    display_name = "Collector Exporting Errors"
    condition_threshold {
      filter          = "metric.type=\"${google_logging_metric.collector_export_error_metric.name}\" AND resource.type=\"gce_instance\""
      duration        = "${var.collector_export_error_alarm.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.collector_export_error_alarm.threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.collector_export_error_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }

  lifecycle {
    replace_triggered_by = [
      google_logging_metric.collector_export_error_metric
    ]
  }
  user_labels = {
    environment = var.environment
    severity    = var.collector_export_error_alarm.severity
  }
  alert_strategy {
    auto_close = var.collector_export_error_alarm.auto_close_sec
  }
}

resource "google_logging_metric" "collector_run_error_metric" {
  name        = "${var.environment}-collector-run-error-counter"
  filter      = "resource.type=\"gce_instance\" AND jsonPayload.message=~\".*collector server run finished with error.*\""
  description = "Error counter of failed to run collector"

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "instanceId"
      value_type  = "STRING"
      description = "Id of the collector instance ."
    }
  }
  label_extractors = {
    "instanceId" = "EXTRACT(resource.labels.\"instance_id\")"
  }
}

resource "google_monitoring_alert_policy" "collector_run_error_alert" {
  count        = var.collector_run_error_alarm.enable_alarm ? 1 : 0
  display_name = "${var.environment} Collector Run Error Alert"
  combiner     = "OR"
  conditions {
    display_name = "Collector Running Errors"
    condition_threshold {
      filter          = "metric.type=\"${google_logging_metric.collector_run_error_metric.name}\" AND resource.type=\"gce_instance\""
      duration        = "${var.collector_run_error_alarm.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.collector_run_error_alarm.threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.collector_run_error_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }

  lifecycle {
    replace_triggered_by = [
      google_logging_metric.collector_run_error_metric
    ]
  }
  user_labels = {
    environment = var.environment
    severity    = var.collector_run_error_alarm.severity
  }
  alert_strategy {
    auto_close = var.collector_run_error_alarm.auto_close_sec
  }
}

resource "google_logging_metric" "collector_crash_error_metric" {
  name        = "${var.environment}-collector-crash-error-counter"
  filter      = "resource.type=\"gce_instance\" AND jsonPayload.MESSAGE=~\".*otelcol-contrib.service: Failed with result 'exit-code'.*\""
  description = "Error counter of collector server crash"

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "instanceId"
      value_type  = "STRING"
      description = "Id of the collector instance ."
    }
  }
  label_extractors = {
    "instanceId" = "EXTRACT(resource.labels.\"instance_id\")"
  }
}

resource "google_monitoring_alert_policy" "collector_crash_error_alert" {
  count        = var.collector_crash_error_alarm.enable_alarm ? 1 : 0
  display_name = "${var.environment} Collector Crash Error Alert"
  combiner     = "OR"
  conditions {
    display_name = "Collector Crash Errors"
    condition_threshold {
      filter          = "metric.type=\"${google_logging_metric.collector_crash_error_metric.name}\" AND resource.type=\"gce_instance\""
      duration        = "${var.collector_crash_error_alarm.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.collector_crash_error_alarm.threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.collector_crash_error_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }

  lifecycle {
    replace_triggered_by = [
      google_logging_metric.collector_crash_error_metric
    ]
  }
  user_labels = {
    environment = var.environment
    severity    = var.collector_crash_error_alarm.severity
  }
  alert_strategy {
    auto_close = var.collector_crash_error_alarm.auto_close_sec
  }
}
