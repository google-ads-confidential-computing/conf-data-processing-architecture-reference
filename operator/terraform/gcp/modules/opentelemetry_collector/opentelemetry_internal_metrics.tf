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
  collector_instance_group_name   = google_compute_region_instance_group_manager.collector_instance.name
  collecotr_instance_group_filter = "resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\""
}

resource "google_monitoring_alert_policy" "collector_queue_size_alert_by_ratio" {
  count        = var.collector_queue_size_ratio_alarm.enable_alarm ? 1 : 0
  display_name = "${var.environment} Collector Queue Size Alert"
  combiner     = "OR"

  conditions {
    display_name = "Collector Queue Size Ratio"

    condition_threshold {
      duration   = "${var.collector_queue_size_ratio_alarm.duration_sec}s"
      comparison = "COMPARISON_GT"

      threshold_value = var.collector_queue_size_ratio_alarm.threshold

      filter             = "metric.type=\"custom.googleapis.com/otelcol_exporter_queue_size\" AND ${local.collecotr_instance_group_filter}"
      denominator_filter = "metric.type=\"custom.googleapis.com/otelcol_exporter_queue_capacity\" AND ${local.collecotr_instance_group_filter}"

      aggregations {
        alignment_period   = "${var.collector_queue_size_ratio_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_MAX"
      }
      denominator_aggregations {
        alignment_period   = "${var.collector_queue_size_ratio_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_MAX"
      }
    }
  }

  alert_strategy {
    auto_close           = "${var.collector_queue_size_ratio_alarm.auto_close_sec}s"
    notification_prompts = ["OPENED"]
  }

  user_labels = {
    environment = var.environment
    severity    = var.collector_queue_size_ratio_alarm.severity
  }
}

