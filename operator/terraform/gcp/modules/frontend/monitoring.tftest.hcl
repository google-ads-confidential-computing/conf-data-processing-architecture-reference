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
  source          = "../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tools/tftesting/tfmocks/google-beta/"
  override_during = plan
}
mock_provider "archive" {
  source          = "../../../../../tools/tftesting/tfmocks/archive/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  project_id                                                                  = ""
  environment                                                                 = "environment"
  region                                                                      = "us"
  job_version                                                                 = ""
  frontend_service_cloud_run_regions                                          = []
  frontend_service_cloud_run_deletion_protection                              = false
  frontend_service_cloud_run_source_container_image_url                       = ""
  frontend_service_cloud_run_cpu_idle                                         = false
  frontend_service_cloud_run_startup_cpu_boost                                = false
  frontend_service_cloud_run_ingress_traffic_setting                          = ""
  frontend_service_cloud_run_allowed_invoker_iam_members                      = []
  frontend_service_cloud_run_binary_authorization                             = {}
  frontend_service_cloud_run_custom_audiences                                 = []
  frontend_service_enable_lb_backend_logging                                  = false
  frontend_service_lb_allowed_request_paths                                   = []
  frontend_service_lb_domain                                                  = ""
  frontend_service_parent_domain_name                                         = ""
  frontend_service_parent_domain_name_project_id                              = ""
  frontend_service_lb_outlier_detection_interval_seconds                      = 0
  frontend_service_lb_outlier_detection_base_ejection_time_seconds            = 0
  frontend_service_lb_outlier_detection_consecutive_errors                    = 0
  frontend_service_lb_outlier_detection_enforcing_consecutive_errors          = 0
  frontend_service_lb_outlier_detection_consecutive_gateway_failure           = 0
  frontend_service_lb_outlier_detection_enforcing_consecutive_gateway_failure = 0
  frontend_service_lb_outlier_detection_max_ejection_percent                  = 0
  create_frontend_service_cloud_function                                      = false
  operator_package_bucket_name                                                = ""
  frontend_service_jar                                                        = ""
  frontend_service_zip                                                        = ""
  frontend_service_cloudfunction_num_cpus                                     = 0
  frontend_service_cloudfunction_memory_mb                                    = 0
  frontend_service_cloudfunction_min_instances                                = 0
  frontend_service_cloudfunction_max_instances                                = 0
  frontend_service_cloudfunction_max_instance_request_concurrency             = 0
  frontend_service_cloudfunction_timeout_sec                                  = 0
  frontend_service_cloudfunction_runtime_sa_email                             = ""
  use_java21_runtime                                                          = false
  spanner_database_name                                                       = ""
  spanner_instance_name                                                       = ""
  job_metadata_table_ttl_days                                                 = 1
  job_table_name                                                              = ""
  job_queue_topic                                                             = ""
  job_queue_sub                                                               = ""
  alarms_enabled                                                              = false
  notification_channel_id                                                     = ""
  alarm_eval_period_sec                                                       = ""
  alarm_duration_sec                                                          = ""
  cloudfunction_error_threshold                                               = "1"
  cloudfunction_max_execution_time_max                                        = "1"
  cloudfunction_5xx_threshold                                                 = "1"
  lb_max_latency_ms                                                           = "1"
  lb_5xx_threshold                                                            = "1"
  cloud_run_error_5xx_alarm_config = {
    enable_alarm    = false
    eval_period_sec = 0
    duration_sec    = 0
    error_threshold = 0
  }
  cloud_run_non_5xx_error_alarm_config = {
    enable_alarm    = false
    eval_period_sec = 0
    duration_sec    = 0
    error_threshold = 0
  }
  cloud_run_execution_time_alarm_config = {
    enable_alarm    = false
    eval_period_sec = 0
    duration_sec    = 0
    threshold_ms    = 0
  }
  lb_error_5xx_alarm_config = {
    enable_alarm    = false
    eval_period_sec = 0
    duration_sec    = 0
    error_threshold = 0
  }
  lb_non_5xx_error_alarm_config = {
    enable_alarm    = false
    eval_period_sec = 0
    duration_sec    = 0
    error_threshold = 0
  }
  lb_request_latencies_alarm_config = {
    enable_alarm    = false
    eval_period_sec = 0
    duration_sec    = 0
    threshold_ms    = 0
  }
}

run "doesnt_create_frontend_cloudfunction_alarms" {
  command = plan

  assert {
    condition     = length(module.frontendservice_cloudfunction_alarms) == 0
    error_message = "Created cloudfunction alarms"
  }
}

run "creates_frontend_cloudfunction_alarms" {
  command = plan

  variables {
    alarms_enabled                         = true
    create_frontend_service_cloud_function = true
  }

  assert {
    condition     = length(module.frontendservice_cloudfunction_alarms) == 1
    error_message = "Didn't create cloudfunction alarms"
  }
}

run "doesnt_create_frontend_cloud_run_alarms" {
  command = plan

  assert {
    condition     = length(module.frontendservice_cloud_run_alarms) == 0
    error_message = "Created cloud run alarms"
  }
}

run "creates_frontend_cloud_run_alarms" {
  command = plan

  variables {
    alarms_enabled = true

    frontend_service_cloudfunction_num_cpus     = 1
    frontend_service_cloud_run_regions          = ["us", "eu"]
    frontend_service_cloud_run_custom_audiences = ["audience1", "audience2"]

    frontend_service_parent_domain_name            = "parent_domain"
    frontend_service_parent_domain_name_project_id = "parent_domain_project_id"
    frontend_service_lb_domain                     = "domain"
  }

  assert {
    condition     = length(module.frontendservice_cloud_run_alarms) == 1
    error_message = "Didn't create cloud run alarms"
  }
}

run "doesnt_create_load_balancer_alarms" {
  command = plan

  assert {
    condition     = length(module.frontendservice_load_balancer_alarms) == 0
    error_message = "Created lb alarms"
  }
}

run "creates_lb_alarms" {
  command = plan

  variables {
    alarms_enabled = true

    frontend_service_cloudfunction_num_cpus     = 1
    frontend_service_cloud_run_regions          = ["us", "eu"]
    frontend_service_cloud_run_custom_audiences = ["audience1", "audience2"]

    frontend_service_parent_domain_name            = "parent_domain"
    frontend_service_parent_domain_name_project_id = "parent_domain_project_id"
    frontend_service_lb_domain                     = "domain"
  }

  assert {
    condition     = length(module.frontendservice_load_balancer_alarms) == 1
    error_message = "Didn't create lb alarms"
  }
}

# TODO test the frontend_dashboard
