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
  create_cloud_function_resources = var.create_frontend_service_cloud_function
  function_name                   = local.create_cloud_function_resources ? google_cloudfunctions2_function.frontend_service_cloudfunction[0].name : ""

  create_cloud_run_resources = length(module.cloud_run_fe) > 0
  lb_url_map_name            = (local.create_cloud_run_resources && length(module.cloud_run_fe_load_balancer) > 0) ? module.cloud_run_fe_load_balancer[0].url_map_name : ""
  create_lb_resources        = local.lb_url_map_name != ""


  // Template for a single dashboard data set for Cloud Run service
  // Substitution order:
  // 1. Template name
  // 2. Aggregation
  // 3. Filter
  // 4. Secondary aggregation
  single_data_set_template = <<EOF
{
  "legendTemplate" : "%s",
  "minAlignmentPeriod" : "60s",
  "plotType" : "LINE",
  "targetAxis" : "Y2",
  "timeSeriesQuery" : {
    "timeSeriesFilter" : {
      "aggregation" : %s,
      "filter" : "%s",
      "secondaryAggregation" : %s
    }
  }
}
EOF

  request_latency_data_sets = local.create_cloud_run_resources ? ([for p in setproduct([for cr in module.cloud_run_fe : cr], [
    {
      legend : "p99",
      metric : "run.googleapis.com/request_latencies"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_99"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"service_name\"",
          "resource.label.\"region\""
        ],
      }
    },
    {
      legend : "p95",
      metric : "run.googleapis.com/request_latencies"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_95"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"service_name\"",
          "resource.label.\"region\""
        ],
      }
    },
    {
      legend : "p50",
      metric : "run.googleapis.com/request_latencies"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_50"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"service_name\"",
          "resource.label.\"region\""
        ],
      }
    }
    ]) : format(
    local.single_data_set_template,
    "${p[1].legend} ${p[0].region}",
    jsonencode(p[1].aggregation),
    "metric.type=\\\"${p[1].metric}\\\" resource.type=\\\"cloud_run_revision\\\" resource.label.\\\"service_name\\\"=\\\"${p[0].service_name}\\\"",
    jsonencode(p[1].secondaryAggregation)
    )
  ]) : []

  request_latencies_widget = <<EOF
{
  "title" : "Cloud Run Request Latencies",
  "xyChart" : {
    "chartOptions" : {
      "mode" : "COLOR"
    },
    "dataSets" : [
      ${join(",", local.request_latency_data_sets)}
    ],
    "timeshiftDuration" : "0s",
    "y2Axis" : {
      "label" : "y2Axis",
      "scale" : "LINEAR"
    }
  }
}
EOF

  request_count_data_sets = local.create_cloud_run_resources ? ([for p in setproduct([for cr in module.cloud_run_fe : cr], [
    {
      legend : "Executions",
      metric : "run.googleapis.com/request_count"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_RATE"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_SUM",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"service_name\"",
          "resource.label.\"region\""
        ],
      }
    }
    ]) : format(
    local.single_data_set_template,
    "${p[1].legend} ${p[0].region}",
    jsonencode(p[1].aggregation),
    "metric.type=\\\"${p[1].metric}\\\" resource.type=\\\"cloud_run_revision\\\" resource.label.\\\"service_name\\\"=\\\"${p[0].service_name}\\\"",
    jsonencode(p[1].secondaryAggregation)
    )
  ]) : []

  request_count_widget = <<EOF
{
  "title" : "Cloud Run Executions",
  "xyChart" : {
    "chartOptions" : {
      "mode" : "COLOR"
    },
    "dataSets" : [
      ${join(",", local.request_count_data_sets)}
    ],
    "timeshiftDuration" : "0s",
    "y2Axis" : {
      "label" : "y2Axis",
      "scale" : "LINEAR"
    }
  }
}
EOF

  bad_response_code_data_sets = local.create_cloud_run_resources ? ([for p in setproduct([for cr in module.cloud_run_fe : cr], [
    {
      legend : "\u0024{metric.labels.response_code_class}",
      metric : "run.googleapis.com/request_count"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_RATE"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_SUM",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"service_name\"",
          "resource.label.\"region\"",
          "metric.label.\"response_code_class\""
        ],
      }
    }
    ]) : format(
    local.single_data_set_template,
    "${p[1].legend} ${p[0].region}",
    jsonencode(p[1].aggregation),
    "metric.type=\\\"${p[1].metric}\\\" resource.type=\\\"cloud_run_revision\\\" resource.label.\\\"service_name\\\"=\\\"${p[0].service_name}\\\" metric.label.\\\"response_code_class\\\"!=\\\"2xx\\\"",
    jsonencode(p[1].secondaryAggregation)
    )
  ]) : []

  request_errors_widget = <<EOF
{
  "title" : "Cloud Run Errors",
  "xyChart" : {
    "chartOptions" : {
      "mode" : "COLOR"
    },
    "dataSets" : [
      ${join(",", local.bad_response_code_data_sets)}
    ],
    "timeshiftDuration" : "0s",
    "y2Axis" : {
      "label" : "y2Axis",
      "scale" : "LINEAR"
    }
  }
}
EOF

  max_concurrent_request_data_sets = local.create_cloud_run_resources ? ([for p in setproduct([for cr in module.cloud_run_fe : cr], [
    {
      legend : "Max Concurrent Requests",
      metric : "run.googleapis.com/container/max_request_concurrencies"

      aggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        groupByFields : [
          "resource.label.\"service_name\""
        ],
        perSeriesAligner : "ALIGN_SUM"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s"
      }
    }
    ]) : format(
    local.single_data_set_template,
    "${p[1].legend} ${p[0].region}",
    jsonencode(p[1].aggregation),
    "metric.type=\\\"${p[1].metric}\\\" resource.type=\\\"cloud_run_revision\\\" resource.label.\\\"service_name\\\"=\\\"${p[0].service_name}\\\" metric.label.\\\"state\\\"=\\\"active\\\"",
    jsonencode(p[1].secondaryAggregation)
    )
  ]) : []

  max_concurrent_requests_widget = <<EOF
{
  "title" : "Cloud Run Max Concurrent Requests",
  "xyChart" : {
    "chartOptions" : {
      "mode" : "COLOR"
    },
    "dataSets" : [
      ${join(",", local.max_concurrent_request_data_sets)}
    ],
    "timeshiftDuration" : "0s",
    "y2Axis" : {
      "label" : "y2Axis",
      "scale" : "LINEAR"
    }
  }
}
EOF

  lb_latency_data_sets = local.create_cloud_run_resources ? ([for item in [
    {
      legend : "p50 Total Latencies",
      metric : "loadbalancing.googleapis.com/https/total_latencies"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_50"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    {
      legend : "p95 Total Latencies",
      metric : "loadbalancing.googleapis.com/https/total_latencies"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_95"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    {
      legend : "p99 Total Latencies",
      metric : "loadbalancing.googleapis.com/https/total_latencies"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_99"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    {
      legend : "p50 Backend Latencies",
      metric : "loadbalancing.googleapis.com/https/backend_latencies"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_50"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    {
      legend : "p95 Backend Latencies",
      metric : "loadbalancing.googleapis.com/https/backend_latencies"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_95"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    {
      legend : "p99 Backend Latencies",
      metric : "loadbalancing.googleapis.com/https/backend_latencies"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_99"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    {
      legend : "p50 Frontend TCP RTT",
      metric : "loadbalancing.googleapis.com/https/frontend_tcp_rtt"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_50"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    {
      legend : "p95 Frontend TCP RTT",
      metric : "loadbalancing.googleapis.com/https/frontend_tcp_rtt"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_95"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    {
      legend : "p99 Frontend TCP RTT",
      metric : "loadbalancing.googleapis.com/https/frontend_tcp_rtt"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_PERCENTILE_99"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_MEAN",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    ] : format(
    local.single_data_set_template,
    "${item.legend}",
    jsonencode(item.aggregation),
    "metric.type=\\\"${item.metric}\\\" resource.type=\\\"https_lb_rule\\\" resource.label.\\\"url_map_name\\\"=\\\"${local.lb_url_map_name}\\\"",
    jsonencode(item.secondaryAggregation)
  )]) : []

  lb_latency_widget = <<EOF
{
  "title" : "Load Balancer Latencies",
  "xyChart" : {
    "chartOptions" : {
      "mode" : "COLOR"
    },
    "dataSets" : [
      ${join(",", local.lb_latency_data_sets)}
    ],
    "timeshiftDuration" : "0s",
    "y2Axis" : {
      "label" : "y2Axis",
      "scale" : "LINEAR"
    }
  }
}
EOF

  lb_request_count_data_sets = local.create_cloud_run_resources ? ([for item in [
    {
      legend : "Request Count",
      metric : "loadbalancing.googleapis.com/https/request_count"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_RATE"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_SUM",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
        ],
      }
    },
    ] : format(
    local.single_data_set_template,
    "${item.legend}",
    jsonencode(item.aggregation),
    "metric.type=\\\"${item.metric}\\\" resource.type=\\\"https_lb_rule\\\" resource.label.\\\"url_map_name\\\"=\\\"${local.lb_url_map_name}\\\"",
    jsonencode(item.secondaryAggregation)
  )]) : []

  lb_request_count_widget = <<EOF
{
  "title" : "Load Balancer Request Count",
  "xyChart" : {
    "chartOptions" : {
      "mode" : "COLOR"
    },
    "dataSets" : [
      ${join(",", local.lb_request_count_data_sets)}
    ],
    "timeshiftDuration" : "0s",
    "y2Axis" : {
      "label" : "y2Axis",
      "scale" : "LINEAR"
    }
  }
}
EOF

  lb_errors_data_sets = local.create_cloud_run_resources ? ([for item in [
    {
      legend : "\u0024{metric.labels.response_code_class}",
      metric : "loadbalancing.googleapis.com/https/request_count"

      aggregation : {
        alignmentPeriod : "60s",
        perSeriesAligner : "ALIGN_RATE"
      },

      secondaryAggregation : {
        alignmentPeriod : "60s",
        crossSeriesReducer : "REDUCE_SUM",
        perSeriesAligner : "ALIGN_MEAN",
        groupByFields : [
          "resource.label.\"url_map_name\"",
          "metric.label.\"response_code_class\""
        ],
      }
    },
    ] : format(
    local.single_data_set_template,
    "${item.legend}",
    jsonencode(item.aggregation),
    "metric.type=\\\"${item.metric}\\\" resource.type=\\\"https_lb_rule\\\" resource.label.\\\"url_map_name\\\"=\\\"${local.lb_url_map_name}\\\" metric.label.\\\"response_code_class\\\"!=200",
    jsonencode(item.secondaryAggregation)
  )]) : []

  lb_errors_widget = <<EOF
{
  "title" : "Load Balancer Errors",
  "xyChart" : {
    "chartOptions" : {
      "mode" : "COLOR"
    },
    "dataSets" : [
      ${join(",", local.lb_errors_data_sets)}
    ],
    "timeshiftDuration" : "0s",
    "y2Axis" : {
      "label" : "y2Axis",
      "scale" : "LINEAR"
    }
  }
}
EOF
}

