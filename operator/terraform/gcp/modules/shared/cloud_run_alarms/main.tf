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

resource "google_monitoring_alert_policy" "error_5xx_count" {
  for_each = var.error_5xx_alarm_config.enable_alarm ? var.cloud_run_service_names : []

  display_name = "${var.service_prefix} ${each.key} Cloud Run 5xx Errors"
  combiner     = "OR"
  conditions {
    display_name = "5xx Errors"
    condition_threshold {
      filter          = "metric.type=\"run.googleapis.com/request_count\" AND resource.type=\"cloud_run_revision\" AND resource.label.service_name=\"${each.key}\" AND metric.label.response_code_class=\"5xx\""
      duration        = "${var.error_5xx_alarm_config.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.error_5xx_alarm_config.error_threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.error_5xx_alarm_config.eval_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }
  notification_channels = [var.notification_channel_id]

  user_labels = {
    environment = var.environment
  }
  alert_strategy {
    auto_close           = "604800s"
    notification_prompts = ["OPENED"]
  }
}

resource "google_monitoring_alert_policy" "non_5xx_error_count" {
  for_each = var.non_5xx_error_alarm_config.enable_alarm ? var.cloud_run_service_names : []

  display_name = "${var.service_prefix} ${each.key} Cloud Run Non-5xx Errors"
  combiner     = "OR"
  conditions {
    display_name = "Non-5xx Errors"
    condition_threshold {
      filter          = "metric.type=\"run.googleapis.com/request_count\" AND resource.type=\"cloud_run_revision\" AND resource.label.service_name=\"${each.key}\" AND metric.label.response_code_class!=\"5xx\" AND metric.label.response_code_class!=\"2xx\""
      duration        = "${var.non_5xx_error_alarm_config.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.non_5xx_error_alarm_config.error_threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.non_5xx_error_alarm_config.eval_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }
  notification_channels = [var.notification_channel_id]

  user_labels = {
    environment = var.environment
  }
  alert_strategy {
    auto_close           = "604800s"
    notification_prompts = ["OPENED"]
  }
}

resource "google_monitoring_alert_policy" "request_latencies" {
  for_each = var.execution_time_alarm_config.enable_alarm ? var.cloud_run_service_names : []

  display_name = "${var.service_prefix} ${each.key} Cloud Run Request Latencies"
  combiner     = "OR"
  conditions {
    display_name = "Request Latencies"
    condition_threshold {
      filter          = "metric.type=\"run.googleapis.com/request_latencies\" AND resource.type=\"cloud_run_revision\" AND resource.label.service_name=\"${each.key}\""
      duration        = "${var.execution_time_alarm_config.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.execution_time_alarm_config.threshold_ms
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.execution_time_alarm_config.eval_period_sec}s"
        per_series_aligner = "ALIGN_PERCENTILE_99"
      }
    }
  }
  notification_channels = [var.notification_channel_id]

  user_labels = {
    environment = var.environment
  }
  alert_strategy {
    auto_close           = "604800s"
    notification_prompts = ["OPENED"]
  }
}
