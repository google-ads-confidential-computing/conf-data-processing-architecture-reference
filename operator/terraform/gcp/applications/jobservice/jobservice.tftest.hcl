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
  # Must set for google storage bucket name validation
  project_id                           = "project_id"
  environment                          = "environment"
  region                               = "us"
  worker_subnet_cidr                   = { "us" : "0.0.0.0/0" }
  proxy_subnet_cidr                    = { "us" : "0.0.0.0/0" }
  collector_subnet_cidr                = { "us" : "0.0.0.0/0" }
  region_zone                          = ""
  operator_package_bucket_location     = ""
  auto_create_subnetworks              = false
  network_name_suffix                  = "networksuffix"
  alarms_enabled                       = false
  alarms_notification_email            = ""
  spanner_instance_config              = ""
  spanner_processing_units             = 0
  spanner_database_deletion_protection = false
  worker_image                         = "worker_image"

  frontend_service_jar                                            = ""
  frontend_service_cloudfunction_num_cpus                         = 0
  frontend_service_cloudfunction_memory_mb                        = 0
  frontend_service_cloudfunction_min_instances                    = 0
  frontend_service_cloudfunction_max_instances                    = 0
  frontend_service_cloudfunction_max_instance_request_concurrency = 1
  frontend_service_cloudfunction_timeout_sec                      = 0
  job_version                                                     = ""
  frontend_cloudfunction_use_java21_runtime                       = false
  autoscaling_cloudfunction_use_java21_runtime                    = false
  notification_cloudfunction_use_java21_runtime                   = false
  frontend_alarm_eval_period_sec                                  = "1"
  frontend_alarm_duration_sec                                     = "1"
  frontend_cloudfunction_error_threshold                          = "1"
  frontend_cloudfunction_max_execution_time_max                   = "1"
  frontend_cloudfunction_5xx_threshold                            = "1"
  frontend_lb_max_latency_ms                                      = "1"
  frontend_lb_5xx_threshold                                       = "1"

  job_metadata_table_ttl_days                           = 1
  instance_type                                         = ""
  worker_instance_disk_type                             = ""
  worker_instance_disk_size_gb                          = 0
  worker_logging_enabled                                = false
  worker_monitoring_enabled                             = false
  worker_memory_monitoring_enabled                      = false
  worker_container_log_redirect                         = ""
  worker_restart_policy                                 = ""
  max_job_processing_time                               = ""
  max_job_num_attempts                                  = ""
  user_provided_worker_sa_email                         = ""
  worker_instance_force_replace                         = false
  worker_alarm_eval_period_sec                          = "1"
  worker_alarm_duration_sec                             = "1"
  java_job_validations_to_alert                         = []
  min_worker_instances                                  = 0
  max_worker_instances                                  = 0
  autoscaling_jobs_per_instance                         = 0
  autoscaling_cloudfunction_memory_mb                   = 0
  worker_scale_in_jar                                   = ""
  termination_wait_timeout_sec                          = ""
  worker_scale_in_cron                                  = ""
  asg_instances_table_ttl_days                          = 1
  autoscaling_alarm_eval_period_sec                     = "1"
  autoscaling_alarm_duration_sec                        = "1"
  autoscaling_max_vm_instances_ratio_threshold          = 1
  autoscaling_cloudfunction_5xx_threshold               = 1
  autoscaling_cloudfunction_error_threshold             = 1
  autoscaling_cloudfunction_max_execution_time_ms       = 1
  autoscaling_cloudfunction_alarm_eval_period_sec       = "1"
  autoscaling_cloudfunction_alarm_duration_sec          = "1"
  jobqueue_alarm_eval_period_sec                        = "1"
  jobqueue_max_undelivered_message_age_sec              = 1
  vpcsc_compatible                                      = false
  vpc_connector_machine_type                            = ""
  enable_job_completion_notifications                   = false
  enable_job_completion_notifications_per_job           = false
  job_completion_notifications_cloud_function_jar       = ""
  job_completion_notifications_cloud_function_cpu_count = ""
  job_completion_notifications_cloud_function_memory_mb = 0
  job_completion_notifications_service_account_email    = ""
}

