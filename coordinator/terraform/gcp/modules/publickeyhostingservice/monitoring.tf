# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

locals {
  cloud_functions       = [for cf in google_cloudfunctions2_function.get_public_key_cloudfunction : cf]
  cloud_function_a_name = local.cloud_functions[0].name
  cloud_function_b_name = local.cloud_functions[1].name
}

module "cloud_function_alarms" {
  source   = "../shared/cloudfunction_alarms"
  for_each = var.alarms_enabled ? google_cloudfunctions2_function.get_public_key_cloudfunction : {}

  environment    = var.environment
  function_name  = each.value.name
  service_prefix = "${var.environment}-${each.value.location} Public Key Service"

  eval_period_sec                 = var.alarm_eval_period_sec
  duration_sec                    = var.alarm_duration_sec
  error_5xx_threshold             = var.get_public_key_cloudfunction_5xx_threshold
  execution_time_max              = var.get_public_key_cloudfunction_max_execution_time_max
  execution_error_ratio_threshold = var.get_public_key_cloudfunction_error_ratio_threshold
  alert_on_memory_usage_threshold = var.cloudfunction_alert_on_memory_usage_threshold
  cloud_function_severity_map     = var.public_key_alerts_severity_overrides
}

resource "google_logging_metric" "get_public_key_empty_key_set_error" {
  filter = "(resource.type=\"cloud_function\" AND resource.labels.function_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) OR (resource.type=\"cloud_run_revision\" AND resource.labels.service_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) AND textPayload=~\"metricName\" AND textPayload=~\"get_active_public_keys/empty_key_set\""
  name   = "${var.environment}/get_active_public_keys/empty_key_set"
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "setName"
      value_type  = "STRING"
      description = "Name of the key set"
    }
  }
  label_extractors = {
    "setName" = "REGEXP_EXTRACT(textPayload, \"setName\\\":([^,}]+)\")"
  }
}

resource "google_logging_metric" "get_public_key_general_error" {
  filter = "(resource.type=\"cloud_function\" AND resource.labels.function_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) OR (resource.type=\"cloud_run_revision\" AND resource.labels.service_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) AND textPayload=~\"metricName\" AND textPayload=~\"get_active_public_keys/error\""
  name   = "${var.environment}/get_active_public_keys/error"
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "errorReason"
      value_type  = "STRING"
      description = "Error reason"
    }
  }
  label_extractors = {
    "errorReason" = "REGEXP_EXTRACT(textPayload, \"errorReason\\\":([^,}]+)\")"
  }
}

resource "google_monitoring_alert_policy" "get_public_key_empty_key_set_error_alert" {
  count        = var.alarms_enabled ? 1 : 0
  display_name = "${var.environment} Public Key Service Get Public Key Empty Key Set Error"
  combiner     = "OR"
  conditions {
    display_name = "Get Public Key Empty Key Set Error"
    condition_threshold {
      filter          = "resource.type=\"cloud_run_revision\" AND metric.type = \"logging.googleapis.com/user/${google_logging_metric.get_public_key_empty_key_set_error.name}\""
      duration        = "${var.alarm_duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.get_public_key_empty_key_set_error_threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.alarm_eval_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }

  user_labels = {
    environment = var.environment
    severity    = lookup(var.public_key_alerts_severity_overrides, "public_key_service_get_public_key_empty_key_set_error_alert", "urgent")
  }

  alert_strategy {
    # 30 minutes.
    auto_close = "1800s"
  }
}

resource "google_monitoring_alert_policy" "get_public_key_general_error_alert" {
  count        = var.alarms_enabled ? 1 : 0
  display_name = "${var.environment} Public Key Service Get Public Key General Error"
  combiner     = "OR"
  conditions {
    display_name = "Get Public Key General Error"
    condition_threshold {
      filter          = "resource.type=\"cloud_run_revision\" AND metric.type = \"logging.googleapis.com/user/${google_logging_metric.get_public_key_general_error.name}\""
      duration        = "${var.alarm_duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.get_public_key_general_error_threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.alarm_eval_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }

  user_labels = {
    environment = var.environment
    severity    = lookup(var.public_key_alerts_severity_overrides, "public_key_service_get_public_key_general_error_alert", "urgent")
  }

  alert_strategy {
    # 30 minutes.
    auto_close = "1800s"
  }
}
