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

// The google-beta provider is renamed to google
mock_provider "google" {
  source          = "../../../../../tfmocks/google-beta/"
  override_during = plan
}

variables {
  project_id                                 = ""
  environment                                = "environment"
  spanner_instance_config                    = ""
  spanner_processing_units                   = 0
  custom_configuration_name                  = null
  custom_configuration_display_name          = ""
  custom_configuration_base_config           = ""
  custom_configuration_read_replica_location = ""
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".

run "creates_db_using_proper_instance" {
  command = plan

  assert {
    condition     = google_spanner_database.keydb.instance == "environment-keydbinstance"
    error_message = "Wrong instance"
  }
}

run "doesnt_create_instance_config_if_not_custom" {
  command = plan

  assert {
    condition     = length(google_spanner_instance_config.example) == 0
    error_message = "Created instance config"
  }
}

run "creates_instance_config" {
  command = plan

  variables {
    custom_configuration_name = "custom_config"
  }

  assert {
    condition     = length(google_spanner_instance_config.example) == 1
    error_message = "Didn't create instance config"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.keydb_name == "environment-keydb"
    error_message = "Wrong keydb"
  }
  assert {
    condition     = output.keydb_instance_name == "environment-keydbinstance"
    error_message = "Wrong instance"
  }
}