resource "google_monitoring_alert_policy" "collector_send_metric_points_failure_alert_by_ratio" {
  count        = var.collector_send_metric_points_ratio_alarm.enable_alarm ? 1 : 0
  display_name = "${var.environment} Collector Send Metric Points Ratio Alert"
  combiner     = "OR"

  conditions {
    display_name = "Collector Send Metric Points Ratio"

    condition_threshold {
      duration   = "${var.collector_send_metric_points_ratio_alarm.duration_sec}s"
      comparison = "COMPARISON_GT"

      threshold_value = var.collector_send_metric_points_ratio_alarm.threshold

      filter             = "metric.type=\"custom.googleapis.com/otelcol_exporter_send_failed_metric_points\" AND ${local.collecotr_instance_group_filter}"
      denominator_filter = "metric.type=\"custom.googleapis.com/otelcol_exporter_sent_metric_points\" AND ${local.collecotr_instance_group_filter}"

      aggregations {
        alignment_period   = "${var.collector_send_metric_points_ratio_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_RATE"
      }
      denominator_aggregations {
        alignment_period   = "${var.collector_send_metric_points_ratio_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  alert_strategy {
    auto_close           = "${var.collector_send_metric_points_ratio_alarm.auto_close_sec}s"
    notification_prompts = ["OPENED"]
  }

  user_labels = {
    environment = var.environment
    severity    = var.collector_send_metric_points_ratio_alarm.severity
  }
}

resource "google_monitoring_alert_policy" "collector_refuse_metric_points_alert_by_ratio" {
  count        = var.collector_refuse_metric_points_ratio_alarm.enable_alarm ? 1 : 0
  display_name = "${var.environment} Collector Refuse Metric Points Ratio from Worker Alert"
  combiner     = "OR"

  conditions {
    display_name = "Collector Accepted/Refused Metric Points Ratio"

    condition_threshold {
      duration   = "${var.collector_refuse_metric_points_ratio_alarm.duration_sec}s"
      comparison = "COMPARISON_GT"

      threshold_value = var.collector_refuse_metric_points_ratio_alarm.threshold

      filter             = "metric.type=\"custom.googleapis.com/otelcol_receiver_refused_metric_points\" AND ${local.collecotr_instance_group_filter}"
      denominator_filter = "metric.type=\"custom.googleapis.com/otelcol_receiver_accepted_metric_points\" AND ${local.collecotr_instance_group_filter}"

      aggregations {
        alignment_period   = "${var.collector_refuse_metric_points_ratio_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_RATE"
      }
      denominator_aggregations {
        alignment_period   = "${var.collector_refuse_metric_points_ratio_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  alert_strategy {
    auto_close           = "${var.collector_refuse_metric_points_ratio_alarm.auto_close_sec}s"
    notification_prompts = ["OPENED"]
  }

  user_labels = {
    environment = var.environment
    severity    = var.collector_refuse_metric_points_ratio_alarm.severity
  }
}

resource "google_monitoring_alert_policy" "collector_exceed_memory_usage_alarm" {
  count        = var.collector_exceed_memory_usage_alarm.enable_alarm ? 1 : 0
  display_name = "${var.environment} Collector Memory Usage Too High"
  combiner     = "OR"
  conditions {
    display_name = "Memory Usage Too High"
    condition_threshold {
      filter          = "metric.type=\"custom.googleapis.com/otelcol_process_memory_rss\" AND ${local.collecotr_instance_group_filter}"
      duration        = "${var.collector_exceed_memory_usage_alarm.duration_sec}s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.collector_exceed_memory_usage_alarm.threshold
      trigger {
        count = 1
      }
      aggregations {
        alignment_period   = "${var.collector_exceed_memory_usage_alarm.alignment_period_sec}s"
        per_series_aligner = "ALIGN_MAX"
      }
    }
  }

  user_labels = {
    environment = var.environment
    severity    = var.collector_exceed_memory_usage_alarm.severity
  }

  alert_strategy {
    auto_close           = "${var.collector_exceed_memory_usage_alarm.auto_close_sec}s"
    notification_prompts = ["OPENED"]
  }
}

resource "google_monitoring_dashboard" "opentelemetry_collector_internal_metrics_dashboard" {
  dashboard_json = jsonencode(
    {
      "displayName" : "${var.environment} OpenTelemetry Collector Internal Metrics Dashboard",
      "gridLayout" : {
        "columns" : "2",
        "widgets" : [
          {
            "title" : "Collector Queue size and capacity",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Queue Size",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_MEAN",
                      },
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_exporter_queue_size\" AND ${local.collecotr_instance_group_filter}",
                    }
                  }
                },
                {
                  "legendTemplate" : "Queue Capacity",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_MEAN",
                      },
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_exporter_queue_capacity\" AND ${local.collecotr_instance_group_filter}",
                    }
                  }
                },
              ],
              "timeshiftDuration" : "0s",
              "y2Axis" : {
                "label" : "y2Axis",
                "scale" : "LINEAR"
              }
            }
          },
          {
            "title" : "Collector Send Success/Failure Metric Points",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Send Success Metric Points",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_RATE",
                        "crossSeriesReducer" : "REDUCE_SUM",
                      },
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_exporter_sent_metric_points\" AND ${local.collecotr_instance_group_filter}",
                    }
                  }
                },
                {
                  "legendTemplate" : "Send Failure Metric Points",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_RATE",
                        "crossSeriesReducer" : "REDUCE_SUM",
                      },
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_exporter_send_failed_metric_points\" AND ${local.collecotr_instance_group_filter}",
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
            "title" : "Collector Receiver Accepted/Refused Metric Points",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Accepted Metric Points",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_RATE",
                        "crossSeriesReducer" : "REDUCE_SUM",
                      },
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_receiver_accepted_metric_points\" AND ${local.collecotr_instance_group_filter}",
                    }
                  }
                },
                {
                  "legendTemplate" : "Refused Metric Points",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_RATE",
                        "crossSeriesReducer" : "REDUCE_SUM",
                      },
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_receiver_refused_metric_points\" AND ${local.collecotr_instance_group_filter}",
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
            "title" : "Collector Memory Usage",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Total physical memory (resident set size) in bytes",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_MEAN",
                      },
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_process_memory_rss\" AND ${local.collecotr_instance_group_filter}",
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
          }
        ]
      }
    }
  )
}
