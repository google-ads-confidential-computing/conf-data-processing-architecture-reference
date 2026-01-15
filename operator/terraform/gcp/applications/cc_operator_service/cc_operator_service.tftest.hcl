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
mock_provider "archive" {
  source          = "../../../../../tools/tftesting/tfmocks/archive/"
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

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  project_id                       = ""
  environment                      = "environment"
  region                           = "us"
  worker_subnet_cidr               = { "us" : "0.0.0.0/0" }
  proxy_subnet_cidr                = { "us" : "0.0.0.0/0" }
  collector_subnet_cidr            = { "us" : "0.0.0.0/0" }
  region_zone                      = ""
  operator_package_bucket_location = ""
  spanner_instance_config          = ""
  worker_image                     = "worker_image"
}

run "doesnt_create_alarm" {
  command = plan

  variables {
    alarms_enabled = false
  }

  providers = {
    google      = google
    google-beta = google-beta
    archive     = archive
  }

  assert {
    condition     = length(google_monitoring_notification_channel.alarm_email) == 0
    error_message = "Created alarm"
  }
}

run "creates_alarm" {
  command = plan

  variables {
    alarms_enabled            = true
    alarms_notification_email = "email"
  }

  providers = {
    google      = google
    google-beta = google-beta
    archive     = archive
  }

  assert {
    condition     = length(google_monitoring_notification_channel.alarm_email) == 1
    error_message = "Didn't create alarm"
  }
}

run "doesnt_create_bucket_if_frontend_and_worker_have_buckets" {
  command = plan

  providers = {
    google      = google
    google-beta = google-beta
    archive     = archive
  }

  variables {
    frontend_service_path = {
      bucket_name   = "frontend_bucket"
      zip_file_name = "zip1"
    }
    worker_scale_in_path = {
      bucket_name   = "worker_bucket"
      zip_file_name = "zip2"
    }
  }

  assert {
    condition     = length(google_storage_bucket.operator_package_bucket) == 0
    error_message = "Created bucket"
  }
}

run "creates_bucket_if_frontend_or_worker_dont_have_buckets" {
  command = plan

  providers = {
    google      = google
    google-beta = google-beta
    archive     = archive
  }

  variables {
    frontend_service_path = {
      bucket_name   = "frontend_bucket"
      zip_file_name = "zip1"
    }
  }

  assert {
    condition     = length(google_storage_bucket.operator_package_bucket) == 1
    error_message = "Didn't create bucket"
  }
  assert {
    condition     = length(module.opentelemetry_collector) == 0
    error_message = ""
  }
}

run "doesnt_create_otel_collector" {
  command = plan

  providers = {
    google      = google
    google-beta = google-beta
    archive     = archive
  }

  assert {
    condition     = length(module.opentelemetry_collector) == 0
    error_message = "Created otel collector"
  }
  assert {
    condition     = length(module.opentelemetry_collector_load_balancer) == 0
    error_message = "Created LB"
  }
}

run "creates_otel_collector" {
  command = plan

  providers = {
    google      = google
    archive     = archive
    google-beta = google-beta
  }

  variables {
    enable_opentelemetry_collector = true
  }

  assert {
    condition     = length(module.opentelemetry_collector) == 1
    error_message = "Didn't create otel collector"
  }
  assert {
    condition     = length(module.opentelemetry_collector_load_balancer) == 1
    error_message = "Didn't create LB"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  providers = {
    google      = google
    archive     = archive
    google-beta = google-beta
  }

  assert {
    condition     = output.worker_service_account_email == "google_service_account_email"
    error_message = "Wrong email"
  }
  assert {
    condition     = output.vpc_network == "self_link"
    error_message = "Wrong network"
  }
  assert {
    condition     = output.frontend_service_cloudfunction_url == "cloudfunctions2_uri"
    error_message = "Wrong url"
  }
}
