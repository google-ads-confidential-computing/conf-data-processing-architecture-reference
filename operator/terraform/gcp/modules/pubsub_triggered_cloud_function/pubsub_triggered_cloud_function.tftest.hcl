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
mock_provider "google" {
  source          = "../../../../../tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tfmocks/google-beta/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment                                  = "environment"
  function_name                                = ""
  region                                       = ""
  description                                  = ""
  function_entrypoint                          = ""
  source_bucket_name                           = ""
  cloud_function_jar                           = ""
  cloud_function_zip                           = ""
  min_instance_count                           = 0
  max_instance_count                           = 0
  concurrency                                  = 0
  cpu_count                                    = ""
  memory_mb                                    = 0
  trigger_pubsub_id                            = ""
  trigger_pubsub_retry_policy                  = ""
  runtime_cloud_function_service_account_email = ""
  event_trigger_service_account_email          = ""
  use_java21_runtime                           = false
}

run "doesnt_create_archive_file_or_bucket_object_from_zip" {
  command = plan

  variables {
    cloud_function_zip = "cloud_function.zip"
  }

  assert {
    condition     = length(data.archive_file.cloud_function_archive) == 0
    error_message = "Created archive file"
  }
  assert {
    condition     = length(google_storage_bucket_object.cloud_function_archive_bucket_object) == 0
    error_message = "Created bucket object"
  }
}

run "creates_archive_file_and_bucket_object_from_jar" {
  command = plan

  variables {
    cloud_function_zip = ""
    cloud_function_jar = "cloud_function_jar"
  }

  assert {
    condition     = length(data.archive_file.cloud_function_archive) == 1
    error_message = "Didn't create archive file"
  }
  assert {
    condition     = length(google_storage_bucket_object.cloud_function_archive_bucket_object) == 1
    error_message = "Didn't create bucket object"
  }
  assert {
    condition     = google_storage_bucket_object.cloud_function_archive_bucket_object[0].name == "environment_pubsub_triggered_cloud_function_mock_output_md5"
    error_message = "Wrong object name"
  }
}

run "creates_cloud_function_java11_from_jar_no_connector_with_created_account" {
  command = plan

  variables {
    use_java21_runtime                  = false
    cloud_function_jar                  = "cloud_function_jar"
    vpc_connector_id                    = null
    event_trigger_service_account_email = ""
  }

  assert {
    condition     = google_cloudfunctions2_function.pubsub_triggered_cloud_function.build_config[0].runtime == "java11"
    error_message = "Wrong Java version"
  }
  assert {
    condition     = google_cloudfunctions2_function.pubsub_triggered_cloud_function.build_config[0].source[0].storage_source[0].object == "environment_pubsub_triggered_cloud_function_mock_output_md5"
    error_message = "Wrong source"
  }
  assert {
    condition = alltrue([
      google_cloudfunctions2_function.pubsub_triggered_cloud_function.service_config[0].vpc_connector == null,
      google_cloudfunctions2_function.pubsub_triggered_cloud_function.service_config[0].vpc_connector_egress_settings == null
    ])
    error_message = "Wrong VPC connector"
  }
  assert {
    condition     = google_cloudfunctions2_function.pubsub_triggered_cloud_function.event_trigger[0].service_account_email == "google_service_account_email"
    error_message = "Wrong source"
  }
}

run "creates_cloud_function_java21_from_zip_with_connector_with_supplied_account" {
  command = plan

  variables {
    use_java21_runtime                  = true
    cloud_function_zip                  = "cloud_function_zip"
    vpc_connector_id                    = "connector_id"
    event_trigger_service_account_email = "account_email"
  }

  assert {
    condition     = google_cloudfunctions2_function.pubsub_triggered_cloud_function.build_config[0].runtime == "java21"
    error_message = "Wrong Java version"
  }
  assert {
    condition     = google_cloudfunctions2_function.pubsub_triggered_cloud_function.build_config[0].source[0].storage_source[0].object == "cloud_function_zip"
    error_message = "Wrong source"
  }
  assert {
    condition = alltrue([
      google_cloudfunctions2_function.pubsub_triggered_cloud_function.service_config[0].vpc_connector == "connector_id",
      google_cloudfunctions2_function.pubsub_triggered_cloud_function.service_config[0].vpc_connector_egress_settings == "ALL_TRAFFIC"
    ])
    error_message = "Wrong VPC connector"
  }
  assert {
    condition     = google_cloudfunctions2_function.pubsub_triggered_cloud_function.event_trigger[0].service_account_email == "account_email"
    error_message = "Wrong source"
  }
}

run "doesnt_create_service_accounts" {
  command = plan

  variables {
    runtime_cloud_function_service_account_email = "runtime_email"
    event_trigger_service_account_email          = "trigger_email"
  }

  assert {
    condition     = length(google_service_account.runtime_cloud_function_service_account) == 0
    error_message = "Created service account"
  }
  assert {
    condition     = length(google_service_account.event_trigger_service_account) == 0
    error_message = "Created service account"
  }
  assert {
    condition     = google_cloud_run_service_iam_member.event_trigger_cloud_run_iam_policy.member == "serviceAccount:trigger_email"
    error_message = "Wrong invoker email"
  }
}

run "creates_service_accounts" {
  command = plan

  variables {
    runtime_cloud_function_service_account_email = ""
    event_trigger_service_account_email          = ""
  }

  assert {
    condition     = length(google_service_account.runtime_cloud_function_service_account) == 1
    error_message = "Didn't create service account"
  }
  assert {
    condition     = length(google_service_account.event_trigger_service_account) == 1
    error_message = "Didn't create service account"
  }
  assert {
    condition     = google_cloud_run_service_iam_member.event_trigger_cloud_run_iam_policy.member == "serviceAccount:google_service_account_email"
    error_message = "Wrong invoker email"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.pubsub_triggered_cloudfunction_url == "cloudfunctions2_uri"
    error_message = "Wrong url"
  }
  assert {
    condition     = output.pubsub_triggered_service_account_email == "google_service_account_email"
    error_message = "Wrong email"
  }
}
