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

test {
  parallel = true
}
mock_provider "google" {
  source          = "../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  topic_name                      = "topic"
  subscription_name               = "subscription"
  environment                     = ""
  alarms_enabled                  = false
  notification_channel_id         = ""
  alarm_eval_period_sec           = ""
  max_undelivered_message_age_sec = 0
}

run "subscription_refers_to_proper_topic" {
  command = plan

  assert {
    condition     = google_pubsub_subscription.queue_sub.topic == "topic"
    error_message = "Wrong topic name"
  }
}

run "doesnt_enable_alerts" {
  command = plan

  assert {
    condition     = length(google_monitoring_alert_policy.queue_undelivered_message_too_old_alert) == 0
    error_message = "Alerts enabled"
  }
}

run "enables_alerts" {
  command = plan

  variables {
    alarms_enabled = true
  }

  assert {
    condition     = length(google_monitoring_alert_policy.queue_undelivered_message_too_old_alert) == 1
    error_message = "Alerts not enabled"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.queue_pubsub_topic_name == "topic"
    error_message = "Wrong topic"
  }
  assert {
    condition     = output.queue_pubsub_sub_name == "subscription"
    error_message = "Wrong subscription"
  }
  assert {
    condition     = output.queue_pubsub_topic_id == "pubsub_topic_id"
    error_message = "Wrong topic"
  }
  assert {
    condition     = output.queue_pubsub_sub_id == "pubsub_subscription_id"
    error_message = "Wrong subscription"
  }
}
