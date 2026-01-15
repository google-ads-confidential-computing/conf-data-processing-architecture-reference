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
mock_provider "archive" {
  source          = "../../../../../tools/tftesting/tfmocks/archive/"
  override_during = plan
}
mock_provider "external" {
  source          = "../../../../../tools/tftesting/tfmocks/external/"
  override_during = plan
}
mock_provider "google" {
  source          = "../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}
mock_provider "google-beta" {
  source          = "../../../../../tools/tftesting/tfmocks/google-beta/"
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
  region      = ""
  worker_template = {
    id                = "id"
    network_interface = []
  }
  min_worker_instances             = 0
  max_worker_instances             = 0
  jobqueue_subscription_name       = ""
  autoscaling_jobs_per_instance    = 0
  alarms_enabled                   = false
  notification_channel_id          = ""
  alarm_eval_period_sec            = ""
  alarm_duration_sec               = ""
  max_vm_instances_ratio_threshold = 0
}

run "doesnt_create_replace_trigger" {
  command = plan

  assert {
    condition     = null_resource.worker_template_mig_replace_trigger.triggers.network == ""
    error_message = "Wrong network trigger"
  }
}

run "creates_replace_trigger_from_worker_template" {
  command = plan

  variables {
    worker_template = {
      id = "id"
      network_interface = [{
        network = "interface"
      }]
    }
  }

  assert {
    condition     = null_resource.worker_template_mig_replace_trigger.triggers.network == "interface"
    error_message = "Wrong network trigger"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.autoscaler_name == "environment-worker-autoscaler"
    error_message = "Wrong MIG name"
  }
  assert {
    # 12d687 is 1234567 in hex
    condition     = output.worker_managed_instance_group_name == "environment-worker-mig-12d687"
    error_message = "Wrong MIG name"
  }
}
