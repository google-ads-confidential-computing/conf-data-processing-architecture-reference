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

resource "google_monitoring_dashboard" "worker_custom_metrics_dashboard" {
  count = var.enable_legacy_metrics ? 1 : 0
  dashboard_json = jsonencode(
    {
      "displayName" : "${local.env_workgroup_name} Worker Custom Metrics Dashboard",
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
                        "perSeriesAligner" : "ALIGN_SUM",
                        "crossSeriesReducer" : "REDUCE_SUM",
                      },
                      "filter" : "metric.type=\"${var.legacy_jobclient_error_metric_type}\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${var.vm_instance_group_name}\"",
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
                        "perSeriesAligner" : "ALIGN_SUM",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "metric.label.\"Validator\""
                        ]
                      },
                      "filter" : "metric.type=\"${var.legacy_jobclient_job_validation_failure_metric_type}\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${var.vm_instance_group_name}\"",
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
                      "filter" : "metric.type=\"${var.legacy_worker_error_metric_type}\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${var.vm_instance_group_name}\"",
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


moved {
  from = google_monitoring_metric_descriptor.jobclient_job_validation_failure_metric
  to   = google_monitoring_metric_descriptor.jobclient_job_validation_failure_metric[0]
}

moved {
  from = google_monitoring_alert_policy.jobclient_job_validation_failure_alert
  to   = google_monitoring_alert_policy.jobclient_job_validation_failure_alert[0]
}

moved {
  from = google_monitoring_alert_policy.jobclient_job_client_error_metric
  to   = google_monitoring_alert_policy.jobclient_job_client_error_metric[0]
}

moved {
  from = google_monitoring_alert_policy.worker_job_error_metric
  to   = google_monitoring_alert_policy.worker_job_error_metric[0]
}

moved {
  from = google_monitoring_alert_policy.worker_job_error_alert
  to   = google_monitoring_alert_policy.worker_job_error_alert[0]
}

moved {
  from = google_monitoring_alert_policy.worker_custom_metrics_dashboard
  to   = google_monitoring_alert_policy.worker_custom_metrics_dashboard[0]
}
