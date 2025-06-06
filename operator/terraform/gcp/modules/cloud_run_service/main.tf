# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

resource "google_cloud_run_v2_service" "cloud_run_service" {
  name        = var.service_name
  description = var.description
  location    = var.region
  ingress     = var.ingress_traffic_setting

  deletion_protection = var.deletion_protection

  template {
    scaling {
      min_instance_count = var.min_instance_count
      max_instance_count = var.max_instance_count
    }

    containers {
      image = var.source_container_image_url

      resources {
        limits = {
          cpu    = "${var.cpu_count}"
          memory = "${var.memory_mb}Mi"
        }
        cpu_idle          = var.cpu_idle
        startup_cpu_boost = var.startup_cpu_boost
      }

      dynamic "env" {
        for_each = var.environment_variables
        content {
          name  = env.key
          value = env.value
        }
      }
    }

    dynamic "vpc_access" {
      for_each = (var.vpc_connector_id == "" || var.vpc_connector_id == null) ? [] : [1]
      content {
        connector = var.vpc_connector_id
        egress    = "ALL_TRAFFIC"
      }
    }

    service_account                  = var.runtime_service_account_email
    max_instance_request_concurrency = var.concurrency
    timeout                          = "${var.timeout_seconds}s"

    labels = {
      environment = var.environment
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }

  dynamic "binary_authorization" {
    for_each = var.binary_authorization[*]
    content {
      breakglass_justification = binary_authorization.value.breakglass_justification
      use_default              = binary_authorization.value.use_default
      policy                   = binary_authorization.value.policy
    }
  }

  custom_audiences = var.custom_audiences
}

resource "google_cloud_run_service_iam_member" "cr_iam_invoker" {
  for_each = var.cloud_run_invoker_iam_members

  project  = var.project
  location = google_cloud_run_v2_service.cloud_run_service.location
  service  = google_cloud_run_v2_service.cloud_run_service.name

  role = "roles/run.invoker"

  member = each.value
}
