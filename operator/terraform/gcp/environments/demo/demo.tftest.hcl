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
  source          = "../../../../../tfmocks/archive/"
  override_during = plan
}
mock_provider "external" {
  source          = "../../../../../tfmocks/external/"
  override_during = plan
}
mock_provider "google" {
  source          = "../../../../../tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tfmocks/google-beta/"
  override_during = plan
}
mock_provider "null" {
  source          = "../../../../../tfmocks/null/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  # Must set this to pass job_service module validation
  project_id                       = "project_id"
  environment                      = "environment"
  region                           = "us"
  region_zone                      = "us-central1"
  operator_package_bucket_location = ""
  spanner_instance_config          = ""

  worker_image = "image"
  worker_subnet_cidr = {
    "us" = "0.0.0.0/0"
  }
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

  assert {
    condition     = output.worker_service_account_email == "environment-worker@project_id.iam.gserviceaccount.com"
    error_message = "Wrong email"
  }
  assert {
    condition     = output.notifications_pubsub_topic_id == null
    error_message = "Wrong id"
  }
  assert {
    condition     = output.job_completion_notifications_internal_topic_id == null
    error_message = "Wrong id"
  }
  assert {
    condition     = output.job_completion_notifications_service_account_email == null
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
    condition     = output.frontend_service_load_balancer_url == ""
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
  assert {
    condition     = output.frontend_service_cloudfunction_url == "mock_url"
    error_message = "Wrong URL"
  }
}
