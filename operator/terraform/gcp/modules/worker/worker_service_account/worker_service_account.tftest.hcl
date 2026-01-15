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
  source          = "../../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../../tools/tftesting/tfmocks/google-beta/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment                   = "environment"
  project_id                    = "project_id"
  metadatadb_name               = "metadatadb_name"
  metadatadb_instance_name      = "metadatadb_instance_name"
  user_provided_worker_sa_email = "user_provided_worker_sa_email"
}

run "doesn_not_create_account_if_email_provided" {
  command = plan

  assert {
    condition     = length(google_service_account.generated_worker_service_account) == 0
    error_message = "generated_worker_service_account is set"
  }
}

run "creates_account_if_no_email_provided" {
  command = plan

  variables {
    user_provided_worker_sa_email = ""
  }

  assert {
    condition     = length(google_service_account.generated_worker_service_account) == 1
    error_message = "generated_worker_service_account is not set"
  }
  assert {
    condition     = google_service_account.generated_worker_service_account[0].email == "google_service_account_email"
    error_message = "generated_worker_service_account email is not correct"
  }
}

run "creates_members_with_roles" {
  command = plan

  assert {
    condition     = google_project_iam_member.worker_storage_iam.role == "roles/storage.admin"
    error_message = "worker_storage_iam role is incorrect"
  }
  assert {
    condition     = google_project_iam_member.worker_secretmanager_iam.role == "roles/secretmanager.secretAccessor"
    error_message = "worker_secretmanager_iam role is incorrect"
  }
  assert {
    condition     = google_project_iam_member.worker_logging_iam.role == "roles/logging.logWriter"
    error_message = "worker_logging_iam role is incorrect"
  }
  assert {
    condition     = google_project_iam_member.worker_monitoring_iam.role == "roles/monitoring.editor"
    error_message = "worker_monitoring_iam role is incorrect"
  }
  assert {
    condition     = google_project_iam_member.worker_instance_group_iam.role == "roles/compute.networkAdmin"
    error_message = "worker_instance_group_iam role is incorrect"
  }
  assert {
    condition     = google_project_iam_member.worker_workload_user_iam.role == "roles/confidentialcomputing.workloadUser"
    error_message = "worker_workload_user_iam role is incorrect"
  }
  assert {
    condition     = google_project_iam_member.worker_pubsub_publisher_iam.role == "roles/pubsub.publisher"
    error_message = "worker_pubsub_publisher_iam role is incorrect"
  }
}

run "creates_members_with_member_and_project" {
  command = plan

  assert {
    condition     = google_project_iam_member.worker_storage_iam.member == "serviceAccount:user_provided_worker_sa_email"
    error_message = "worker_storage_iam member is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_secretmanager_iam.member == "serviceAccount:user_provided_worker_sa_email"
    error_message = "worker_secretmanager_iam member is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_logging_iam.member == "serviceAccount:user_provided_worker_sa_email"
    error_message = "worker_logging_iam member is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_monitoring_iam.member == "serviceAccount:user_provided_worker_sa_email"
    error_message = "worker_monitoring_iam member is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_instance_group_iam.member == "serviceAccount:user_provided_worker_sa_email"
    error_message = "worker_instance_group_iam member is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_workload_user_iam.member == "serviceAccount:user_provided_worker_sa_email"
    error_message = "worker_workload_user_iam member is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_pubsub_publisher_iam.member == "serviceAccount:user_provided_worker_sa_email"
    error_message = "worker_pubsub_publisher_iam member is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_storage_iam.project == "project_id"
    error_message = "worker_storage_iam project is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_secretmanager_iam.project == "project_id"
    error_message = "worker_secretmanager_iam project is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_logging_iam.project == "project_id"
    error_message = "worker_logging_iam project is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_monitoring_iam.project == "project_id"
    error_message = "worker_monitoring_iam project is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_instance_group_iam.project == "project_id"
    error_message = "worker_instance_group_iam project is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_workload_user_iam.project == "project_id"
    error_message = "worker_workload_user_iam project is not correct"
  }
  assert {
    condition     = google_project_iam_member.worker_pubsub_publisher_iam.project == "project_id"
    error_message = "worker_pubsub_publisher_iam project is not correct"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.worker_service_account_email == "user_provided_worker_sa_email"
    error_message = "worker_service_account_email is not user provided"
  }
}

run "generates_outputs_with_plan_created_email" {
  command = plan

  variables {
    user_provided_worker_sa_email = ""
  }

  assert {
    condition     = output.worker_service_account_email == "google_service_account_email"
    error_message = "worker_service_account_email is not correct"
  }
}
