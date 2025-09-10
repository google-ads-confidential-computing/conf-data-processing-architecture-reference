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

moved {
  from = module.worker.module.base_worker.google_service_account.worker_service_account[0]
  to   = module.worker_service_account.google_service_account.generated_worker_service_account[0]
}

moved {
  from = module.worker.module.base_worker.google_spanner_database_iam_member.worker_jobmetadatadb_iam
  to   = module.worker_service_account.google_spanner_database_iam_member.worker_jobmetadatadb_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_storage_iam
  to   = module.worker_service_account.google_project_iam_member.worker_storage_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_secretmanager_iam
  to   = module.worker_service_account.google_project_iam_member.worker_secretmanager_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_logging_iam
  to   = module.worker_service_account.google_project_iam_member.worker_logging_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_monitoring_iam
  to   = module.worker_service_account.google_project_iam_member.worker_monitoring_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_instance_group_iam
  to   = module.worker_service_account.google_project_iam_member.worker_instance_group_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_workload_user_iam
  to   = module.worker_service_account.google_project_iam_member.worker_workload_user_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_pubsub_publisher_iam
  to   = module.worker_service_account.google_project_iam_member.worker_pubsub_publisher_iam
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_metric_descriptor.jobclient_job_validation_failure_metric[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_metric_descriptor.jobclient_job_validation_failure_metric[0]
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_metric_descriptor.jobclient_job_client_error_metric[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_metric_descriptor.jobclient_job_client_error_metric[0]
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_metric_descriptor.worker_job_error_metric[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_metric_descriptor.worker_job_error_metric[0]
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_metric_descriptor.jobclient_job_validation_failure_counter_metric[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_metric_descriptor.jobclient_job_validation_failure_counter_metric[0]
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_metric_descriptor.jobclient_job_client_error_counter_metric[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_metric_descriptor.jobclient_job_client_error_counter_metric[0]
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_metric_descriptor.worker_job_error_new_metric[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_metric_descriptor.worker_job_error_new_metric[0]
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_alert_policy.jobclient_job_validation_failure_alert[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_alert_policy.jobclient_job_validation_failure_alert[0]
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_alert_policy.worker_job_error_alert[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_alert_policy.worker_job_error_alert[0]
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_alert_policy.jobclient_job_validation_failure_counter_alert[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_alert_policy.jobclient_job_validation_failure_counter_alert[0]
}

moved {
  from = module.worker[0].module.java_custom_monitoring[0].google_monitoring_alert_policy.worker_job_error_new_alert[0]
  to   = module.java_global_custom_monitoring[0].google_monitoring_alert_policy.worker_job_error_new_alert[0]
}

moved {
  from = module.worker
  to   = module.worker[0]
}

moved {
  from = module.autoscaling
  to   = module.autoscaling[0]
}

moved {
  from = module.jobqueue
  to   = module.jobqueue[0]
}

moved {
  from = module.job_queue_topic_id
  to   = module.job_queue_topic_id[0]
}

moved {
  from = module.job_queue_topic_name
  to   = module.job_queue_topic_name[0]
}

moved {
  from = module.job_queue_subscription_id
  to   = module.job_queue_subscription_id[0]
}

moved {
  from = module.job_queue_subscription_name
  to   = module.job_queue_subscription_name[0]
}

moved {
  from = module.worker_managed_instance_group_name
  to   = module.worker_managed_instance_group_name[0]
}
