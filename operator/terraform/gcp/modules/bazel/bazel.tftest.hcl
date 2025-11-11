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
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.bazel_bin == "path"
    error_message = "Wrong bazel_bin"
  }
  assert {
    condition     = output.workspace == "path"
    error_message = "Wrong workspace"
  }
}
