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

variable "environment" {
  description = "Description for the environment, e.g. dev, staging, production."
  type        = string
}

variable "notification_channel_id" {
  description = "Notification channel to which to send alarms."
  type        = string
}

variable "lb_url_map_name" {
  description = "Names of the URL map name of the load balancer for which to create alarms."
  type        = string
}

variable "service_prefix" {
  description = "Prefix to use in alert Display Name. Should contain environment."
  type        = string
}

################################################################################
# Alarm Variables.
################################################################################

variable "error_5xx_alarm_config" {
  description = "The configuration for the 5xx error alarm."

  type = object({
    enable_alarm    = bool   # Whether to enable this alarm
    eval_period_sec = number # Amount of time (in seconds) for alarm evaluation. Example: '60'
    duration_sec    = number # Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'
    error_threshold = number # error count greater than this to send alarm. Example: 0.
  })
}

variable "non_5xx_error_alarm_config" {
  description = "The configuration for the non-5xx error (3xx-4xx) alarm."

  type = object({
    enable_alarm    = bool   # Whether to enable this alarm
    eval_period_sec = number # Amount of time (in seconds) for alarm evaluation. Example: '60'
    duration_sec    = number # Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'
    error_threshold = number # error count greater than this to send alarm. Example: 500.
  })
}

variable "request_latencies_alarm_config" {
  description = "The configuration for the request latency alarm."

  type = object({
    enable_alarm    = bool   # Whether to enable this alarm
    eval_period_sec = number # Amount of time (in seconds) for alarm evaluation. Example: '60'
    duration_sec    = number # Amount of time (in seconds) after which to send alarm if conditions are met. Must be in minute intervals. Example: '60','120'
    threshold_ms    = number # Request latencies greater than this to send alarm. Example: 0.
  })
}
