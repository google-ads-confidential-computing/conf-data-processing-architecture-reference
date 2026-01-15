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

mock_provider "google" {
  source = "../../../../../../tools/tftesting/tfmocks/google/"
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  project_id                             = ""
  environment                            = ""
  name                                   = ""
  pubsub_triggered_service_account_email = "email@google.com"
}

run "doesnt_create_notifications_if_not_enabled" {
  command = plan

  assert {
    condition     = length(module.notifications_customer_topic_1) == 0
    error_message = "Notification was created"
  }
  assert {
    condition     = length(module.notifications_customer_topic_2) == 0
    error_message = "Notification was created"
  }
  assert {
    condition     = length(module.parameter_customer_topic_id_1) == 0
    error_message = "Notification was created"
  }
  assert {
    condition     = length(module.parameter_customer_topic_id_2) == 0
    error_message = "Notification was created"
  }
  assert {
    condition     = length(google_pubsub_topic_iam_member.runtime_cloud_function_pubsub_iam_customer_topic_1) == 0
    error_message = "IAM member was created"
  }
  assert {
    condition     = length(google_pubsub_topic_iam_member.runtime_cloud_function_pubsub_iam_customer_topic_2) == 0
    error_message = "IAM member was created"
  }
}

run "creates_notifications_if_enabled" {
  command = plan

  variables {
    enable_job_completion_notifications_per_job = true
  }

  assert {
    condition     = length(module.notifications_customer_topic_1) == 1
    error_message = "Notification was created"
  }
  assert {
    condition     = length(module.notifications_customer_topic_2) == 1
    error_message = "Notification was created"
  }
  assert {
    condition     = length(module.parameter_customer_topic_id_1) == 1
    error_message = "Notification was created"
  }
  assert {
    condition     = length(module.parameter_customer_topic_id_2) == 1
    error_message = "Notification was created"
  }
  assert {
    condition     = length(google_pubsub_topic_iam_member.runtime_cloud_function_pubsub_iam_customer_topic_1) == 1
    error_message = "IAM member was created"
  }
  assert {
    condition     = length(google_pubsub_topic_iam_member.runtime_cloud_function_pubsub_iam_customer_topic_2) == 1
    error_message = "IAM member was created"
  }
}

run "iam_members_reference_proper_value" {
  command = apply

  variables {
    enable_job_completion_notifications_per_job = true
  }

  assert {
    condition     = google_pubsub_topic_iam_member.runtime_cloud_function_pubsub_iam_customer_topic_1[0].topic == "pubsub_topic_id"
    error_message = "Wrong topic ID"
  }
  assert {
    condition     = google_pubsub_topic_iam_member.runtime_cloud_function_pubsub_iam_customer_topic_2[0].topic == "pubsub_topic_id"
    error_message = "Wrong topic ID"
  }
}

run "generates_outputs_with_plan_no_notifications" {
  command = plan

  assert {
    condition     = output.notifications_customer_topic_id_1 == ""
    error_message = "Wrong topic ID"
  }
  assert {
    condition     = output.notifications_customer_topic_id_2 == ""
    error_message = "Wrong topic ID"
  }
}

run "generates_outputs_with_plan_with_notifications" {
  command = plan

  variables {
    enable_job_completion_notifications_per_job = true
  }

  assert {
    condition     = output.notifications_customer_topic_id_1 == "pubsub_topic_id"
    error_message = "Wrong topic ID"
  }
  assert {
    condition     = output.notifications_customer_topic_id_2 == "pubsub_topic_id"
    error_message = "Wrong topic ID"
  }
}
