/**
 * Copyright 2026 Google LLC
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
  source          = "<MOCK_RELATIVE_PATH>/archive/"
  override_during = plan
}
mock_provider "external" {
  source          = "<MOCK_RELATIVE_PATH>/external/"
  override_during = plan
}
mock_provider "google" {
  source          = "<MOCK_RELATIVE_PATH>/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "<MOCK_RELATIVE_PATH>/google-beta/"
  override_during = plan
}
mock_provider "null" {
  source          = "<MOCK_RELATIVE_PATH>/null/"
  override_during = plan
}