run "doesnt_create_bucket_if_all_have_bucket_names" {
  command = plan

  variables {
    frontend_service_path = {
      bucket_name   = "frontend_bucket"
      zip_file_name = "zip1"
    }
    worker_scale_in_path = {
      bucket_name   = "worker_bucket"
      zip_file_name = "zip2"
    }
    job_completion_notifications_cloud_function_path = {
      bucket_name   = "function_bucket"
      zip_file_name = "zip3"
    }
  }

  assert {
    condition     = length(google_storage_bucket.operator_package_bucket) == 0
    error_message = "Created bucket"
  }
}
run "creates_bucket_if_any_dont_have_bucket_names" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  variables {
    worker_scale_in_path = {
      bucket_name   = "worker_bucket"
      zip_file_name = "zip2"
    }
    job_completion_notifications_cloud_function_path = {
      bucket_name   = "function_bucket"
      zip_file_name = "zip3"
    }
  }

  assert {
    condition     = length(google_storage_bucket.operator_package_bucket) == 1
    error_message = "Didn't create bucket"
  }
}

run "doesnt_create_alarm_or_custom_monitoring" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  assert {
    condition     = length(google_monitoring_notification_channel.alarm_email) == 0
    error_message = "Created alarm email"
  }
  assert {
    condition     = length(module.java_global_custom_monitoring) == 0
    error_message = "Created custom monitoring"
  }
}

run "creates_alarm_and_custom_monitoring" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  variables {
    alarms_enabled            = true
    alarms_notification_email = "email"
  }

  assert {
    condition     = length(google_monitoring_notification_channel.alarm_email) == 1
    error_message = "Didn't create alarm email"
  }
  assert {
    condition     = length(module.java_global_custom_monitoring) == 1
    error_message = "Didn't create custom monitoring"
  }
}

run "doesnt_create_legacy_resources" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  variables {
    enable_legacy_worker = false
    initial_workgroup    = "workgroup"
    workgroup_configs = {
      "workgroup" : {
        worker_image = "worker_image"
      }
    }
  }

  assert {
    condition     = length(module.jobqueue) == 0
    error_message = "Created jobqueue"
  }
  assert {
    condition     = length(module.job_queue_topic_id) == 0
    error_message = "Created jobqueue topic"
  }
  assert {
    condition     = length(module.job_queue_subscription_id) == 0
    error_message = "Created jobqueue subscription"
  }
  assert {
    condition     = length(module.job_queue_topic_name) == 0
    error_message = "Created jobqueue topic name"
  }
  assert {
    condition     = length(module.job_queue_subscription_name) == 0
    error_message = "Created jobqueue subscription name"
  }
  assert {
    condition     = length(module.worker_managed_instance_group_name) == 0
    error_message = "Created mig name"
  }
  assert {
    condition     = length(module.worker) == 0
    error_message = "Created worker"
  }
  assert {
    condition     = length(module.autoscaling) == 0
    error_message = "Created autoscaling"
  }
}

run "creates_legacy_resources" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  variables {
    enable_legacy_worker = true
    initial_workgroup    = "workgroup"
    workgroup_configs = {
      "workgroup" : {
        worker_image = "worker_image"
      }
    }
  }

  assert {
    condition     = length(module.jobqueue) == 1
    error_message = "Didn't create jobqueue"
  }
  assert {
    condition     = length(module.job_queue_topic_id) == 1
    error_message = "Didn't create jobqueue topic"
  }
  assert {
    condition     = length(module.job_queue_subscription_id) == 1
    error_message = "Didn't create jobqueue subscription"
  }
  assert {
    condition     = length(module.job_queue_topic_name) == 1
    error_message = "Didn't create jobqueue topic name"
  }
  assert {
    condition     = length(module.job_queue_subscription_name) == 1
    error_message = "Didn't create jobqueue subscription name"
  }
  assert {
    condition     = length(module.worker_managed_instance_group_name) == 1
    error_message = "Didn't create mig name"
  }
  assert {
    condition     = length(module.worker) == 1
    error_message = "Didn't create worker"
  }
  assert {
    condition     = length(module.autoscaling) == 1
    error_message = "Didn't create autoscaling"
  }
}

