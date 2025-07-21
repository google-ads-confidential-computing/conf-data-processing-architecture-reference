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
  count = var.error_5xx_alarm_config.enable_alarm ? 1 : 0

  display_name = "${var.service_prefix} Load Balancer 5xx Errors"
  combiner     = "OR"
  conditions {
    display_name = "5xx Errors"
    condition_threshold {
      filter          = "metric.type=\"loadbalancing.googleapis.com/https/request_count\" AND resource.type=\"https_lb_rule\" AND resource.label.\"url_map_name\"=\"${var.lb_url_map_name}\" AND metric.label.response_code_class=500"
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
  count = var.non_5xx_error_alarm_config.enable_alarm ? 1 : 0

  display_name = "${var.service_prefix} Load Balancer Non-5xx Errors"
  combiner     = "OR"
  conditions {
    display_name = "5xx Errors"
    condition_threshold {
      filter          = "metric.type=\"loadbalancing.googleapis.com/https/request_count\" AND resource.type=\"https_lb_rule\" AND resource.label.\"url_map_name\"=\"${var.lb_url_map_name}\" AND metric.label.response_code_class!=500 AND metric.label.response_code_class!=200"
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
  count = var.request_latencies_alarm_config.enable_alarm ? 1 : 0

  display_name = "${var.service_prefix} Load Balancer Request Latencies"
  combiner     = "OR"
  conditions {
    display_name = "Request Latencies"
    condition_threshold {
      filter          = "metric.type=\"loadbalancing.googleapis.com/https/total_latencies\" AND resource.type=\"https_lb_rule\" AND resource.label.\"url_map_name\"=\"${var.lb_url_map_name}\""
      duration        = "${var.request_latencies_alarm_config.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.request_latencies_alarm_config.threshold_ms
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.request_latencies_alarm_config.eval_period_sec}s"
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
