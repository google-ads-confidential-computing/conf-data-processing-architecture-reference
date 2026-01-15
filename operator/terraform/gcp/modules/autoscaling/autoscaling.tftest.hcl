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
mock_provider "null" {
  override_during = plan
  mock_resource "null_resource" {
    defaults = {
      id = "1234567890"
    }
  }
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  project_id  = ""
  environment = "environment"
  workgroup   = null
  region      = "us"
  subnet_id   = ""
  worker_template = {
    id                = ""
    network_interface = []
  }
  min_worker_instances                = 0
  max_worker_instances                = 0
  jobqueue_subscription_name          = ""
  autoscaling_jobs_per_instance       = 0
  autoscaling_cloudfunction_memory_mb = 0
  worker_service_account              = "email"
  operator_package_bucket_name        = ""
  worker_scale_in_jar                 = ""
  worker_scale_in_zip                 = ""
  metadatadb_instance_name            = ""
  metadatadb_name                     = ""
  termination_wait_timeout_sec        = ""
  worker_scale_in_cron                = ""
  asg_instances_table_ttl_days        = 1
  use_java21_runtime                  = false
  alarms_enabled                      = false
  notification_channel_id             = ""
  alarm_eval_period_sec               = ""
  alarm_duration_sec                  = ""
  cloudfunction_error_threshold       = 0
  cloudfunction_max_execution_time_ms = 0
  cloudfunction_5xx_threshold         = 0
  max_vm_instances_ratio_threshold    = 0
  cloudfunction_alarm_eval_period_sec = ""
  cloudfunction_alarm_duration_sec    = ""
  auto_create_subnetworks             = false
}

run "creates_replace_trigger_with_empty_string" {
  command = plan

  variables {
    worker_template = {
      id = ""
      network_interface = [
      ]
    }
  }

  assert {
    condition = null_resource.worker_template_mig_replace_trigger.triggers == tomap({
      network = ""
    })
    error_message = "Wrong network trigger: ${null_resource.worker_template_mig_replace_trigger.triggers.network}"
  }
}

run "creates_replace_trigger_with_network" {
  command = plan

  variables {
    worker_template = {
      id = ""
      network_interface = [{
        network = "network"
        }
      ]
    }
  }

  assert {
    condition = null_resource.worker_template_mig_replace_trigger.triggers == tomap({
      network = "network"
    })
    error_message = "Wrong network trigger: ${null_resource.worker_template_mig_replace_trigger.triggers.network}"
  }
}

run "creates_replace_trigger_with_network_and_subnet_id" {
  command = plan

  variables {
    subnet_id = "subnet"
    worker_template = {
      id = ""
      network_interface = [{
        network = "network"
        }
      ]
    }
  }

  assert {
    condition = null_resource.worker_template_mig_replace_trigger.triggers == tomap({
      network = "network-subnet"
    })
    error_message = "Wrong network trigger: ${null_resource.worker_template_mig_replace_trigger.triggers.network}"
  }
}

run "creates_instance_group_manager" {
  command = plan

  providers = {
    google      = google
    google-beta = google-beta
    archive     = archive
    null        = null
  }

  assert {
    # 12d687 is 1234567 in Hex.
    condition     = google_compute_region_instance_group_manager.worker_instance_group.name == "environment-worker-mig-12d687"
    error_message = "Wrong group name"
  }
  assert {
    condition     = google_compute_region_instance_group_manager.worker_instance_group.base_instance_name == "environment-worker"
    error_message = "Wrong base name"
  }
}

run "creates_instance_group_manager_with_workgroup" {
  command = plan

  variables {
    workgroup = "workgroup"
  }

  assert {
    # 12d687 is 1234567 in Hex.
    condition     = google_compute_region_instance_group_manager.worker_instance_group.name == "environment-workgroup-worker-mig-12d687"
    error_message = "Wrong group name"
  }
  assert {
    condition     = google_compute_region_instance_group_manager.worker_instance_group.base_instance_name == "environment-workgroup-worker"
    error_message = "Wrong base name"
  }
}

run "autoscaler_uses_proper_naming_and_uses_proper_target" {
  command = plan

  variables {
    jobqueue_subscription_name = "jobqueue_sub_name"
  }

  assert {
    condition     = google_compute_region_autoscaler.worker_autoscaler.name == "environment-worker-autoscaler"
    error_message = "Wrong autoscaler name"
  }
  assert {
    condition     = google_compute_region_autoscaler.worker_autoscaler.target == "instance_group_manager_id"
    error_message = "Wrong target"
  }
  assert {
    condition     = google_compute_region_autoscaler.worker_autoscaler.autoscaling_policy[0].metric[0].filter == "resource.type = pubsub_subscription AND resource.label.subscription_id = jobqueue_sub_name"
    error_message = "Wrong metric filter"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    # 12d687 is 1234567 in Hex.
    condition     = google_compute_region_instance_group_manager.worker_instance_group.name == "environment-worker-mig-12d687"
    error_message = "Wrong group name"
  }
  assert {
    condition     = google_compute_region_autoscaler.worker_autoscaler.name == "environment-worker-autoscaler"
    error_message = "Wrong autoscaler name"
  }
  assert {
    condition     = google_cloudfunctions2_function.worker_scale_in_cloudfunction.name == "environment-us-worker-scale-in"
    error_message = "Wrong scale in name"
  }
  assert {
    condition     = google_compute_region_instance_group_manager.worker_instance_group.base_instance_name == "environment-worker"
    error_message = "Wrong base name"
  }
}
