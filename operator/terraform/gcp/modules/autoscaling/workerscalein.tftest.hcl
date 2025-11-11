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
  source          = "../../../../../tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tfmocks/google-beta/"
  override_during = plan
}
mock_provider "archive" {
  source          = "../../../../../tfmocks/archive/"
  override_during = plan
}
mock_provider "null" {
  override_during = plan
  mock_resource "null_resource" {
    defaults = {
      id = "1234567890"
    }
  }
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  project_id  = ""
  environment = "environment"
  workgroup   = ""
  region      = "us"
  subnet_id   = ""
  worker_template = {
    id                = ""
    network_interface = []
  }
  min_worker_instances                = 0
  max_worker_instances                = 0
  jobqueue_subscription_name          = ""
  autoscaling_jobs_per_instance       = 0
  autoscaling_cloudfunction_memory_mb = 0
  worker_service_account              = "email"
  operator_package_bucket_name        = ""
  worker_scale_in_jar                 = ""
  worker_scale_in_zip                 = ""
  metadatadb_instance_name            = ""
  metadatadb_name                     = ""
  termination_wait_timeout_sec        = ""
  worker_scale_in_cron                = ""
  asg_instances_table_ttl_days        = 1
  use_java21_runtime                  = false
  alarms_enabled                      = false
  notification_channel_id             = ""
  alarm_eval_period_sec               = ""
  alarm_duration_sec                  = ""
  cloudfunction_error_threshold       = 0
  cloudfunction_max_execution_time_ms = 0
  cloudfunction_5xx_threshold         = 0
  max_vm_instances_ratio_threshold    = 0
  cloudfunction_alarm_eval_period_sec = ""
  cloudfunction_alarm_duration_sec    = ""
  auto_create_subnetworks             = false
}

run "doesnt_create_scale_in_archive_from_zip" {
  command = plan

  variables {
    worker_scale_in_zip = "scale_in"
  }

  assert {
    condition     = length(data.archive_file.worker_scale_in_archive) == 0
    error_message = "Created scale in archive"
  }
}

run "creates_scale_in_archive_from_jar" {
  command = plan

  variables {
    worker_scale_in_jar = "scale_in_jar"
  }

  assert {
    condition     = data.archive_file.worker_scale_in_archive[0].output_path == "scale_in_jar.zip"
    error_message = "Wrong output path"
  }
}

run "doesnt_create_package_object_from_zip" {
  command = plan

  variables {
    worker_scale_in_zip = "scale_in"
  }

  assert {
    condition     = length(google_storage_bucket_object.worker_scale_in_package_bucket_object) == 0
    error_message = "Created scale in archive"
  }
}

run "creates_package_object_from_jar" {
  command = plan

  variables {
    workgroup           = null
    worker_scale_in_jar = "scale_in_jar"
  }

  assert {
    condition     = google_storage_bucket_object.worker_scale_in_package_bucket_object[0].name == "environment_worker-scale-in-cloudfunction_mock_output_md5"
    error_message = "Wrong object name"
  }
  assert {
    condition     = google_storage_bucket_object.worker_scale_in_package_bucket_object[0].source == "scale_in_jar.zip"
    error_message = "Wrong source"
  }
}

run "creates_scalein_function_java11_with_zip_no_workgroup_no_vpc_conn" {
  command = plan

  variables {
    workgroup           = null
    worker_scale_in_zip = "scale_in_zip"
  }

  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.name == "environment-us-worker-scale-in"
    error_message = "Wrong function name"
  }
  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.build_config[0].runtime == "java11"
    error_message = "Wrong java version"
  }
  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.build_config[0].source[0].storage_source[0].object == "scale_in_zip"
    error_message = "Wrong source object"
  }
  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.service_config[0].vpc_connector == null
    error_message = "Wrong connector"
  }
  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.service_config[0].vpc_connector_egress_settings == null
    error_message = "Wrong connector egress settings"
  }
}

run "scale_in_function_uses_proper_mig_name" {
  command = plan

  variables {
    workgroup = null
  }

  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.service_config[0].environment_variables.MANAGED_INSTANCE_GROUP_NAME == "environment-worker-mig-12d687"
    error_message = "Wrong MIG name"
  }
}

run "creates_scalein_function_java21_no_zip_with_workgroup_with_vpc_conn" {
  command = plan

  variables {
    use_java21_runtime = true
    workgroup          = "workgroup"
    vpc_connector_id   = "connector_id"
  }

  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.name == "environment-workgroup-us-worker-scale-in"
    error_message = "Wrong function name"
  }
  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.build_config[0].runtime == "java21"
    error_message = "Wrong java version"
  }
  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.build_config[0].source[0].storage_source[0].object == "environment-workgroup_worker-scale-in-cloudfunction_mock_output_md5"
    error_message = "Wrong source object"
  }
  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.service_config[0].vpc_connector == "connector_id"
    error_message = "Wrong connector"
  }
  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.service_config[0].vpc_connector_egress_settings == "ALL_TRAFFIC"
    error_message = "Wrong connector egress settings"
  }
}
