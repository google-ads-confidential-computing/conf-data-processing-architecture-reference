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
  # Required for validation
  project_id  = "project_id"
  environment = "environment"
  region      = "us"
  region_zone = "us-central1"
  worker_subnet_cidr = {
    "us" : "0.0.0.0/0"
  }
  operator_package_bucket_location = ""
  spanner_instance_config          = ""
  worker_image                     = "image"
  collector_regional_config = {
    "us-central1" = {
      zonal_config = {
        "us-central1-c" = {
          min_collector_count              = 1
          max_collector_count              = 2
          collector_cpu_utilization_target = 0.8
        }
      }
    },
    "us-east1" = {
      zonal_config = {
        "us-east1-c" = {
          min_collector_count              = 1
          max_collector_count              = 2
          collector_cpu_utilization_target = 0.8
        }
      }
    }
  }
  collector_subnet_cidr = {
    "us-central1" = "10.3.0.0/16",
    "us-east1"    = "10.13.0.0/16"
  }
  proxy_subnet_cidr = {
    "us-central1" = "10.4.0.0/16",
    "us-east1"    = "10.14.0.0/16"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  providers = {
    google      = google
    archive     = archive
    google-beta = google-beta
  }

  // b/456789140 required to properly mock
  override_module {
    target = module.operator_service.module.frontend
    outputs = {
      frontend_service_cloudfunction_url = "mock_url"
    }
  }

  assert {
    condition     = output.worker_service_account_email == "environment-worker@project_id.iam.gserviceaccount.com"
    error_message = "Wrong email"
  }
  assert {
    condition     = output.vpc_network == "self_link"
    error_message = "Wrong network"
  }
  assert {
    condition     = output.frontend_service_cloudfunction_url == "mock_url"
    error_message = "Wrong url"
  }
}
