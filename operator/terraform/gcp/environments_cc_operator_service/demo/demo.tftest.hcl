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
  worker_subnet_cidr = { "us-central1" : "0.0.0.0/0" }
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
    condition     = output.worker_service_account_email == "<PrecreatedWorkerServiceAccountEmail>"
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
