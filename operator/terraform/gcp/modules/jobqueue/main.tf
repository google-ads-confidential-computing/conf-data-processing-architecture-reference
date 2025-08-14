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
  resource_prefix = var.workgroup == null ? var.environment : "${var.environment}-${var.workgroup}"
}
resource "google_pubsub_topic" "job_queue_topic" {
  name = "${local.resource_prefix}-JobQueue"

  labels = {
    environment = var.environment,
    workgroup   = var.workgroup,
    type        = "scp-jobqueue"
  }
}

resource "google_pubsub_subscription" "job_queue_sub" {
  name  = "${local.resource_prefix}-JobQueueSub"
  topic = google_pubsub_topic.job_queue_topic.name

  ack_deadline_seconds = 600

  # Set ttl to empty to never delete the subscription because of inactivity
  expiration_policy {
    ttl = ""
  }

  labels = {
    environment = var.environment,
    workgroup   = var.workgroup
  }
}
