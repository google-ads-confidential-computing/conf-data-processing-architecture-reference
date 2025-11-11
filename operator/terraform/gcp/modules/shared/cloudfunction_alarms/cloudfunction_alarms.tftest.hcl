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
  source          = "../../../../../../tfmocks/google/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment               = ""
  notification_channel_id   = ""
  function_name             = ""
  service_prefix            = ""
  eval_period_sec           = ""
  duration_sec              = ""
  error_5xx_threshold       = 0
  execution_error_threshold = 0
  execution_time_max        = 0
}
