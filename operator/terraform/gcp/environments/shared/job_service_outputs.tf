/**
 * Copyright 2022 Google LLC
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

output "frontend_service_cloudfunction_url" {
  value       = module.job_service.frontend_service_cloudfunction_url
  description = "The frontend service cloud function url."
}

output "worker_service_account_email" {
  value       = module.job_service.worker_service_account_email
  description = "The worker service account email to provide to coordinator."
}

output "vpc_network" {
  value       = module.job_service.vpc_network
  description = "The VPC network used by the job service components."
}

output "notifications_pubsub_topic_id" {
  value       = module.job_service.notifications_pubsub_topic_id
  description = "The full ID of the Pub/Sub notifications topic."
}

output "job_completion_notifications_internal_topic_id" {
  value       = module.job_service.job_completion_notifications_internal_topic_id
  description = "The full ID of the Pub/Sub internal notifications topic."
}

output "job_completion_notifications_service_account_email" {
  value       = module.job_service.job_completion_notifications_service_account_email
  description = "The email of the service account to run the job completion notification feature."
}

output "frontend_service_cloud_run_urls" {
  value       = module.job_service.frontend_service_cloud_run_urls
  description = "The URLs of the individual Cloud Runs that comprise the frontend service."
}

output "frontend_service_load_balancer_url" {
  value       = module.job_service.frontend_service_load_balancer_url
  description = "The URL that can be used to call the frontend service via the load balancer."
}

output "frontend_service_load_balancer_ip_address" {
  value       = module.job_service.frontend_service_load_balancer_ip_address
  description = "The external IP address of the entry-point load balancer for the FE service."
}
