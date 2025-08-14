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

output "legacy_jobclient_job_validation_failure_metric_type" {
  value       = var.enable_legacy_metrics ? google_monitoring_metric_descriptor.jobclient_job_validation_failure_metric[0].type : ""
  description = "The metric for legacy JobClient job validation failures."
}

output "legacy_jobclient_error_metric_type" {
  value       = var.enable_legacy_metrics ? google_monitoring_metric_descriptor.jobclient_job_client_error_metric[0].type : ""
  description = "The metric for legacy JobClient errors."
}

output "legacy_worker_error_metric_type" {
  value       = var.enable_legacy_metrics ? google_monitoring_metric_descriptor.worker_job_error_metric[0].type : ""
  description = "The metric for legacy worker errors."
}

output "new_jobclient_job_validation_failure_metric_type" {
  value       = var.enable_new_metrics ? google_monitoring_metric_descriptor.jobclient_job_validation_failure_counter_metric[0].type : ""
  description = "The metric for legacy JobClient job validation failures."
}

output "new_jobclient_error_metric_type" {
  value       = var.enable_new_metrics ? google_monitoring_metric_descriptor.jobclient_job_client_error_counter_metric[0].type : ""
  description = "The metric for legacy JobClient errors."
}

output "new_worker_error_metric_type" {
  value       = var.enable_new_metrics ? google_monitoring_metric_descriptor.worker_job_error_new_metric[0].type : ""
  description = "The metric for legacy worker errors."
}
