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

resource "google_monitoring_alert_policy" "collector_exceed_cpu_usage_alarm" {
  count        = var.collector_exceed_cpu_usage_alarm.enable_alarm ? 1 : 0
  display_name = "${var.environment} Collector CPU Usage Too High"
  combiner     = "OR"
  conditions {
    display_name = "CPU Usage Too High"
    condition_threshold {
      filter          = "metric.type=\"compute.googleapis.com/instance/cpu/utilization\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${google_compute_region_instance_group_manager.collector_instance.name}\""
      duration        = "${var.collector_exceed_cpu_usage_alarm.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.collector_exceed_cpu_usage_alarm.threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period     = "${var.collector_exceed_cpu_usage_alarm.alignment_period_sec}s"
        per_series_aligner   = "ALIGN_MAX"
        cross_series_reducer = "REDUCE_MAX"
      }
    }
  }

  user_labels = {
    environment = var.environment
    severity    = var.collector_exceed_cpu_usage_alarm.severity
  }

  alert_strategy {
    auto_close           = "${var.collector_exceed_cpu_usage_alarm.auto_close_sec}s"
    notification_prompts = ["OPENED"]
  }
}

resource "google_logging_metric" "collector_export_error_metric" {
  name        = "${var.environment}-collector-export-error-counter"
  filter      = "resource.type=\"gce_instance\" AND jsonPayload.message=~\".*Exporting failed.*\""
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
      filter          = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.collector_export_error_metric.name}\" AND resource.type=\"gce_instance\""
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
    auto_close           = "${var.collector_export_error_alarm.auto_close_sec}s"
    notification_prompts = ["OPENED"]
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
      filter          = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.collector_run_error_metric.name}\" AND resource.type=\"gce_instance\""
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
    auto_close           = "${var.collector_run_error_alarm.auto_close_sec}s"
    notification_prompts = ["OPENED"]
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
      filter          = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.collector_crash_error_metric.name}\" AND resource.type=\"gce_instance\""
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
    auto_close           = "${var.collector_crash_error_alarm.auto_close_sec}s"
    notification_prompts = ["OPENED"]
  }
}

resource "google_logging_metric" "worker_export_metric_error_metric" {
  name        = "${var.environment}-worker-export-metric-error-counter"
  filter      = "resource.type=\"gce_instance\" AND (jsonPayload.MESSAGE=~\".*WARNING: Failed to export metrics.*\" OR jsonPayload.MESSAGE=~\".*Export() failed:*\")"
  description = "Error counter of exporting metric data from worker to collector container"

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"

    labels {
      key         = "instanceId"
      value_type  = "STRING"
      description = "Id of the worker instance ."
    }
  }
  label_extractors = {
    "instanceId" = "EXTRACT(resource.labels.\"instance_id\")"
  }
}

resource "google_monitoring_alert_policy" "worker_export_metric_error_alert" {
  count        = var.worker_exporting_metrics_error_alarm.enable_alarm ? 1 : 0
  display_name = "${var.environment} Worker Exporting Metrics to Collector Error Alert"
  combiner     = "OR"
  conditions {
    display_name = "Worker Exporting Metrics To Collector Errors"
    condition_threshold {
      filter          = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.worker_export_metric_error_metric.name}\" AND resource.type=\"gce_instance\""
      duration        = "${var.worker_exporting_metrics_error_alarm.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.worker_exporting_metrics_error_alarm.threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.worker_exporting_metrics_error_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }

  lifecycle {
    replace_triggered_by = [
      google_logging_metric.worker_export_metric_error_metric
    ]
  }
  user_labels = {
    environment = var.environment
    severity    = var.worker_exporting_metrics_error_alarm.severity
  }
  alert_strategy {
    auto_close           = "${var.worker_exporting_metrics_error_alarm.auto_close_sec}s"
    notification_prompts = ["OPENED"]
  }
}

resource "google_monitoring_dashboard" "opentelemetry_metrics_dashboard" {
  dashboard_json = jsonencode(
    {
      "displayName" : "${var.environment} OpenTelemetry Metrics Dashboard",
      "gridLayout" : {
        "columns" : "2",
        "widgets" : [
          {
            "title" : "Collector VM CPU Utilization",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y1",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_MEAN"
                      },
                      "filter" : "metric.type=\"compute.googleapis.com/instance/cpu/utilization\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${google_compute_region_instance_group_manager.collector_instance.name}\"",
                    }
                  }
                }
              ],
              "timeshiftDuration" : "0s",
              "yAxis" : {
                "label" : "y2Axis",
                "scale" : "LINEAR"
              }
            }
          },
          {
            "title" : "Collector Instances - Export Errors",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_SUM",
                        "crossSeriesReducer" : "REDUCE_SUM",
                      },
                      "filter" : "metric.type=\"logging.googleapis.com/user/${google_logging_metric.collector_export_error_metric.name}\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${google_compute_region_instance_group_manager.collector_instance.name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                }
              ],
              "timeshiftDuration" : "0s",
              "y2Axis" : {
                "label" : "y2Axis",
                "scale" : "LINEAR"
              }
            }
          },
          {
            "title" : "Collector Instances - Startup Errors",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_SUM",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "metric.label.\"Validator\""
                        ]
                      },
                      "filter" : "metric.type=\"logging.googleapis.com/user/${google_logging_metric.collector_run_error_metric.name}\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${google_compute_region_instance_group_manager.collector_instance.name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                }
              ],
              "timeshiftDuration" : "0s",
              "y2Axis" : {
                "label" : "y2Axis",
                "scale" : "LINEAR"
              }
            }
          },
          {
            "title" : "Collector Instances - Crash Errors",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_SUM",
                        "crossSeriesReducer" : "REDUCE_SUM",
                      },
                      "filter" : "metric.type=\"logging.googleapis.com/user/${google_logging_metric.collector_crash_error_metric.name}\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${google_compute_region_instance_group_manager.collector_instance.name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                }
              ],
              "timeshiftDuration" : "0s",
              "y2Axis" : {
                "label" : "y2Axis",
                "scale" : "LINEAR"
              }
            }
          },
          {
            "title" : "Worker Instances - Export Metrics Errors",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_SUM",
                        "crossSeriesReducer" : "REDUCE_SUM",
                      },
                      "filter" : "metric.type=\"logging.googleapis.com/user/${google_logging_metric.worker_export_metric_error_metric.name}\" resource.type=\"gce_instance\" metadata.user_labels.\"environment\"=\"${var.environment}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                }
              ],
              "timeshiftDuration" : "0s",
              "y2Axis" : {
                "label" : "y2Axis",
                "scale" : "LINEAR"
              }
            }
          },
        ]
      }
    }
  )
}
