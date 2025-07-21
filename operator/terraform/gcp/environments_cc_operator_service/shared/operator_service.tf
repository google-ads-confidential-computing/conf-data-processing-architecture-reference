/**
 * Copyright 2023 Google LLC
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

module "operator_service" {
  source                  = "../../applications/cc_operator_service"
  environment             = var.environment
  project_id              = var.project_id
  region                  = var.region
  region_zone             = var.region_zone
  network_name_suffix     = var.network_name_suffix
  auto_create_subnetworks = var.auto_create_subnetworks
  worker_subnet_cidr      = var.worker_subnet_cidr
  collector_subnet_cidr   = var.collector_subnet_cidr
  proxy_subnet_cidr       = var.proxy_subnet_cidr

  # Global Alarms
  alarms_enabled            = var.alarms_enabled
  alarms_notification_email = var.alarms_notification_email

  # Spanner DB
  spanner_instance_config              = var.spanner_instance_config
  spanner_processing_units             = var.spanner_processing_units
  spanner_database_deletion_protection = var.spanner_database_deletion_protection

  # Worker
  instance_type                    = var.instance_type
  instance_disk_image_family       = var.instance_disk_image_family
  instance_disk_image              = var.instance_disk_image
  worker_instance_disk_type        = var.worker_instance_disk_type
  worker_instance_disk_size_gb     = var.worker_instance_disk_size_gb
  user_provided_worker_sa_email    = var.user_provided_worker_sa_email
  worker_instance_force_replace    = var.worker_instance_force_replace
  worker_logging_enabled           = var.worker_logging_enabled
  worker_container_log_redirect    = var.worker_container_log_redirect
  worker_monitoring_enabled        = var.worker_monitoring_enabled
  worker_image                     = var.worker_image
  worker_image_signature_repos     = var.worker_image_signature_repos
  worker_restart_policy            = var.worker_restart_policy
  allowed_operator_service_account = var.allowed_operator_service_account

  # Worker Alarms
  custom_metrics_alarms_enabled = var.custom_metrics_alarms_enabled
  worker_alarm_duration_sec     = var.worker_alarm_duration_sec
  worker_alarm_eval_period_sec  = var.worker_alarm_eval_period_sec

  # Autoscaling
  min_worker_instances          = var.min_worker_instances
  max_worker_instances          = var.max_worker_instances
  autoscaling_jobs_per_instance = var.autoscaling_jobs_per_instance

  # Autoscaling Alarms
  autoscaling_alarm_duration_sec               = var.autoscaling_alarm_duration_sec
  autoscaling_alarm_eval_period_sec            = var.autoscaling_alarm_eval_period_sec
  autoscaling_max_vm_instances_ratio_threshold = var.autoscaling_max_vm_instances_ratio_threshold

  # Scale-in service
  autoscaling_cloudfunction_memory_mb          = var.autoscaling_cloudfunction_memory_mb
  autoscaling_cloudfunction_use_java21_runtime = var.autoscaling_cloudfunction_use_java21_runtime
  worker_scale_in_jar                          = var.worker_scale_in_jar
  termination_wait_timeout_sec                 = var.termination_wait_timeout_sec
  worker_scale_in_cron                         = var.worker_scale_in_cron
  asg_instances_table_ttl_days                 = var.asg_instances_table_ttl_days

  # Scale-in service alarm
  autoscaling_cloudfunction_5xx_threshold         = var.autoscaling_cloudfunction_5xx_threshold
  autoscaling_cloudfunction_error_threshold       = var.autoscaling_cloudfunction_error_threshold
  autoscaling_cloudfunction_max_execution_time_ms = var.autoscaling_cloudfunction_max_execution_time_ms
  autoscaling_cloudfunction_alarm_duration_sec    = var.autoscaling_cloudfunction_alarm_duration_sec
  autoscaling_cloudfunction_alarm_eval_period_sec = var.autoscaling_cloudfunction_alarm_eval_period_sec

  # Job Queue Alarms
  jobqueue_alarm_eval_period_sec           = var.jobqueue_alarm_eval_period_sec
  jobqueue_max_undelivered_message_age_sec = var.jobqueue_max_undelivered_message_age_sec

  # VPC Service Control
  vpcsc_compatible           = var.vpcsc_compatible
  vpc_connector_machine_type = var.vpc_connector_machine_type

  # OpenTelemetry Collector
  enable_opentelemetry_collector   = var.enable_opentelemetry_collector
  collector_instance_type          = var.collector_instance_type
  max_collector_instances          = var.max_collector_instances
  min_collector_instances          = var.min_collector_instances
  user_provided_collector_sa_email = var.user_provided_collector_sa_email
  collector_service_port_name      = var.collector_service_port_name
  collector_service_port           = var.collector_service_port
  collector_domain_name            = var.collector_domain_name
  collector_dns_name               = var.collector_dns_name
  collector_min_instance_ready_sec = var.collector_min_instance_ready_sec
  collector_send_batch_max_size    = var.collector_send_batch_max_size
  collector_send_batch_size        = var.collector_send_batch_size
  collector_send_batch_timeout     = var.collector_send_batch_timeout
  collector_queue_size             = var.collector_queue_size

  collector_exceed_cpu_usage_alarm     = var.collector_exceed_cpu_usage_alarm
  collector_exceed_memory_usage_alarm  = var.collector_exceed_memory_usage_alarm
  collector_export_error_alarm         = var.collector_export_error_alarm
  collector_run_error_alarm            = var.collector_run_error_alarm
  collector_crash_error_alarm          = var.collector_crash_error_alarm
  worker_exporting_metrics_error_alarm = var.worker_exporting_metrics_error_alarm

  # Frontend Service
  create_frontend_service_cloud_function                          = var.create_frontend_service_cloud_function
  operator_package_bucket_location                                = var.operator_package_bucket_location
  frontend_service_jar                                            = var.frontend_service_jar
  frontend_service_cloudfunction_num_cpus                         = var.frontend_service_cloudfunction_num_cpus
  frontend_service_cloudfunction_memory_mb                        = var.frontend_service_cloudfunction_memory_mb
  frontend_service_cloudfunction_min_instances                    = var.frontend_service_cloudfunction_min_instances
  frontend_service_cloudfunction_max_instances                    = var.frontend_service_cloudfunction_max_instances
  frontend_service_cloudfunction_max_instance_request_concurrency = var.frontend_service_cloudfunction_max_instance_request_concurrency
  frontend_service_cloudfunction_runtime_sa_email                 = var.frontend_service_cloudfunction_runtime_sa_email
  frontend_service_cloudfunction_use_java21_runtime               = var.frontend_service_cloudfunction_use_java21_runtime

  frontend_service_cloudfunction_timeout_sec = var.frontend_service_cloudfunction_timeout_sec
  job_table_ttl_days                         = var.job_table_ttl_days
  job_version                                = var.job_version

  frontend_service_cloud_run_regions                     = var.frontend_service_cloud_run_regions
  frontend_service_cloud_run_deletion_protection         = var.frontend_service_cloud_run_deletion_protection
  frontend_service_cloud_run_source_container_image_url  = var.frontend_service_cloud_run_source_container_image_url
  frontend_service_cloud_run_cpu_idle                    = var.frontend_service_cloud_run_cpu_idle
  frontend_service_cloud_run_startup_cpu_boost           = var.frontend_service_cloud_run_startup_cpu_boost
  frontend_service_cloud_run_ingress_traffic_setting     = var.frontend_service_cloud_run_ingress_traffic_setting
  frontend_service_cloud_run_allowed_invoker_iam_members = var.frontend_service_cloud_run_allowed_invoker_iam_members
  frontend_service_cloud_run_binary_authorization        = var.frontend_service_cloud_run_binary_authorization
  frontend_service_cloud_run_custom_audiences            = var.frontend_service_cloud_run_custom_audiences

  frontend_service_enable_lb_backend_logging = var.frontend_service_enable_lb_backend_logging
  frontend_service_lb_allowed_request_paths  = var.frontend_service_lb_allowed_request_paths
  frontend_service_lb_domain                 = var.frontend_service_lb_domain

  frontend_service_lb_outlier_detection_interval_seconds                      = var.frontend_service_lb_outlier_detection_interval_seconds
  frontend_service_lb_outlier_detection_base_ejection_time_seconds            = var.frontend_service_lb_outlier_detection_base_ejection_time_seconds
  frontend_service_lb_outlier_detection_consecutive_errors                    = var.frontend_service_lb_outlier_detection_consecutive_errors
  frontend_service_lb_outlier_detection_enforcing_consecutive_errors          = var.frontend_service_lb_outlier_detection_enforcing_consecutive_errors
  frontend_service_lb_outlier_detection_consecutive_gateway_failure           = var.frontend_service_lb_outlier_detection_consecutive_gateway_failure
  frontend_service_lb_outlier_detection_enforcing_consecutive_gateway_failure = var.frontend_service_lb_outlier_detection_enforcing_consecutive_gateway_failure
  frontend_service_lb_outlier_detection_max_ejection_percent                  = var.frontend_service_lb_outlier_detection_max_ejection_percent

  frontend_service_parent_domain_name            = var.frontend_service_parent_domain_name
  frontend_service_parent_domain_name_project_id = var.frontend_service_parent_domain_name_project_id

  # Frontend Service Alarms
  frontend_alarm_duration_sec                   = var.frontend_alarm_duration_sec
  frontend_alarm_eval_period_sec                = var.frontend_alarm_eval_period_sec
  frontend_cloudfunction_5xx_threshold          = var.frontend_cloudfunction_5xx_threshold
  frontend_cloudfunction_error_threshold        = var.frontend_cloudfunction_error_threshold
  frontend_cloudfunction_max_execution_time_max = var.frontend_cloudfunction_max_execution_time_max
  frontend_lb_5xx_threshold                     = var.frontend_lb_5xx_threshold
  frontend_lb_max_latency_ms                    = var.frontend_lb_max_latency_ms

  frontend_cloud_run_error_5xx_alarm_config      = var.frontend_cloud_run_error_5xx_alarm_config
  frontend_cloud_run_non_5xx_error_alarm_config  = var.frontend_cloud_run_non_5xx_error_alarm_config
  frontend_cloud_run_execution_time_alarm_config = var.frontend_cloud_run_execution_time_alarm_config

  frontend_lb_error_5xx_alarm_config         = var.frontend_lb_error_5xx_alarm_config
  frontend_lb_non_5xx_error_alarm_config     = var.frontend_lb_non_5xx_error_alarm_config
  frontend_lb_request_latencies_alarm_config = var.frontend_lb_request_latencies_alarm_config

  # Parameters
  common_parameter_names                = var.common_parameter_names
  common_parameter_values               = var.common_parameter_values
  job_client_parameter_names            = var.job_client_parameter_names
  job_client_parameter_values           = var.job_client_parameter_values
  crypto_client_parameter_names         = var.crypto_client_parameter_names
  crypto_client_parameter_values        = var.crypto_client_parameter_values
  auto_scaling_client_parameter_names   = var.auto_scaling_client_parameter_names
  auto_scaling_client_parameter_values  = var.auto_scaling_client_parameter_values
  metric_client_parameter_names         = var.metric_client_parameter_names
  metric_client_parameter_values        = var.metric_client_parameter_values
  job_lifecycle_helper_parameter_names  = var.job_lifecycle_helper_parameter_names
  job_lifecycle_helper_parameter_values = var.job_lifecycle_helper_parameter_values
}
