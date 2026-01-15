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

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  project                       = ""
  environment                   = ""
  service_name                  = "cloud_run_service_name"
  deletion_protection           = false
  region                        = "us"
  description                   = ""
  source_container_image_url    = ""
  environment_variables         = { "key1" : "val1", "key2" : "val2" }
  min_instance_count            = 0
  max_instance_count            = 0
  concurrency                   = 0
  cpu_idle                      = false
  startup_cpu_boost             = false
  cpu_count                     = 1
  memory_mb                     = 0
  timeout_seconds               = 0
  runtime_service_account_email = ""
  vpc_connector_id              = ""
  ingress_traffic_setting       = ""
  cloud_run_invoker_iam_members = []
  custom_audiences              = []
  binary_authorization          = {}
}

run "sets_envs" {
  command = plan

  assert {
    condition = jsonencode(google_cloud_run_v2_service.cloud_run_service.template[0].containers[0].env) == jsonencode([
      {
        name         = "key1"
        value        = "val1"
        value_source = []
      },
      {
        name         = "key2"
        value        = "val2"
        value_source = []
      }
    ])
    error_message = "env is wrong: ${jsonencode(google_cloud_run_v2_service.cloud_run_service.template[0].containers[0].env)}"
  }
}

run "doesnt_set_vpc_access" {
  command = plan

  assert {
    condition     = length(google_cloud_run_v2_service.cloud_run_service.template[0].vpc_access) == 0
    error_message = "vpc_access is set"
  }
}

run "sets_vpc_access" {
  command = plan

  variables {
    vpc_connector_id = "vpc_connector_id"
  }

  assert {
    condition = jsonencode(google_cloud_run_v2_service.cloud_run_service.template[0].vpc_access) == jsonencode([
      {
        connector          = "vpc_connector_id"
        egress             = "ALL_TRAFFIC"
        network_interfaces = []
      }
    ])
    error_message = "vpc_access is wrong ${jsonencode(google_cloud_run_v2_service.cloud_run_service.template[0].vpc_access)}"
  }
}

run "sets_binary_authorization" {
  command = plan

  variables {
    binary_authorization = {
      breakglass_justification = true
      use_default              = true
      policy                   = "policy"
    }
  }

  assert {
    condition = jsonencode(google_cloud_run_v2_service.cloud_run_service.binary_authorization) == jsonencode([{
      breakglass_justification = "true"
      use_default              = true
      policy                   = "policy"
    }])
    error_message = "binary_authorization is wrong: ${jsonencode(google_cloud_run_v2_service.cloud_run_service.binary_authorization)}"
  }
}

run "creates_members" {
  command = plan

  variables {
    cloud_run_invoker_iam_members = ["allUsers", "allAuthenticatedUsers"]
  }

  assert {
    condition = [for invoker in google_cloud_run_service_iam_member.cr_iam_invoker : {
      member = invoker.member
      role   = invoker.role
      }] == [
      {
        member = "allAuthenticatedUsers"
        role   = "roles/run.invoker"
      },
      {
        member = "allUsers"
        role   = "roles/run.invoker"
    }]
    error_message = "members are wrong: ${jsonencode([for invoker in google_cloud_run_service_iam_member.cr_iam_invoker : invoker])}"
  }
}

run "generates_outputs_with_plan" {
  command = plan

  assert {
    condition     = output.service_name == "cloud_run_service_name"
    error_message = "Service name is wrong"
  }
  assert {
    condition     = output.region == "us"
    error_message = "Region is wrong"
  }
  assert {
    condition     = output.cloud_run_url == "cloud_run_v2_uri"
    error_message = "Cloud run url is wrong"
  }
}
