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

override_data {
  target = data.external.bazel_bin
  values = {
    result = {
      path = "mock_path"
    }
  }
}
override_data {
  target = data.external.workspace
  values = {
    result = {
      path = "mock_workspace"
    }
  }
}

variables {
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.bazel_bin == "mock_path"
    error_message = "Wrong bazel bin"
  }
  assert {
    condition     = output.workspace == "mock_workspace"
    error_message = "Wrong workspace"
  }
}
