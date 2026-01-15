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
  source          = "../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tools/tftesting/tfmocks/google-beta/"
  override_during = plan
}
mock_provider "null" {
  source          = "../../../../../tools/tftesting/tfmocks/null/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment                      = "environment"
  project_id                       = ""
  network                          = ""
  subnet_id                        = ""
  region                           = ""
  user_provided_collector_sa_email = ""
  collector_instance_type          = ""
  collector_startup_script         = "script"
  collector_service_port_name      = ""
  collector_service_port           = 0
  max_collector_instances          = 0
  min_collector_instances          = 0
  collector_min_instance_ready_sec = 0
  collector_cpu_utilization_target = 0
  collector_exceed_cpu_usage_alarm = {
    alignment_period_sec = 0
    auto_close_sec       = 0
    duration_sec         = 0
    enable_alarm         = false
    severity             = ""
    threshold            = 0
  }
  collector_exceed_memory_usage_alarm = {
    alignment_period_sec = 0
    auto_close_sec       = 0
    duration_sec         = 0
    enable_alarm         = false
    severity             = ""
    threshold            = 0
  }
  collector_export_error_alarm = {
    alignment_period_sec = 0
    auto_close_sec       = 0
    duration_sec         = 0
    enable_alarm         = false
    severity             = ""
    threshold            = 0
  }
  collector_startup_error_alarm = {
    alignment_period_sec = 0
    auto_close_sec       = 0
    duration_sec         = 0
    enable_alarm         = false
    severity             = ""
    threshold            = 0
  }
  collector_crash_error_alarm = {
    alignment_period_sec = 0
    auto_close_sec       = 0
    duration_sec         = 0
    enable_alarm         = false
    severity             = ""
    threshold            = 0
  }
  export_metric_to_collector_error_alarm = {
    alignment_period_sec = 0
    auto_close_sec       = 0
    duration_sec         = 0
    enable_alarm         = false
    severity             = ""
    threshold            = 0
  }
  collector_queue_size_alarm = {
    alignment_period_sec = 0
    auto_close_sec       = 0
    duration_sec         = 0
    enable_alarm         = false
    severity             = ""
    threshold            = 0
  }
  collector_send_metric_failure_rate_alarm = {
    alignment_period_sec = 0
    auto_close_sec       = 0
    duration_sec         = 0
    enable_alarm         = false
    severity             = ""
    threshold            = 0
  }
  collector_refuse_metric_rate_alarm = {
    alignment_period_sec = 0
    auto_close_sec       = 0
    duration_sec         = 0
    enable_alarm         = false
    severity             = ""
    threshold            = 0
  }
}

run "creates_server_replace_trigger" {
  command = plan

  assert {
    condition = null_resource.server_instance_replace_trigger.triggers == tomap({
      replace = sha256("script")
    })
    error_message = "Wrong trigger"
  }
}

run "creates_template_mig_trigger_with_empty_string" {
  command = plan

  assert {
    condition = null_resource.collector_template_mig_replace_trigger.triggers == tomap({
      network   = ""
      subnet_id = ""
    })
    error_message = "Wrong trigger"
  }
}

run "creates_template_mig_trigger_with_network_and_subnet" {
  command = plan

  variables {
    network    = "network"
    subnet_id  = "subnet_id"
    project_id = "project_id"
  }

  assert {
    condition = null_resource.collector_template_mig_replace_trigger.triggers == tomap({
      network   = "network"
      subnet_id = "subnet_id"
    })
    error_message = "Wrong trigger"
  }
}

run "creates_service_account" {
  command = plan

  assert {
    condition     = length(google_service_account.collector_service_account) == 1
    error_message = "Didn't create service account"
  }
}

run "doesnt_create_service_account" {
  command = plan

  variables {
    user_provided_collector_sa_email = "email"
  }

  assert {
    condition     = length(google_service_account.collector_service_account) == 0
    error_message = "Created service account"
  }
  assert {
    condition     = google_project_iam_member.collector_service_account_monitoring_viewer.member == "serviceAccount:email"
    error_message = "IAM member using wrong member"
  }
  assert {
    condition     = google_project_iam_member.collector_service_account_metric_writer_iam.member == "serviceAccount:email"
    error_message = "IAM member using wrong member"
  }
  assert {
    condition     = google_project_iam_member.collector_service_account_log_writer_iam.member == "serviceAccount:email"
    error_message = "IAM member using wrong member"
  }
}

run "iam_members_use_autocreated_account" {
  command = plan

  providers = {
    google      = google
    google-beta = google-beta
  }

  assert {
    condition     = google_project_iam_member.collector_service_account_monitoring_viewer.member == "serviceAccount:google_service_account_email"
    error_message = "IAM member using wrong member"
  }
  assert {
    condition     = google_project_iam_member.collector_service_account_metric_writer_iam.member == "serviceAccount:google_service_account_email"
    error_message = "IAM member using wrong member"
  }
  assert {
    condition     = google_project_iam_member.collector_service_account_log_writer_iam.member == "serviceAccount:google_service_account_email"
    error_message = "IAM member using wrong member"
  }
}

run "ig_manager_uses_proper_autohealing_and_template" {
  command = plan

  assert {
    condition     = google_compute_region_instance_group_manager.collector_instance.auto_healing_policies[0].health_check == "health_check_id"
    error_message = "Wrong auto healing"
  }
  assert {
    condition     = google_compute_region_instance_group_manager.collector_instance.version[0].instance_template == "instance_template_id"
    error_message = "Wrong template"
  }
}

run "creates_collector_autoscaler_with_proper_target" {
  command = plan

  assert {
    condition     = google_compute_region_autoscaler.collector_autoscaler.target == "region_instance_group_manager_id"
    error_message = "Wrong autoscaling target"
  }
}
