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

module "max_job_processing_time" {
  source          = "../parameters"
  environment     = var.environment
  workgroup       = var.workgroup
  parameter_name  = "MAX_JOB_PROCESSING_TIME_SECONDS"
  parameter_value = var.max_job_processing_time
}

module "max_job_num_attempts" {
  source          = "../parameters"
  environment     = var.environment
  workgroup       = var.workgroup
  parameter_name  = "MAX_JOB_NUM_ATTEMPTS"
  parameter_value = var.max_job_num_attempts
}

module "worker_managed_instance_group_name" {
  source          = "../parameters"
  environment     = var.environment
  workgroup       = var.workgroup
  parameter_name  = "WORKER_MANAGED_INSTANCE_GROUP_NAME"
  parameter_value = module.autoscaling.worker_managed_instance_group_name
}

module "jobqueue" {
  source      = "../jobqueue"
  environment = var.environment
  workgroup   = var.workgroup

  alarms_enabled                  = var.alarms_enabled
  notification_channel_id         = var.notification_channel_id
  alarm_eval_period_sec           = var.jobqueue_alarm_eval_period_sec
  max_undelivered_message_age_sec = var.jobqueue_max_undelivered_message_age_sec
}

module "job_queue_topic_name" {
  source         = "../parameters"
  environment    = var.environment
  workgroup      = var.workgroup
  parameter_name = "JOB_PUBSUB_TOPIC_NAME"
  // Terraform definition of topic id and name are the opposite of GCP API definition
  parameter_value = module.jobqueue.jobqueue_pubsub_topic_id
}

module "job_queue_subscription_name" {
  source         = "../parameters"
  environment    = var.environment
  workgroup      = var.workgroup
  parameter_name = "JOB_PUBSUB_SUBSCRIPTION_NAME"
  // Terraform definition of subscription id and name are the opposite of GCP API definition
  parameter_value = module.jobqueue.jobqueue_pubsub_sub_id
}

module "worker" {
  source                        = "../worker/java_worker"
  environment                   = var.environment
  workgroup                     = var.workgroup
  project_id                    = var.project_id
  network                       = var.network
  subnet_id                     = var.subnet_id
  egress_internet_tag           = var.egress_internet_tag
  worker_instance_force_replace = var.worker_instance_force_replace

  metadatadb_instance_name = var.metadatadb_instance_name
  metadatadb_name          = var.metadatadb_name
  job_queue_sub            = module.jobqueue.jobqueue_pubsub_sub_name
  job_queue_topic          = module.jobqueue.jobqueue_pubsub_topic_name

  instance_type                = var.instance_type
  instance_disk_image_family   = var.instance_disk_image_family
  instance_disk_image          = var.instance_disk_image
  worker_instance_disk_type    = var.worker_instance_disk_type
  worker_instance_disk_size_gb = var.worker_instance_disk_size_gb

  worker_service_account_email = var.worker_service_account_email

  # Instance Metadata
  worker_logging_enabled           = var.worker_logging_enabled
  worker_monitoring_enabled        = var.worker_monitoring_enabled
  worker_container_log_redirect    = var.worker_container_log_redirect
  worker_memory_monitoring_enabled = var.worker_memory_monitoring_enabled
  worker_image                     = var.worker_image
  worker_image_signature_repos     = var.worker_image_signature_repos
  worker_restart_policy            = var.worker_restart_policy
  allowed_operator_service_account = var.allowed_operator_service_account

  autoscaler_cloudfunction_name        = module.autoscaling.autoscaler_cloudfunction_name
  autoscaler_name                      = module.autoscaling.autoscaler_name
  vm_instance_group_name               = module.autoscaling.worker_managed_instance_group_name
  vm_instance_group_base_instance_name = module.autoscaling.worker_managed_instance_group_base_instance_name

  alarms_enabled                                      = var.alarms_enabled
  java_custom_metrics_alarms_enabled                  = var.alarms_enabled
  alarm_duration_sec                                  = var.worker_alarm_duration_sec
  alarm_eval_period_sec                               = var.worker_alarm_eval_period_sec
  notification_channel_id                             = var.notification_channel_id
  enable_new_metrics                                  = var.enable_remote_metric_aggregation
  enable_legacy_metrics                               = var.enable_legacy_metrics
  legacy_jobclient_job_validation_failure_metric_type = var.legacy_jobclient_job_validation_failure_metric_type
  legacy_jobclient_error_metric_type                  = var.legacy_jobclient_error_metric_type
  legacy_worker_error_metric_type                     = var.legacy_worker_error_metric_type
  new_jobclient_job_validation_failure_metric_type    = var.new_jobclient_job_validation_failure_metric_type
  new_jobclient_error_metric_type                     = var.new_jobclient_error_metric_type
  new_worker_error_metric_type                        = var.new_worker_error_metric_type
}

module "autoscaling" {
  source                  = "../autoscaling"
  environment             = var.environment
  workgroup               = var.workgroup
  project_id              = var.project_id
  region                  = var.region
  subnet_id               = var.subnet_id
  vpc_connector_id        = var.vpc_connector_id
  auto_create_subnetworks = var.auto_create_subnetworks

  worker_template                     = module.worker.worker_template
  min_worker_instances                = var.min_worker_instances
  max_worker_instances                = var.max_worker_instances
  jobqueue_subscription_name          = module.jobqueue.jobqueue_pubsub_sub_name
  autoscaling_jobs_per_instance       = var.autoscaling_jobs_per_instance
  autoscaling_cloudfunction_memory_mb = var.autoscaling_cloudfunction_memory_mb
  worker_service_account              = module.worker.worker_service_account_email
  termination_wait_timeout_sec        = var.termination_wait_timeout_sec
  worker_scale_in_cron                = var.worker_scale_in_cron

  operator_package_bucket_name = var.operator_package_bucket_name
  worker_scale_in_jar          = var.worker_scale_in_jar
  worker_scale_in_zip          = var.worker_scale_in_zip

  metadatadb_instance_name     = var.metadatadb_instance_name
  metadatadb_name              = var.metadatadb_name
  asg_instances_table_ttl_days = var.asg_instances_table_ttl_days

  alarms_enabled                      = var.alarms_enabled
  notification_channel_id             = var.notification_channel_id
  alarm_eval_period_sec               = var.autoscaling_alarm_eval_period_sec
  alarm_duration_sec                  = var.autoscaling_alarm_duration_sec
  max_vm_instances_ratio_threshold    = var.autoscaling_max_vm_instances_ratio_threshold
  cloudfunction_5xx_threshold         = var.autoscaling_cloudfunction_5xx_threshold
  cloudfunction_error_threshold       = var.autoscaling_cloudfunction_error_threshold
  cloudfunction_max_execution_time_ms = var.autoscaling_cloudfunction_max_execution_time_ms
  cloudfunction_alarm_eval_period_sec = var.autoscaling_cloudfunction_alarm_eval_period_sec
  cloudfunction_alarm_duration_sec    = var.autoscaling_cloudfunction_alarm_duration_sec

  use_java21_runtime = var.autoscaling_cloudfunction_use_java21_runtime
}
