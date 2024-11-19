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
  load_balancer_name      = google_compute_url_map.get_public_key_loadbalancer.name
  cloud_functions         = [for cf in google_cloudfunctions2_function.get_public_key_cloudfunction : cf]
  cloud_function_a_name   = local.cloud_functions[0].name
  cloud_function_a_region = local.cloud_functions[0].location
  cloud_function_b_name   = local.cloud_functions[1].name
  cloud_function_b_region = local.cloud_functions[1].location
}

module "load_balancer_alarms" {
  source = "../shared/loadbalancer_alarms"
  count  = var.alarms_enabled ? 1 : 0

  environment             = var.environment
  notification_channel_id = var.notification_channel_id
  load_balancer_name      = google_compute_url_map.get_public_key_loadbalancer.name
  service_prefix          = "${var.environment} Public Key Service"

  eval_period_sec            = var.alarm_eval_period_sec
  duration_sec               = var.alarm_duration_sec
  error_5xx_threshold        = var.get_public_key_lb_5xx_threshold
  max_latency_ms             = var.get_public_key_lb_max_latency_ms
  load_balancer_severity_map = var.public_key_alerts_severity_overrides
}

module "cloud_function_alarms" {
  source   = "../shared/cloudfunction_alarms"
  for_each = var.alarms_enabled ? google_cloudfunctions2_function.get_public_key_cloudfunction : {}

  environment             = var.environment
  notification_channel_id = var.notification_channel_id
  function_name           = each.value.name
  service_prefix          = "${var.environment}-${each.value.location} Public Key Service"

  eval_period_sec                 = var.alarm_eval_period_sec
  duration_sec                    = var.alarm_duration_sec
  error_5xx_threshold             = var.get_public_key_cloudfunction_5xx_threshold
  execution_time_max              = var.get_public_key_cloudfunction_max_execution_time_max
  execution_error_ratio_threshold = var.get_public_key_cloudfunction_error_ratio_threshold
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
        per_series_aligner = "ALIGN_MAX"
      }
    }
  }
  notification_channels = [var.notification_channel_id]

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
        per_series_aligner = "ALIGN_MAX"
      }
    }
  }
  notification_channels = [var.notification_channel_id]

  user_labels = {
    environment = var.environment
    severity    = lookup(var.public_key_alerts_severity_overrides, "public_key_service_get_public_key_general_error_alert", "urgent")
  }

  alert_strategy {
    # 30 minutes.
    auto_close = "1800s"
  }
}

