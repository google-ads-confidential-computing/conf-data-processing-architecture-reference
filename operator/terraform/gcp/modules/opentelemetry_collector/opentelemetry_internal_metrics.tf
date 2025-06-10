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
  collector_instance_group_name = google_compute_region_instance_group_manager.collector_instance.name
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
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_exporter_queue_size\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\"",
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
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_exporter_queue_capacity\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\"",
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
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_exporter_sent_metric_points\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\"",
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
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_exporter_send_failed_metric_points\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\"",
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
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_receiver_accepted_metric_points\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\"",
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
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_receiver_refused_metric_points\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\"",
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
            "title" : "Collector Processor Incoming/Outgoing Items",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Incoming Items",
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
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_processor_incoming_items\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\"",
                    }
                  }
                },
                {
                  "legendTemplate" : "Outgoing Items",
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
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_processor_outgoing_items\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\"",
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
                      "filter" : "metric.type=\"custom.googleapis.com/otelcol_process_memory_rss\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${local.collector_instance_group_name}\"",
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
