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
  cloudfunction_error_threshold                                               = ""
  cloudfunction_max_execution_time_max                                        = ""
  cloudfunction_5xx_threshold                                                 = ""
  lb_max_latency_ms                                                           = ""
  lb_5xx_threshold                                                            = ""
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

run "doesnt_create_frontend_service_account" {
  command = plan

  variables {
    frontend_service_cloudfunction_runtime_sa_email = "email"
  }

  assert {
    condition     = length(google_service_account.frontend_service_account) == 0
    error_message = "Created frontend_service_account"
  }
}

run "creates_frontend_service_account" {
  command = plan

  assert {
    condition     = google_service_account.frontend_service_account[0].account_id == "environment-frontend"
    error_message = "FE account ID is wrong"
  }
}

run "doesnt_create_archive" {
  command = plan

  variables {
    frontend_service_zip = "zip"
  }

  assert {
    condition     = length(data.archive_file.frontend_service_archive) == 0
    error_message = "Created archive"
  }
}

run "creates_archive_and_places_in_bucket" {
  command = plan

  variables {
    frontend_service_jar = "service_jar"
  }

  assert {
    condition     = data.archive_file.frontend_service_archive[0].output_path == "service_jar.zip"
    error_message = "Archive output path is wrong"
  }
  assert {
    condition     = google_storage_bucket_object.frontend_service_package_bucket_object[0].name == "environment_frontend_service_cloudfunction_mock_output_md5"
    error_message = "Object name is wrong"
  }
  assert {
    condition     = google_storage_bucket_object.frontend_service_package_bucket_object[0].source == "service_jar.zip"
    error_message = "Object source is wrong"
  }
}

run "doesnt_create_cloud_function" {
  command = plan

  assert {
    condition     = length(google_cloudfunctions2_function.frontend_service_cloudfunction) == 0
    error_message = "Created cloud function"
  }
}

run "creates_cloud_function_java11_autocreated_object_no_vpc_connector" {
  command = plan

  variables {
    create_frontend_service_cloud_function = true
  }

  assert {
    condition     = google_cloudfunctions2_function.frontend_service_cloudfunction[0].name == "environment-us-frontend-service"
    error_message = "name is wrong"
  }
  assert {
    condition     = google_cloudfunctions2_function.frontend_service_cloudfunction[0].build_config[0].runtime == "java11"
    error_message = "Uses wrong java version"
  }
  assert {
    condition     = google_cloudfunctions2_function.frontend_service_cloudfunction[0].build_config[0].source[0].storage_source[0].object == "environment_frontend_service_cloudfunction_mock_output_md5"
    error_message = "Uses wrong source object"
  }
  assert {
    condition     = google_cloudfunctions2_function.frontend_service_cloudfunction[0].service_config[0].vpc_connector == null
    error_message = "Using VPC connector"
  }
  assert {
    condition     = google_cloudfunctions2_function.frontend_service_cloudfunction[0].service_config[0].vpc_connector_egress_settings == null
    error_message = "Using VPC connector"
  }
}

run "creates_cloud_function_java21_preexistingzip_with_vpc_connector" {
  command = plan

  variables {
    create_frontend_service_cloud_function = true
    use_java21_runtime                     = true
    frontend_service_zip                   = "service.zip"
    vpc_connector_ids                      = { "us" : "id" }
  }

  assert {
    condition     = google_cloudfunctions2_function.frontend_service_cloudfunction[0].build_config[0].runtime == "java21"
    error_message = "Uses wrong java version"
  }
  assert {
    condition     = google_cloudfunctions2_function.frontend_service_cloudfunction[0].build_config[0].source[0].storage_source[0].object == "service.zip"
    error_message = "Uses wrong source object"
  }
  assert {
    condition     = google_cloudfunctions2_function.frontend_service_cloudfunction[0].service_config[0].vpc_connector == "id"
    error_message = "Using VPC connector"
  }
  assert {
    condition     = google_cloudfunctions2_function.frontend_service_cloudfunction[0].service_config[0].vpc_connector_egress_settings == "ALL_TRAFFIC"
    error_message = "Using VPC connector"
  }
}

run "doesnt_create_cloud_run_fe" {
  command = plan

  assert {
    condition     = length(module.cloud_run_fe) == 0
    error_message = "Created FE"
  }
}

run "creates_cloud_run_fe_for_each_region" {
  command = plan

  variables {
    frontend_service_cloudfunction_num_cpus     = 1
    frontend_service_cloud_run_regions          = ["us", "eu"]
    frontend_service_cloud_run_custom_audiences = ["audience1", "audience2"]

    frontend_service_parent_domain_name            = "parent_domain"
    frontend_service_parent_domain_name_project_id = "parent_domain_project_id"
    frontend_service_lb_domain                     = "domain"
  }

  assert {
    condition = [for fe_instance in module.cloud_run_fe : fe_instance.service_name] == [
      "environment-eu-cr-frontend-service",
      "environment-us-cr-frontend-service"
    ]
    error_message = "Wrong service names: ${jsonencode([for fe_instance in module.cloud_run_fe : fe_instance.service_name])}"
  }
}

