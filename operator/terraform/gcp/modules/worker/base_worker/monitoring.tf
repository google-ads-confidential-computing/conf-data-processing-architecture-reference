/**
 * Copyright 2024 Google LLC
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

resource "google_monitoring_dashboard" "worker_dashboard" {
  dashboard_json = jsonencode(
    {
      "displayName" : "${var.environment} Worker Dashboard",
      "gridLayout" : {
        "columns" : "2",
        "widgets" : [
          {
            "title" : "Job Queue Message Operations",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Messages Finished Processing",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"subscription_id\""
                        ],
                        "perSeriesAligner" : "ALIGN_SUM"
                      },
                      "filter" : "metric.type=\"pubsub.googleapis.com/subscription/pull_ack_request_count\" resource.type=\"pubsub_subscription\" resource.label.\"subscription_id\"=\"${var.job_queue_sub}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "Messages Created",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"topic_id\""
                        ],
                        "perSeriesAligner" : "ALIGN_SUM"
                      },
                      "filter" : "metric.type=\"pubsub.googleapis.com/topic/send_request_count\" resource.type=\"pubsub_topic\" resource.label.\"topic_id\"=\"${var.job_queue_topic}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "Messages Delivered",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"subscription_id\""
                        ],
                        "perSeriesAligner" : "ALIGN_SUM"
                      },
                      "filter" : "metric.type=\"pubsub.googleapis.com/subscription/sent_message_count\" resource.type=\"pubsub_subscription\" resource.label.\"subscription_id\"=\"${var.job_queue_sub}\"",
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
            "title" : "Job Queue Messages In-Flight",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Delivered Messages",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"subscription_id\""
                        ],
                        "perSeriesAligner" : "ALIGN_MAX"
                      },
                      "filter" : "metric.type=\"pubsub.googleapis.com/subscription/sent_message_count\" resource.type=\"pubsub_subscription\" resource.label.\"subscription_id\"=\"${var.job_queue_sub}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "Undelivered Messages",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"subscription_id\""
                        ],
                        "perSeriesAligner" : "ALIGN_MAX"
                      },
                      "filter" : "metric.type=\"pubsub.googleapis.com/subscription/num_undelivered_messages\" resource.type=\"pubsub_subscription\" resource.label.\"subscription_id\"=\"${var.job_queue_sub}\"",
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
            "title" : "Job Queue Oldest Message Age",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Oldest Message Age",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_MAX",
                        "groupByFields" : [
                          "resource.label.\"subscription_id\""
                        ],
                        "perSeriesAligner" : "ALIGN_MAX"
                      },
                      "filter" : "metric.type=\"pubsub.googleapis.com/subscription/oldest_unacked_message_age\" resource.type=\"pubsub_subscription\" resource.label.\"subscription_id\"=\"${var.job_queue_sub}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s"
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
            "title" : "Job Metadata DB Scanned and Returned Rows",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Returned Rows",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"instance_id\""
                        ],
                        "perSeriesAligner" : "ALIGN_SUM"
                      },
                      "filter" : "metric.type=\"spanner.googleapis.com/query_stat/total/returned_rows_count\" resource.type=\"spanner_instance\" resource.label.\"instance_id\"=\"${var.metadatadb_instance_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "Scanned Rows",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"instance_id\""
                        ],
                        "perSeriesAligner" : "ALIGN_SUM"
                      },
                      "filter" : "metric.type=\"spanner.googleapis.com/query_stat/total/scanned_rows_count\" resource.type=\"spanner_instance\" resource.label.\"instance_id\"=\"${var.metadatadb_instance_name}\"",
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
            "title" : "Job Metadata DB Read/Write API Request Latency",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "p99 \u0024{metric.labels.method}",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_PERCENTILE_99"
                      },
                      "filter" : "metric.type=\"spanner.googleapis.com/api/request_latencies_by_transaction_type\" resource.type=\"spanner_instance\" resource.label.\"instance_id\"=\"${var.metadatadb_instance_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_MEAN",
                        "groupByFields" : [
                          "metric.label.\"method\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "p95 \u0024{metric.labels.method}",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_PERCENTILE_95"
                      },
                      "filter" : "metric.type=\"spanner.googleapis.com/api/request_latencies_by_transaction_type\" resource.type=\"spanner_instance\" resource.label.\"instance_id\"=\"${var.metadatadb_instance_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_MEAN",
                        "groupByFields" : [
                          "metric.label.\"method\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "p50 \u0024{metric.labels.method}",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_PERCENTILE_50"
                      },
                      "filter" : "metric.type=\"spanner.googleapis.com/api/request_latencies_by_transaction_type\" resource.type=\"spanner_instance\" resource.label.\"instance_id\"=\"${var.metadatadb_instance_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_MEAN",
                        "groupByFields" : [
                          "metric.label.\"method\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
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
            "title" : "Job Metadata DB API Request Error Rate",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "API Request Errors",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"instance_id\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
                      },
                      "filter" : "metric.type=\"spanner.googleapis.com/api/request_count\" resource.type=\"spanner_instance\" resource.label.\"instance_id\"=\"${var.metadatadb_instance_name}\" metric.label.\"status\"!=\"OK\"",
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
            "title" : "Job Metadata DB API Request Error Percentage",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "API Request Errors",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilterRatio" : {
                      "denominator" : {
                        "aggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_SUM",
                          "groupByFields" : [
                            "resource.label.\"instance_id\""
                          ],
                          "perSeriesAligner" : "ALIGN_SUM"
                        },
                        "filter" : "metric.type=\"spanner.googleapis.com/api/api_request_count\" resource.type=\"spanner_instance\" resource.label.\"instance_id\"=\"${var.metadatadb_instance_name}\""
                      },
                      "numerator" : {
                        "aggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_SUM",
                          "groupByFields" : [
                            "resource.label.\"instance_id\""
                          ],
                          "perSeriesAligner" : "ALIGN_SUM"
                        },
                        "filter" : "metric.type=\"spanner.googleapis.com/api/api_request_count\" resource.type=\"spanner_instance\" resource.label.\"instance_id\"=\"${var.metadatadb_instance_name}\" metric.label.\"status\"!=\"OK\""
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
            "title" : "Worker VM CPU Utilization",
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
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "metric.label.\"instance_name\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
                      },
                      "filter" : "metric.type=\"compute.googleapis.com/instance/cpu/utilization\" resource.type=\"gce_instance\" metadata.system_labels.\"instance_group\"=\"${var.vm_instance_group_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s"
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
            "title" : "Worker VM Memory Usage",
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
                      "filter" : "metric.type=\"compute.googleapis.com/guest/memory/bytes_used\" resource.type=\"gce_instance\" metric.label.\"state\"=\"used\" metadata.user_labels.\"environment\"=\"${var.environment}\""
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
            "title" : "Autoscaling Cloud Function Executions",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Executions",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_RATE"
                      },
                      "filter" : "metric.type=\"run.googleapis.com/request_count\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${var.autoscaler_cloudfunction_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"service_name\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
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
            "title" : "Autoscaling Cloud Function Execution Times",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "p99",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_PERCENTILE_99"
                      },
                      "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${var.autoscaler_cloudfunction_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_MEAN",
                        "groupByFields" : [
                          "resource.label.\"function_name\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "p95",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_PERCENTILE_95"
                      },
                      "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${var.autoscaler_cloudfunction_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_MEAN",
                        "groupByFields" : [
                          "resource.label.\"function_name\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "p50",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_PERCENTILE_50"
                      },
                      "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${var.autoscaler_cloudfunction_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_MEAN",
                        "groupByFields" : [
                          "resource.label.\"function_name\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
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
            "title" : "Autoscaling Cloud Function Errors",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "\u0024{metric.labels.response_code_class}",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "perSeriesAligner" : "ALIGN_RATE"
                      },
                      "filter" : "metric.type=\"run.googleapis.com/request_count\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${var.autoscaler_cloudfunction_name}\" metric.label.\"response_code_class\"!=\"2xx\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "metric.label.\"response_code_class\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
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
            "title" : "Autoscaling Cloud Function Max Concurrent Requests",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Max Concurrent Requests",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_MEAN",
                        "groupByFields" : [
                          "resource.label.\"service_name\""
                        ],
                        "perSeriesAligner" : "ALIGN_SUM"
                      },
                      "filter" : "metric.type=\"run.googleapis.com/container/max_request_concurrencies\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${var.autoscaler_cloudfunction_name}\" metric.label.\"state\"=\"active\"",
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
            "title" : "Autoscaler Utilization and Capacity",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Capacity",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"autoscaler_name\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
                      },
                      "filter" : "metric.type=\"autoscaler.googleapis.com/capacity\" resource.type=\"autoscaler\" resource.label.\"autoscaler_name\"=\"${var.autoscaler_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s",
                      }
                    }
                  }
                },
                {
                  "legendTemplate" : "Utilization",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"autoscaler_name\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
                      },
                      "filter" : "metric.type=\"autoscaler.googleapis.com/current_utilization\" resource.type=\"autoscaler\" resource.label.\"autoscaler_name\"=\"${var.autoscaler_name}\"",
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
            "title" : "Autoscaler Instance Group Size",
            "xyChart" : {
              "chartOptions" : {
                "mode" : "COLOR"
              },
              "dataSets" : [
                {
                  "legendTemplate" : "Size of Instance Group",
                  "minAlignmentPeriod" : "60s",
                  "plotType" : "LINE",
                  "targetAxis" : "Y2",
                  "timeSeriesQuery" : {
                    "timeSeriesFilter" : {
                      "aggregation" : {
                        "alignmentPeriod" : "60s",
                        "crossSeriesReducer" : "REDUCE_SUM",
                        "groupByFields" : [
                          "resource.label.\"instance_group_name\""
                        ],
                        "perSeriesAligner" : "ALIGN_MEAN"
                      },
                      "filter" : "metric.type=\"compute.googleapis.com/instance_group/size\" resource.type=\"instance_group\" resource.label.\"instance_group_name\"=\"${var.vm_instance_group_name}\"",
                      "secondaryAggregation" : {
                        "alignmentPeriod" : "60s"
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
