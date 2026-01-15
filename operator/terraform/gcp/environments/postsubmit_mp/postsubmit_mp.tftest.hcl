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

mock_provider "archive" {
  source          = "../../../../../tools/tftesting/tfmocks/archive/"
  override_during = plan
}
mock_provider "external" {
  source          = "../../../../../tools/tftesting/tfmocks/external/"
  override_during = plan
}
mock_provider "google" {
  source          = "../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tools/tftesting/tfmocks/google-beta/"
  override_during = plan
}
mock_provider "null" {
  source          = "../../../../../tools/tftesting/tfmocks/null/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  # Must set this to pass job_service module validation
  project_id                       = "project_id"
  environment                      = "environment"
  region_zone                      = "us-central1"
  operator_package_bucket_location = ""
  spanner_instance_config          = ""

  worker_image = "image"
}

run "generates_outputs_with_plan" {
  command = plan

  providers = {
    google      = google
    google-beta = google-beta
    archive     = archive
  }

  // b/456789140 required to properly mock
  override_module {
    target = module.job_service.module.frontend
    outputs = {
      frontend_service_cloud_run_urls           = ["url1", "url2"]
      frontend_service_load_balancer_ip_address = "mock_ip"
      frontend_service_cloudfunction_url        = "mock_url"
    }
  }
  override_module {
    target = module.job_service.module.notifications
    outputs = {
      notifications_pubsub_topic_id = "notifications_topic_id"
    }
  }
  override_module {
    target = module.job_service.module.job_completion_notifications
    outputs = {
      notifications_pubsub_topic_id = "internal_topic_id"
    }
  }
  override_module {
    target = module.customer_notifications
    outputs = {
      notifications_customer_topic_id_1 = "topic_1"
      notifications_customer_topic_id_2 = "topic_2"
    }
  }

  assert {
    condition     = output.worker_service_account_email == "postsubmit-mp-worker-sa@admcloud-adtech1.iam.gserviceaccount.com"
    error_message = "Wrong email"
  }
  assert {
    condition     = output.notifications_pubsub_topic_id == "notifications_topic_id"
    error_message = "Wrong id"
  }
  assert {
    condition     = output.job_completion_notifications_internal_topic_id == "internal_topic_id"
    error_message = "Wrong id"
  }
  assert {
    condition     = output.frontend_service_cloudfunction_url == "mock_url"
    error_message = "Wrong URL"
  }
  assert {
    condition     = output.notifications_customer_topic_id_1 == "topic_1"
    error_message = "Wrong topic ID"
  }
  assert {
    condition     = output.notifications_customer_topic_id_2 == "topic_2"
    error_message = "Wrong topic ID"
  }
  assert {
    condition     = output.job_completion_notifications_service_account_email == "environment-notification@project_id.iam.gserviceaccount.com"
    error_message = "Wrong email"
  }
  assert {
    condition = output.frontend_service_cloud_run_urls == [
      "url1",
      "url2"
    ]
    error_message = "Wrong URLs"
  }
  assert {
    condition     = output.frontend_service_load_balancer_url == "https://wrkr-fe-postsubmit-test.gcp.admcstesting.dev"
    error_message = "Wrong URL"
  }
  assert {
    condition     = output.frontend_service_load_balancer_ip_address == "mock_ip"
    error_message = "Wrong IP"
  }
  assert {
    condition     = output.vpc_network == "self_link"
    error_message = "Wrong network"
  }
}
