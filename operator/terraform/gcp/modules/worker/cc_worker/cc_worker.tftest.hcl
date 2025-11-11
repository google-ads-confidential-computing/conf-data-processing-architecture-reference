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
  source = "../../../../../../tfmocks/google/"
}
mock_provider "null" {
  source = "../../../../../../tfmocks/null/"
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment         = "environment"
  project_id          = ""
  network             = ""
  subnet_id           = ""
  egress_internet_tag = ""
  instance_type       = ""
  instance_disk_image_family = {
    image_family  = "image_family"
    image_project = "image_project"
  }
  instance_disk_image                                 = ""
  worker_instance_disk_type                           = ""
  worker_instance_disk_size_gb                        = 0
  worker_logging_enabled                              = false
  worker_monitoring_enabled                           = false
  worker_memory_monitoring_enabled                    = false
  worker_container_log_redirect                       = ""
  worker_image                                        = "worker_image"
  worker_restart_policy                               = ""
  allowed_operator_service_account                    = ""
  metadatadb_name                                     = ""
  metadatadb_instance_name                            = ""
  job_queue_sub                                       = ""
  job_queue_topic                                     = ""
  worker_service_account_email                        = "service_account@email.com"
  worker_instance_force_replace                       = false
  autoscaler_cloudfunction_name                       = ""
  autoscaler_name                                     = ""
  vm_instance_group_name                              = ""
  alarms_enabled                                      = false
  cc_custom_metrics_alarms_enabled                    = false
  alarm_eval_period_sec                               = ""
  alarm_duration_sec                                  = ""
  notification_channel_id                             = ""
  joblifecyclehelper_job_pulling_failure_threshold    = 0
  joblifecyclehelper_job_completion_failure_threshold = 0
  joblifecyclehelper_job_extender_failure_threshold   = 0
  joblifecyclehelper_job_pool_failure_threshold       = 0
  joblifecyclehelper_job_processing_time_threshold    = 0
  joblifecyclehelper_job_release_failure_threshold    = 0
  joblifecyclehelper_job_waiting_time_threshold       = 0
}

run "generates_outputs_with_plan" {
  command = plan

  providers = {
    google = google
  }

  assert {
    condition     = output.worker_service_account_email == "service_account@email.com"
    error_message = "Wrong email"
  }
  assert {
    condition     = output.worker_template.name_prefix == "environment-worker-template"
    error_message = "Wrong template"
  }
}
