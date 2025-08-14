/**
 * Copyright 2022 Google LLC
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
  new_job_validation_filter = length(var.java_job_validations_to_alert) == 0 ? "" : format(" AND metric.label.\"Validator\"=monitoring.regex.full_match(\"%s\")", join("|", var.java_job_validations_to_alert))
  env_workgroup_name        = var.workgroup == null ? var.environment : "${var.environment} ${var.workgroup}"
}

resource "google_monitoring_alert_policy" "jobclient_job_validation_failure_counter_alert" {
  count        = length(var.java_job_validations_to_alert) > 0 && var.enable_new_metrics ? 1 : 0
  display_name = "${local.env_workgroup_name} Job Client Validation Failure Alert"
  combiner     = "OR"
  conditions {
    display_name = "Validation Failures"
    condition_threshold {
      // revert to legacy metric until metric kind resolved b/434222428
      filter     = "metric.type=\"${var.legacy_jobclient_job_validation_failure_metric_type}\" AND resource.type=\"gce_instance\"${local.new_job_validation_filter} ${local.workgroup_filter}"
      duration   = "${var.alarm_duration_sec}s"
      comparison = "COMPARISON_GT"
      aggregations {
        alignment_period   = "${var.alarm_eval_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }
  notification_channels = [var.notification_channel_id]

  user_labels = {
    environment = var.environment,
    workgroup   = var.workgroup
  }
  alert_strategy {
    auto_close           = "604800s"
    notification_prompts = ["OPENED"]
  }
}

resource "google_monitoring_alert_policy" "worker_job_error_new_alert" {
  count        = var.enable_new_metrics ? 1 : 0
  display_name = "${local.env_workgroup_name} Worker Job Errors Alert"
  combiner     = "OR"
  conditions {
    display_name = "Worker Job Errors"
    condition_threshold {
      filter     = "metric.type=\"${var.new_worker_error_metric_type}\" AND resource.type=\"gce_instance\" ${local.workgroup_filter}"
      duration   = "${var.alarm_duration_sec}s"
      comparison = "COMPARISON_GT"
      aggregations {
        alignment_period   = "${var.alarm_eval_period_sec}s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }
  notification_channels = [var.notification_channel_id]

  user_labels = {
    environment = var.environment,
    workgroup   = var.workgroup
  }
  alert_strategy {
    auto_close           = "604800s"
    notification_prompts = ["OPENED"]
  }
}

resource "google_monitoring_dashboard" "worker_custom_metrics_new_dashboard" {
  count = var.enable_new_metrics ? 1 : 0
  dashboard_json = jsonencode(
    {
      "displayName" : "${local.env_workgroup_name} New Worker Custom Metrics Dashboard",
      "gridLayout" : {
        "columns" : "2",
        "widgets" : [
          {
            "title" : "Worker and Job Client - Errors and Validation Failures",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Job Client Errors",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_RATE",
                      },
                      "filter" : "metric.type=\"${var.new_jobclient_error_metric_type}\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${var.vm_instance_group_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                },
                {
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_RATE",
                        "groupByFields" : [
                          "metric.label.\"Validator\""
                        ]
                      },
                      "filter" : "metric.type=\"${var.new_jobclient_job_validation_failure_metric_type}\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${var.vm_instance_group_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "Worker Job Errors",
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
                      "filter" : "metric.type=\"${var.new_worker_error_metric_type}\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${var.vm_instance_group_name}\"",
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
          }
        ]
      }
    }
  )
}