run "doesnt_create_cloud_run_ip" {
  command = plan

  assert {
    condition     = length(google_compute_global_address.cloud_run_fe_ip_address) == 0
    error_message = "Set cloud_run_fe_ip_address"
  }
}

run "creates_cloud_run_ip" {
  command = plan

  variables {
    frontend_service_cloudfunction_num_cpus        = 1
    frontend_service_parent_domain_name            = "parent_domain"
    frontend_service_parent_domain_name_project_id = "parent_domain_project_id"
    frontend_service_cloud_run_regions             = ["us", "eu"]
    frontend_service_lb_domain                     = "domain"
  }

  assert {
    condition     = google_compute_global_address.cloud_run_fe_ip_address[0].name == "environment-fe-lb-ip-addr"
    error_message = "IP address is wrong"
  }
}

run "doesnt_create_fe_dns_a_records" {
  command = plan

  assert {
    condition     = length(module.fe_service_dns_a_records) == 0
    error_message = "Set fe_service_dns_a_records"
  }
}

run "creates_fe_dns_a_records" {
  command = plan

  variables {
    frontend_service_cloudfunction_num_cpus        = 1
    frontend_service_parent_domain_name            = "parent_domain"
    frontend_service_parent_domain_name_project_id = "parent_domain_project_id"
    frontend_service_cloud_run_regions             = ["us", "eu"]
    frontend_service_lb_domain                     = "domain"
  }

  assert {
    condition     = length(module.fe_service_dns_a_records) == 1
    error_message = "Doesn't set fe_service_dns_a_records"
  }
}

run "doesnt_create_lb" {
  command = plan

  assert {
    condition     = length(module.cloud_run_fe_load_balancer) == 0
    error_message = "Set cloud_run_fe_load_balancer"
  }
}

run "creates_lb" {
  command = plan

  variables {
    frontend_service_cloudfunction_num_cpus        = 1
    frontend_service_parent_domain_name            = "parent_domain"
    frontend_service_parent_domain_name_project_id = "parent_domain_project_id"
    frontend_service_cloud_run_regions             = ["us", "eu"]
    frontend_service_lb_domain                     = "domain"
  }

  assert {
    condition     = module.cloud_run_fe_load_balancer[0].url_map_name == "environment-wrkr-fe-cr-lb"
    error_message = "Wrong URL map name"
  }
}

run "creates_jobmetadatadb_iam_with_generated_account" {
  command = plan

  assert {
    condition     = google_spanner_database_iam_member.frontend_service_jobmetadatadb_iam.member == "serviceAccount:google_service_account_email"
    error_message = "Wrong email for IAM"
  }
}

run "creates_jobmetadatadb_iam_with_supplied_account" {
  command = plan

  variables {
    frontend_service_cloudfunction_runtime_sa_email = "sa_email"
  }

  assert {
    condition     = google_spanner_database_iam_member.frontend_service_jobmetadatadb_iam.member == "serviceAccount:sa_email"
    error_message = "Wrong email for IAM"
  }
}

run "creates_jobqueue_iam_with_generated_account" {
  command = plan

  assert {
    condition     = google_pubsub_topic_iam_member.frontend_service_jobqueue_iam.member == "serviceAccount:google_service_account_email"
    error_message = "Wrong email for IAM"
  }
}

run "creates_jobqueue_iam_with_supplied_account" {
  command = plan

  variables {
    frontend_service_cloudfunction_runtime_sa_email = "sa_email"
  }

  assert {
    condition     = google_pubsub_topic_iam_member.frontend_service_jobqueue_iam.member == "serviceAccount:sa_email"
    error_message = "Wrong email for IAM"
  }
}

run "generates_no_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.frontend_service_cloudfunction_url == ""
    error_message = "Cloud function url is set"
  }
  assert {
    condition     = output.frontend_cloud_run_information == toset([])
    error_message = "Cloud run infos are set"
  }
  assert {
    condition     = output.frontend_service_cloud_run_urls == []
    error_message = "Cloud run URLs are set"
  }
  assert {
    condition     = output.frontend_service_load_balancer_ip_address == ""
    error_message = "LB IP is set"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  variables {
    create_frontend_service_cloud_function  = true
    frontend_service_cloud_run_regions      = ["us", "eu"]
    frontend_service_cloudfunction_num_cpus = 1
  }

  assert {
    condition = [for info in output.frontend_cloud_run_information : {
      region       = info.region
      service_name = info.service_name.service_name
      }] == [
      {
        region       = "eu"
        service_name = "environment-eu-cr-frontend-service"
      },
      {
        region       = "us"
        service_name = "environment-us-cr-frontend-service"
      }
    ]
    error_message = "Cloud run infos are wrong: ${jsonencode(output.frontend_cloud_run_information)}"
  }
  assert {
    condition = output.frontend_service_cloud_run_urls == tolist([
      "cloud_run_v2_uri",
      "cloud_run_v2_uri"
    ])
    error_message = "Cloud run URLs are wrong: ${jsonencode(output.frontend_service_cloud_run_urls)}"
  }
  assert {
    condition     = output.frontend_service_cloudfunction_url == "cloudfunctions2_uri"
    error_message = "Cloud function url is wrong"
  }
  assert {
    condition     = output.frontend_service_load_balancer_ip_address == ""
    error_message = "LB IP is wrong"
  }
}
