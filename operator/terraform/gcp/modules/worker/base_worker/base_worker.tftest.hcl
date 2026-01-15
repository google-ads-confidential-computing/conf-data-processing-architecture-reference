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

mock_provider "google" {
  source          = "../../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}
mock_provider "null" {
  source          = "../../../../../../tools/tftesting/tfmocks/null/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment         = "environment"
  workgroup           = null
  project_id          = ""
  network             = ""
  subnet_id           = ""
  egress_internet_tag = ""
  instance_type       = ""
  instance_disk_image_family = {
    image_family  = "image_family"
    image_project = "image_project"
  }
  instance_disk_image              = ""
  worker_instance_disk_type        = ""
  worker_instance_disk_size_gb     = 0
  worker_logging_enabled           = false
  worker_monitoring_enabled        = false
  worker_memory_monitoring_enabled = false
  worker_container_log_redirect    = ""
  worker_image                     = "worker_image"
  worker_restart_policy            = ""
  allowed_operator_service_account = ""
  metadatadb_name                  = ""
  metadatadb_instance_name         = ""
  job_queue_sub                    = ""
  job_queue_topic                  = ""
  worker_service_account_email     = "service_account@email.com"
  worker_instance_force_replace    = false
  autoscaler_cloudfunction_name    = ""
  autoscaler_name                  = ""
  vm_instance_group_name           = ""
  alarms_enabled                   = false
  alarm_eval_period_sec            = ""
  alarm_duration_sec               = ""
  notification_channel_id          = ""
}

run "creates_compute_image" {
  command = plan

  assert {
    condition     = length(data.google_compute_image.tee_image) == 1
    error_message = "Didn't create image"
  }
}

run "doesnt_create_compute_image" {
  command = plan

  variables {
    instance_disk_image = "image"
  }

  assert {
    condition     = length(data.google_compute_image.tee_image) == 0
    error_message = "Created image"
  }
}

run "doesnt_create_trigger_for_force_replace" {
  command = plan

  assert {
    condition     = null_resource.worker_instance_replace_trigger.triggers["replace"] == ""
    error_message = "Wrong trigger"
  }
}

run "creates_trigger_for_force_replace" {
  # Since the value (the triggers["replace"]) as well as the expectation
  # reference timestamp(), it is not known during plan and cannot be mocked.
  command = apply

  variables {
    worker_instance_force_replace = true
  }

  assert {
    # We only compare up the minute of the timestamp as it is flaky to compare seconds
    condition     = substr(null_resource.worker_instance_replace_trigger.triggers["replace"], 0, 16) == substr(timestamp(), 0, 16)
    error_message = "Wrong trigger"
  }
}

run "creates_template_no_workgroup_with_input_disk" {
  command = plan

  variables {
    instance_disk_image = "instance_disk_image"
  }

  assert {
    condition     = google_compute_instance_template.worker_instance_template.name_prefix == "environment-worker-template"
    error_message = "Wrong name prefix"
  }
  assert {
    condition     = google_compute_instance_template.worker_instance_template.disk[0].source_image == "instance_disk_image"
    error_message = "Wrong source image"
  }
}

run "creates_template_with_workgroup_with_generated_disk" {
  command = plan

  variables {
    workgroup = "workgroup"
  }

  assert {
    condition     = google_compute_instance_template.worker_instance_template.name_prefix == "environment-workgroup-worker-template"
    error_message = "Wrong name prefix"
  }
  assert {
    condition     = google_compute_instance_template.worker_instance_template.disk[0].source_image == "google_compute_image_self_link"
    error_message = "Wrong source image"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  variables {
    worker_service_account_email = "email"
  }

  assert {
    condition     = output.worker_service_account_email == "email"
    error_message = "Wrong service account email"
  }
  assert {
    condition     = output.worker_template.name_prefix == "environment-worker-template"
    error_message = "Wrong template"
  }
}