module "frontendservice_cloudfunction_alarms" {
  source = "../shared/cloudfunction_alarms"
  count  = var.alarms_enabled && local.create_cloud_function_resources ? 1 : 0

  environment             = var.environment
  notification_channel_id = var.notification_channel_id
  function_name           = local.function_name
  service_prefix          = "${var.environment} Frontend Service"

  eval_period_sec           = var.alarm_eval_period_sec
  error_5xx_threshold       = var.cloudfunction_5xx_threshold
  execution_time_max        = var.cloudfunction_max_execution_time_max
  execution_error_threshold = var.cloudfunction_error_threshold
  duration_sec              = var.alarm_duration_sec
}

module "frontendservice_cloud_run_alarms" {
  source = "../shared/cloud_run_alarms"
  count  = (var.alarms_enabled && local.create_cloud_run_resources) ? 1 : 0

  cloud_run_service_names = [for cr in module.cloud_run_fe : cr.service_name]

  environment             = var.environment
  notification_channel_id = var.notification_channel_id
  service_prefix          = "${var.environment} Frontend Service"

  error_5xx_alarm_config      = var.cloud_run_error_5xx_alarm_config
  non_5xx_error_alarm_config  = var.cloud_run_non_5xx_error_alarm_config
  execution_time_alarm_config = var.cloud_run_execution_time_alarm_config
}