run "doesnt_create_job_completion_notification_resources" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  assert {
    condition     = length(module.notifications_topic_id) == 0
    error_message = "Created notifications topic"
  }
  assert {
    condition     = length(module.notifications) == 0
    error_message = "Created notifications"
  }
  assert {
    condition     = length(module.job_completion_notifications) == 0
    error_message = "Created job comp notifications"
  }
  assert {
    condition     = length(module.job_completion_notifications_internal_topic_id) == 0
    error_message = "Created job comp notifications internal"
  }
  assert {
    condition     = length(module.job_completion_notifications_cloud_function) == 0
    error_message = "Created job comp cloud function"
  }
  assert {
    condition     = length(google_pubsub_topic_iam_member.job_completion_notifications_cloud_function_iam_global_notification) == 0
    error_message = "Created IAM member"
  }
}

run "creates_job_completion_notification_resources" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  variables {
    enable_job_completion_notifications = true
  }

  assert {
    condition     = length(module.notifications_topic_id) == 1
    error_message = "Didn't create notifications topic"
  }
  assert {
    condition     = length(module.notifications) == 1
    error_message = "Didn't create notifications"
  }
  assert {
    condition     = length(module.job_completion_notifications) == 1
    error_message = "Didn't create job comp notifications"
  }
  assert {
    condition     = length(module.job_completion_notifications_internal_topic_id) == 1
    error_message = "Didn't create job comp notifications internal"
  }
  assert {
    condition     = length(module.job_completion_notifications_cloud_function) == 1
    error_message = "Didn't create job comp cloud function"
  }
  assert {
    condition     = length(google_pubsub_topic_iam_member.job_completion_notifications_cloud_function_iam_global_notification) == 1
    error_message = "Didn't create IAM member"
  }
}

run "doesnt_create_otel_resources" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  assert {
    condition     = length(module.opentelemetry_collector_address) == 0
    error_message = "Created collector address"
  }
  assert {
    condition     = length(module.opentelemetry_collector) == 0
    error_message = "Created collector"
  }
  assert {
    condition     = length(module.opentelemetry_collector_load_balancer) == 0
    error_message = "Created collector LB"
  }
}

run "creates_otel_resources" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  variables {
    enable_remote_metric_aggregation = true
    enable_opentelemetry_collector   = true
  }

  assert {
    condition     = length(module.opentelemetry_collector_address) == 1
    error_message = "Didn't create collector address"
  }
  assert {
    condition     = length(module.opentelemetry_collector) == 1
    error_message = "Didn't create collector"
  }
  assert {
    condition     = length(module.opentelemetry_collector_load_balancer) == 1
    error_message = "Didn't create collector LB"
  }
}

run "creates_workgroups_for_each_config" {
  command = plan

  providers = {
    google  = google
    archive = archive
  }

  variables {
    workgroup_configs = {
      "workgroup1" : {
        worker_image = "image"
      },
      "workgroup2" : {
        worker_image = "image"
      }
    }
  }

  assert {
    condition     = length(module.workgroups) == 2
    error_message = "Wrong workgroups"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  providers = {
    google      = google
    google-beta = google-beta
    archive     = archive
  }

  variables {
    enable_job_completion_notifications = true
  }

  assert {
    condition     = output.frontend_service_cloudfunction_url == "cloudfunctions2_uri"
    error_message = "Wrong url"
  }
  assert {
    condition     = output.worker_service_account_email == "google_service_account_email"
    error_message = "Wrong email"
  }
  assert {
    condition     = output.vpc_network == "self_link"
    error_message = "Wrong VPC network"
  }
  assert {
    condition     = output.notifications_pubsub_topic_id == "pubsub_topic_id"
    error_message = "Wrong topic ID"
  }
  assert {
    condition     = output.job_completion_notifications_internal_topic_id == "pubsub_topic_id"
    error_message = "Wrong topic ID"
  }
  assert {
    condition     = output.job_completion_notifications_service_account_email == "google_service_account_email"
    error_message = "Wrong email"
  }
  assert {
    condition     = output.frontend_service_cloud_run_urls == []
    error_message = "Wrong urls"
  }
  assert {
    condition     = output.frontend_service_load_balancer_url == ""
    error_message = "Wrong url"
  }
  assert {
    condition     = output.frontend_service_load_balancer_ip_address == ""
    error_message = "Wrong IP"
  }
}
