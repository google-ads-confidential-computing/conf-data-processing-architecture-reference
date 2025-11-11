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
  source          = "../../../../../tfmocks/google/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment                     = "environment"
  workgroup                       = null
  alarms_enabled                  = false
  notification_channel_id         = ""
  alarm_eval_period_sec           = ""
  max_undelivered_message_age_sec = 0
}

run "creates_pubsub_topic_and_sub_without_workgroup" {
  command = plan

  assert {
    condition     = google_pubsub_topic.job_queue_topic.name == "environment-JobQueue"
    error_message = "Wrong topic name"
  }
  assert {
    condition     = google_pubsub_subscription.job_queue_sub.name == "environment-JobQueueSub"
    error_message = "Wrong sub name"
  }
  assert {
    condition     = google_pubsub_subscription.job_queue_sub.topic == "environment-JobQueue"
    error_message = "Subscription is for wrong topic"
  }
}

run "creates_pubsub_topic_and_sub_with_workgroup" {
  command = plan

  variables {
    workgroup = "workgroup"
  }

  assert {
    condition     = google_pubsub_topic.job_queue_topic.name == "environment-workgroup-JobQueue"
    error_message = "Wrong topic name"
  }
  assert {
    condition     = google_pubsub_subscription.job_queue_sub.name == "environment-workgroup-JobQueueSub"
    error_message = "Wrong sub name"
  }
  assert {
    condition     = google_pubsub_subscription.job_queue_sub.topic == "environment-workgroup-JobQueue"
    error_message = "Subscription is for wrong topic"
  }
}

run "doesnt_create_alert" {
  command = plan

  assert {
    condition     = length(google_monitoring_alert_policy.job_queue_undelivered_message_too_old_alert) == 0
    error_message = "Created alert"
  }
}

run "creates_alert" {
  command = plan

  variables {
    alarms_enabled = true
  }

  assert {
    condition     = length(google_monitoring_alert_policy.job_queue_undelivered_message_too_old_alert) == 1
    error_message = "Didn't create alert"
  }
  assert {
    condition     = strcontains(google_monitoring_alert_policy.job_queue_undelivered_message_too_old_alert[0].conditions[0].condition_threshold[0].filter, "environment-JobQueueSub")
    error_message = "Alerting on wrong subscription name"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.jobqueue_pubsub_topic_name == "environment-JobQueue"
    error_message = "Wrong topic name"
  }
  assert {
    condition     = output.jobqueue_pubsub_sub_name == "environment-JobQueueSub"
    error_message = "Wrong subscription name"
  }
  assert {
    condition     = output.jobqueue_pubsub_topic_id == "pubsub_topic_id"
    error_message = "Wrong topic ID"
  }
  assert {
    condition     = output.jobqueue_pubsub_sub_id == "pubsub_subscription_id"
    error_message = "Wrong subscription ID"
  }
}
