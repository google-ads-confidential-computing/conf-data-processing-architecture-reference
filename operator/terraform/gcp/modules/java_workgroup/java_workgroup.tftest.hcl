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
  source          = "../../../../../tools/tftesting/tfmocks/archive/"
  override_during = plan
}
mock_provider "google" {
  source          = "../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tools/tftesting/tfmocks/google-beta/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  auto_create_subnetworks                         = false
  min_worker_instances                            = 0
  max_worker_instances                            = 0
  autoscaling_jobs_per_instance                   = 0
  autoscaling_cloudfunction_memory_mb             = 0
  operator_package_bucket_name                    = ""
  worker_scale_in_jar                             = ""
  worker_scale_in_zip                             = ""
  termination_wait_timeout_sec                    = ""
  worker_scale_in_cron                            = ""
  asg_instances_table_ttl_days                    = 1
  autoscaling_cloudfunction_use_java21_runtime    = false
  autoscaling_alarm_eval_period_sec               = ""
  autoscaling_alarm_duration_sec                  = ""
  autoscaling_max_vm_instances_ratio_threshold    = 0
  autoscaling_cloudfunction_5xx_threshold         = 0
  autoscaling_cloudfunction_error_threshold       = 0
  autoscaling_cloudfunction_max_execution_time_ms = 0
  autoscaling_cloudfunction_alarm_eval_period_sec = ""
  autoscaling_cloudfunction_alarm_duration_sec    = ""
  jobqueue_alarm_eval_period_sec                  = ""
  jobqueue_max_undelivered_message_age_sec        = 0
  project_id                                      = "project_id"
  environment                                     = "environment"
  workgroup                                       = "workgroup"
  region                                          = ""
  max_job_processing_time                         = ""
  max_job_num_attempts                            = ""
  alarms_enabled                                  = false
  notification_channel_id                         = ""
  enable_remote_metric_aggregation                = false
  enable_legacy_metrics                           = false
  network                                         = ""
  subnet_id                                       = ""
  egress_internet_tag                             = ""
  instance_type                                   = ""
  instance_disk_image_family = {
    image_family  = "family"
    image_project = "project"
  }
  instance_disk_image                                 = ""
  worker_instance_disk_type                           = ""
  worker_instance_disk_size_gb                        = 0
  worker_logging_enabled                              = false
  worker_monitoring_enabled                           = false
  worker_memory_monitoring_enabled                    = false
  worker_container_log_redirect                       = ""
  worker_image                                        = "image"
  worker_restart_policy                               = ""
  allowed_operator_service_account                    = ""
  metadatadb_name                                     = ""
  metadatadb_instance_name                            = ""
  worker_service_account_email                        = "service_account@gmail.com"
  worker_instance_force_replace                       = false
  worker_alarm_eval_period_sec                        = ""
  worker_alarm_duration_sec                           = ""
  legacy_worker_error_metric_type                     = ""
  new_worker_error_metric_type                        = ""
  legacy_jobclient_job_validation_failure_metric_type = ""
  legacy_jobclient_error_metric_type                  = ""
  new_jobclient_job_validation_failure_metric_type    = ""
  new_jobclient_error_metric_type                     = ""
}

run "generates_outputs_with_plan" {
  command = plan

  providers = {
    google      = google
    google-beta = google-beta
    archive     = archive
  }

  assert {
    condition     = output.workgroup_id == "workgroup"
    error_message = "Wrong workgroup"
  }
  assert {
    condition     = output.jobqueue_pubsub_topic_id == "pubsub_topic_id"
    error_message = "Wrong topic"
  }
  assert {
    condition     = output.jobqueue_pubsub_sub_id == "pubsub_subscription_id"
    error_message = "Wrong sub"
  }
}
