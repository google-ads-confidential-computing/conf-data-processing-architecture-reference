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
  load_balancer_name = google_compute_url_map.encryption_key_service_loadbalancer.name
}

module "encryptionkeyservice_loadbalancer_alarms" {
  source = "../shared/loadbalancer_alarms"
  count  = var.alarms_enabled ? 1 : 0

  environment        = var.environment
  load_balancer_name = local.load_balancer_name
  service_prefix     = "${var.environment} Encryption Key Service"

  eval_period_sec            = var.alarm_eval_period_sec
  error_5xx_threshold        = var.lb_5xx_threshold
  max_latency_ms             = var.lb_max_latency_ms
  duration_sec               = var.alarm_duration_sec
  load_balancer_severity_map = var.encryption_key_service_severity_map
}

moved {
  from = module.encryptionkeyservice_cloudfunction_alarms[0]
  to   = module.encryptionkeyservice_cloudfunction_alarms["0"]
}

module "encryptionkeyservice_cloudfunction_alarms" {
  source   = "../shared/cloudfunction_alarms"
  for_each = var.alarms_enabled ? local.cfs : {}

  environment    = var.environment
  function_name  = each.value.name
  service_prefix = "${var.environment}-${each.value.location} Encryption Key Service"

  eval_period_sec                 = var.alarm_eval_period_sec
  error_5xx_ratio_threshold       = var.cloudfunction_5xx_ratio_threshold
  execution_time_max              = var.cloudfunction_max_execution_time_max
  execution_error_ratio_threshold = var.cloudfunction_error_ratio_threshold
  alert_on_memory_usage_threshold = var.cloudfunction_alert_on_memory_usage_threshold
  duration_sec                    = var.alarm_duration_sec
  cloud_function_severity_map     = var.encryption_key_service_severity_map
}

resource "google_logging_metric" "get_encrypted_private_key_general_error" {
  filter = "(resource.type=\"cloud_function\" AND resource.labels.function_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) OR (resource.type=\"cloud_run_revision\" AND resource.labels.service_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) AND textPayload=~\"metricName\" AND textPayload=~\"get_encrypted_private_key/error\""
  name   = "${var.environment}/get_encrypted_private_key/error"
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

resource "google_logging_metric" "get_encrypted_private_key_activation_age_in_days" {
  filter = "(resource.type=\"cloud_function\" AND resource.labels.function_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) OR (resource.type=\"cloud_run_revision\" AND resource.labels.service_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) AND textPayload=~\"metricName\" AND textPayload=~\"get_encrypted_private_key/age_in_days\""
  name   = "${var.environment}/get_encrypted_private_key/age_in_days"
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "DISTRIBUTION"
    labels {
      key         = "setName"
      value_type  = "STRING"
      description = "SetName associated with key"
    }
  }

  bucket_options {
    # Linear sequence of 100 buckets that all have the same width of 1 week.
    linear_buckets {
      num_finite_buckets = 100
      width              = 7
      offset             = 0
    }
  }

  // {"metricName":"get_encrypted_private_key/age_in_days","setName":"ga-partner","days":0}
  value_extractor = "REGEXP_EXTRACT(textPayload, \"days\\\":(\\\\d+)\")"
  label_extractors = {
    "setName" = "REGEXP_EXTRACT(textPayload, \"setName\\\":\\\"([^\\\"]+)\")"
  }
}

resource "google_logging_metric" "list_recent_encrypted_keys_age_in_days" {
  filter = "(resource.type=\"cloud_function\" AND resource.labels.function_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) OR (resource.type=\"cloud_run_revision\" AND resource.labels.service_name=(\"${local.cloud_function_a_name}\" OR \"${local.cloud_function_b_name}\")) AND textPayload=~\"metricName\" AND textPayload=~\"list_recent_encrypted_keys/age_in_days\""
  name   = "${var.environment}/list_recent_encrypted_keys/age_in_days"
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "DISTRIBUTION"
    labels {
      key         = "setName"
      value_type  = "STRING"
      description = "SetName associated with keys"
    }
  }

  bucket_options {
    # Linear sequence of 100 buckets that all have the same width of 1 week.
    linear_buckets {
      num_finite_buckets = 100
      width              = 7
      offset             = 0
    }
  }

  // {"metricName":"list_recent_encrypted_keys/age_in_days","setName":"ga-partner","days":0}
  value_extractor = "REGEXP_EXTRACT(textPayload, \"days\\\":(\\\\d+)\")"
  label_extractors = {
    "setName" = "REGEXP_EXTRACT(textPayload, \"setName\\\":\\\"([^\\\"]+)\")"
  }
}

resource "google_monitoring_alert_policy" "get_encrypted_private_key_general_error_alert" {
  count        = var.alarms_enabled ? 1 : 0
  display_name = "${var.environment} Encryption Key Service Get Encrypted Private Key General Error"
  combiner     = "OR"
  conditions {
    display_name = "Get Encrypted Private Key General Error"
    condition_threshold {
      filter          = "resource.type=\"cloud_run_revision\" AND metric.type = \"logging.googleapis.com/user/${google_logging_metric.get_encrypted_private_key_general_error.name}\""
      duration        = "${var.alarm_duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.get_encrypted_private_key_general_error_threshold
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
    severity    = lookup(var.encryption_key_service_severity_map, "encryption_key_service_get_encrypted_private_key_general_error_alert", "urgent")
  }

  alert_strategy {
    # 30 minutes.
    auto_close = "1800s"
  }
}

moved {
  from = module.encryptionkeyservice_monitoring_dashboard
  to   = module.encryptionkeyservice_monitoring_dashboard["0"]
}

module "encryptionkeyservice_monitoring_dashboard" {
  for_each      = local.cfs
  source        = "../shared/cloudfunction_dashboards"
  environment   = var.environment
  service_name  = "Encryption Key"
  function_name = "${each.value.location}-${each.value.name}"
}