resource "google_monitoring_dashboard" "dashboard" {
  count = var.alarms_enabled ? 1 : 0

  project = var.project_id

  # Ensure that `dashboard_json` does not detect changes repeatedly by copying
  # down the full value which include defaults or other values altered on the
  # service side. Use the following command to get the full value:
  #
  # terraform show -json | jq --raw-output ".. | select(type == \"object\" and has(\"address\")) | select(.address | contains(\"THE_RESOURCE_ADDRESS\")) | .values.dashboard_json" | jq
  #
  # String interpolations will need to be updated and `name` attribute should
  # be left off.
  dashboard_json = jsonencode(
    {
      "displayName" : "${var.environment} Public Key Hosting Dashboard",
      "mosaicLayout" : {
        "columns" : 12,
        "tiles" : [
          {
            "height" : 4,
            "widget" : {
              "title" : "Load Balancer Requests per Second",
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
                          "perSeriesAligner" : "ALIGN_RATE"
                        },
                        "filter" : "metric.type=\"loadbalancing.googleapis.com/https/request_count\" resource.type=\"https_lb_rule\" resource.label.\"url_map_name\"=\"${local.load_balancer_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_SUM",
                          "groupByFields" : [
                            "resource.label.\"url_map_name\""
                          ]
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
            "width" : 3
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Load Balancer Errors per Second",
              "xyChart" : {
                "chartOptions" : {
                  "mode" : "COLOR"
                },
                "dataSets" : [
                  {
                    "legendTemplate" : "400",
                    "minAlignmentPeriod" : "60s",
                    "plotType" : "LINE",
                    "targetAxis" : "Y2",
                    "timeSeriesQuery" : {
                      "timeSeriesFilter" : {
                        "aggregation" : {
                          "alignmentPeriod" : "60s",
                          "perSeriesAligner" : "ALIGN_RATE"
                        },
                        "filter" : "metric.type=\"loadbalancing.googleapis.com/https/request_count\" resource.type=\"https_lb_rule\" resource.label.\"url_map_name\"=\"${local.load_balancer_name}\" metric.label.\"response_code_class\"=\"400\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_SUM",
                          "groupByFields" : [
                            "metric.label.\"response_code_class\""
                          ]
                        }
                      }
                    }
                  },
                  {
                    "legendTemplate" : "500",
                    "minAlignmentPeriod" : "60s",
                    "plotType" : "LINE",
                    "targetAxis" : "Y2",
                    "timeSeriesQuery" : {
                      "timeSeriesFilter" : {
                        "aggregation" : {
                          "alignmentPeriod" : "60s",
                          "perSeriesAligner" : "ALIGN_RATE"
                        },
                        "filter" : "metric.type=\"loadbalancing.googleapis.com/https/request_count\" resource.type=\"https_lb_rule\" resource.label.\"url_map_name\"=\"${local.load_balancer_name}\" metric.label.\"response_code_class\"=\"500\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_SUM",
                          "groupByFields" : [
                            "metric.label.\"response_code_class\""
                          ]
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
            "width" : 3,
            "xPos" : 3
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Load Balancer Cache Misses",
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
                          "perSeriesAligner" : "ALIGN_SUM"
                        },
                        "filter" : "metric.type=\"loadbalancing.googleapis.com/https/request_count\" resource.type=\"https_lb_rule\" resource.label.\"url_map_name\"=\"${local.load_balancer_name}\" metric.label.\"cache_result\"!=\"HIT\"",
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
            "width" : 3,
            "xPos" : 6
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Load Balancer Latency",
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
                        "filter" : "metric.type=\"loadbalancing.googleapis.com/https/total_latencies\" resource.type=\"https_lb_rule\" resource.label.\"url_map_name\"=\"${local.load_balancer_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"url_map_name\""
                          ]
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
                        "filter" : "metric.type=\"loadbalancing.googleapis.com/https/total_latencies\" resource.type=\"https_lb_rule\" resource.label.\"url_map_name\"=\"${local.load_balancer_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"url_map_name\""
                          ]
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
                        "filter" : "metric.type=\"loadbalancing.googleapis.com/https/total_latencies\" resource.type=\"https_lb_rule\" resource.label.\"url_map_name\"=\"${local.load_balancer_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"url_map_name\""
                          ]
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
            "width" : 3,
            "xPos" : 9
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_a_region} Executions per Second",
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
                          "perSeriesAligner" : "ALIGN_RATE"
                        },
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_count\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_a_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_SUM",
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
            "width" : 3,
            "yPos" : 4
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_a_region} Execution Times",
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
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_a_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"function_name\""
                          ]
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
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_a_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"function_name\""
                          ]
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
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_a_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"function_name\""
                          ]
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
            "width" : 3,
            "xPos" : 3,
            "yPos" : 4
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_a_region} Errors Per Second",
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
                          "perSeriesAligner" : "ALIGN_RATE"
                        },
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_count\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_a_name}\" metric.label.\"status\"!=\"ok\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
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
            "width" : 3,
            "xPos" : 6,
            "yPos" : 4
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_a_region} Instance Count Max",
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
                          "perSeriesAligner" : "ALIGN_MAX"
                        },
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/instance_count\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_a_name}\""
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
            "width" : 3,
            "xPos" : 9,
            "yPos" : 4
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_a_region} Max Concurrent Requests",
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
                        "filter" : "metric.type=\"run.googleapis.com/container/max_request_concurrencies\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${local.cloud_function_a_name}\" metric.label.\"state\"=\"active\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"service_name\""
                          ]
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
                        "filter" : "metric.type=\"run.googleapis.com/container/max_request_concurrencies\" resource.type=\"cloud_run_revision\" metric.label.\"state\"=\"active\" resource.label.\"service_name\"=\"${local.cloud_function_a_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"service_name\""
                          ]
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
                        "filter" : "metric.type=\"run.googleapis.com/container/max_request_concurrencies\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${local.cloud_function_a_name}\" metric.label.\"state\"=\"active\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"service_name\""
                          ]
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
            "width" : 3,
            "yPos" : 8
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_b_region} Executions per Second",
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
                          "perSeriesAligner" : "ALIGN_RATE"
                        },
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_count\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_b_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_SUM",
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
            "width" : 3,
            "xPos" : 3,
            "yPos" : 8
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_b_region} Execution Times",
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
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_b_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"function_name\""
                          ]
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
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_b_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"function_name\""
                          ]
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
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_times\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_b_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"function_name\""
                          ]
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
            "width" : 3,
            "xPos" : 6,
            "yPos" : 8
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_b_region} Errors Per Second",
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
                          "perSeriesAligner" : "ALIGN_RATE"
                        },
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/execution_count\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_b_name}\" metric.label.\"status\"!=\"ok\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
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
            "width" : 3,
            "xPos" : 9,
            "yPos" : 8
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_b_region} Instance Count Max",
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
                          "perSeriesAligner" : "ALIGN_MAX"
                        },
                        "filter" : "metric.type=\"cloudfunctions.googleapis.com/function/instance_count\" resource.type=\"cloud_function\" resource.label.\"function_name\"=\"${local.cloud_function_b_name}\""
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
            "width" : 3,
            "yPos" : 12
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud Function ${local.cloud_function_b_region} Max Concurrent Requests",
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
                        "filter" : "metric.type=\"run.googleapis.com/container/max_request_concurrencies\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${local.cloud_function_b_name}\" metric.label.\"state\"=\"active\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"service_name\""
                          ]
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
                        "filter" : "metric.type=\"run.googleapis.com/container/max_request_concurrencies\" resource.type=\"cloud_run_revision\" metric.label.\"state\"=\"active\" resource.label.\"service_name\"=\"${local.cloud_function_b_name}\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"service_name\""
                          ]
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
                        "filter" : "metric.type=\"run.googleapis.com/container/max_request_concurrencies\" resource.type=\"cloud_run_revision\" resource.label.\"service_name\"=\"${local.cloud_function_b_name}\" metric.label.\"state\"=\"active\"",
                        "secondaryAggregation" : {
                          "alignmentPeriod" : "60s",
                          "crossSeriesReducer" : "REDUCE_MEAN",
                          "groupByFields" : [
                            "resource.label.\"service_name\""
                          ]
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
            "width" : 3,
            "xPos" : 3,
            "yPos" : 12
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud CDN RTT By Regions",
              "xyChart" : {
                "chartOptions" : {
                  "mode" : "COLOR"
                },
                "dataSets" : [
                  {
                    "plotType" : "LINE",
                    "targetAxis" : "Y1",
                    "timeSeriesQuery" : {
                      "timeSeriesQueryLanguage" : "fetch https_lb_rule\n| metric 'loadbalancing.googleapis.com/https/frontend_tcp_rtt'\n| group_by 1m,\n    [value_frontend_tcp_rtt_aggregate: aggregate(value.frontend_tcp_rtt)]\n| every 1m\n| group_by [metric.proxy_continent],\n    [value_frontend_tcp_rtt_aggregate_percentile:\n       percentile(value_frontend_tcp_rtt_aggregate, 95)]"
                    }
                  }
                ],
                "timeshiftDuration" : "0s",
                "yAxis" : {
                  "scale" : "LINEAR"
                }
              }
            },
            "width" : 3,
            "xPos" : 6,
            "yPos" : 12
          },
          {
            "height" : 4,
            "widget" : {
              "title" : "Cloud CDN Request Count By Response Code",
              "xyChart" : {
                "chartOptions" : {
                  "mode" : "COLOR"
                },
                "dataSets" : [
                  {
                    "plotType" : "LINE",
                    "targetAxis" : "Y1",
                    "timeSeriesQuery" : {
                      "timeSeriesQueryLanguage" : "fetch https_lb_rule\n| metric 'loadbalancing.googleapis.com/https/request_count'\n| filter (metric.cache_result != 'DISABLED')\n| group_by 1m, [row_count: row_count()]\n| every 1m\n| group_by [metric.response_code_class],\n    [row_count_aggregate: aggregate(row_count)]"
                    }
                  }
                ],
                "timeshiftDuration" : "0s",
                "yAxis" : {
                  "scale" : "LINEAR"
                }
              }
            },
            "width" : 3,
            "xPos" : 9,
            "yPos" : 12
          }
        ]
      },
    }
  )
}
