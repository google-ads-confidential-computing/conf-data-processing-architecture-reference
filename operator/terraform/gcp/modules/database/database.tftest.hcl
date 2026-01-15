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
  instance_name                        = "instance"
  database_name                        = "database"
  database_schema                      = []
  environment                          = ""
  spanner_instance_config              = ""
  spanner_processing_units             = 0
  spanner_database_deletion_protection = false
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.database_name == "database"
    error_message = "Wrong DB name"
  }
  assert {
    condition     = output.instance_name == "instance"
    error_message = "Wrong DB name"
  }
}
