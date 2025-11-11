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

// google-beta provider is renamed to google
mock_provider "google" {
  source          = "../../../../../tfmocks/google-beta/"
  override_during = plan
}

variables {
  project_id                                = ""
  environment                               = "environment"
  region                                    = ""
  key_storage_domain                        = ""
  load_balancing_scheme                     = ""
  source_container_image_url                = ""
  key_storage_memory                        = 0
  key_storage_service_min_instances         = 0
  key_storage_service_max_instances         = 0
  spanner_instance_name                     = ""
  spanner_database_name                     = ""
  key_encryption_key_id                     = ""
  alarms_enabled                            = false
  alarm_eval_period_sec                     = ""
  alarm_duration_sec                        = ""
  cloud_run_max_execution_time_max          = 0
  cloud_run_5xx_threshold                   = 0
  cloud_run_alert_on_memory_usage_threshold = 0
  lb_max_latency_ms                         = ""
  lb_5xx_threshold                          = 0
  lb_5xx_ratio_threshold                    = 0
  key_storage_severity_map                  = {}
  populate_migration_key_data               = ""
  disable_key_set_acl                       = ""
  kms_key_base_uri                          = ""
  migration_kms_key_base_uri                = ""
  key_sets                                  = ["set1", "set2"]
  key_encryption_key_ring_id                = ""
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".

run "creates_crypto_key_iam_member_for_each_keyset" {
  command = plan

  assert {
    condition = [for _, member in google_kms_crypto_key_iam_member.kms_key_set_level_iam_policy : {
      key_id = member.crypto_key_id
      member = member.member
      }] == [
      {
        key_id = "/cryptoKeys/environment_set1_key_encryption_key"
        member = "serviceAccount:mock_google_service_account_email"
        }, {

        key_id = "/cryptoKeys/environment_set2_key_encryption_key"
        member = "serviceAccount:mock_google_service_account_email"
      }

    ]
    error_message = "Wrong members"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.load_balancer_ip == "mock_compute_global_address"
    error_message = "Wrong IP"
  }
  assert {
    condition     = output.key_storage_service_account_email == "mock_google_service_account_email"
    error_message = "Wrong email"
  }
  assert {
    condition     = output.key_storage_cloud_run_url == "mock_cloud_run_v2_uri"
    error_message = "Wrong URL"
  }
}