module "frontendservice_load_balancer_alarms" {
  source = "../shared/cloud_run_lb_alarms"
  count  = (var.alarms_enabled && local.create_lb_resources) ? 1 : 0

  lb_url_map_name = local.lb_url_map_name

  environment             = var.environment
  notification_channel_id = var.notification_channel_id
  service_prefix          = "${var.environment} Frontend Service"

  error_5xx_alarm_config         = var.lb_error_5xx_alarm_config
  non_5xx_error_alarm_config     = var.lb_non_5xx_error_alarm_config
  request_latencies_alarm_config = var.lb_request_latencies_alarm_config
}

resource "google_monitoring_dashboard" "frontend_dashboard" {
  dashboard_json = jsonencode(
    {
      "displayName" : "${var.environment} Frontend Dashboard",
      "gridLayout" : {
        "columns" : "2",
        "widgets" : flatten([
          local.create_cloud_function_resources ? [{
            "title" : "Cloud Function Executions",
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
                      "filter" : "metric.type=\"run.googleapis.com/request_count\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${local.function_name}\"",
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
          }] : [],
          local.create_cloud_function_resources ? [{
            "title" : "Cloud Function Execution Times",
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
                      "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.function_name}\"",
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
                      "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.function_name}\"",
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
                      "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.function_name}\"",
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
          }] : [],
          local.create_cloud_function_resources ? [{
            "title" : "Cloud Function Errors",
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
                      "filter" : "metric.type=\"run.googleapis.com/request_count\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${local.function_name}\" metric.label.\"response_code_class\"!=\"2xx\"",
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
          }] : [],
          local.create_cloud_function_resources ? [{
            "title" : "Cloud Function Max Concurrent Requests",
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
                      "filter" : "metric.type=\"run.googleapis.com/container/max_request_concurrencies\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${local.function_name}\" metric.label.\"state\"=\"active\"",
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
          }] : [],
          (local.create_lb_resources ? [jsondecode(local.lb_request_count_widget)] : []),
          (local.create_lb_resources ? [jsondecode(local.lb_latency_widget)] : []),
          (local.create_lb_resources ? [jsondecode(local.lb_errors_widget)] : []),
          (local.create_cloud_run_resources ? [jsondecode(local.request_count_widget)] : []),
          (local.create_cloud_run_resources ? [jsondecode(local.request_latencies_widget)] : []),
          (local.create_cloud_run_resources ? [jsondecode(local.request_errors_widget)] : []),
          (local.create_cloud_run_resources ? [jsondecode(local.max_concurrent_requests_widget)] : [])
        ])
      }
    }
  )
}
