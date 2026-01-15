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

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  environment                                         = "environments"
  workgroup                                           = null
  vm_instance_group_name                              = ""
  vm_instance_group_base_instance_name                = ""
  enable_new_metrics                                  = false
  enable_legacy_metrics                               = false
  legacy_jobclient_job_validation_failure_metric_type = ""
  legacy_jobclient_error_metric_type                  = ""
  legacy_worker_error_metric_type                     = ""
  new_jobclient_job_validation_failure_metric_type    = ""
  new_jobclient_error_metric_type                     = ""
  new_worker_error_metric_type                        = ""
}

run "doesnt_create_dashboard_for_legacy_metrics" {
  command = plan

  assert {
    condition     = length(google_monitoring_dashboard.worker_custom_metrics_dashboard) == 0
    error_message = "Created dashboard"
  }
}

run "creates_dashboard_for_legacy_metrics" {
  command = plan

  variables {
    enable_legacy_metrics = true
  }

  assert {
    condition     = length(google_monitoring_dashboard.worker_custom_metrics_dashboard) == 1
    error_message = "Didn't create dashboard"
  }
}

run "creates_metrics_for_each_worker_count_no_workgroup" {
  command = plan

  assert {
    condition = toset([for metric in google_logging_metric.worker_count_metrics : metric.name]) == toset([
      "environments/ks-client-splitkey-decrypt-success-with-retries",
      "environments/ks-client-splitkey-decrypt-failure-with-retries",
      "environments/ks-client-splitkey-decrypt-failure",
      "environments/ks-client-splitkey-decrypt-call"
    ])
    error_message = "Wrong metric_names"
  }
}

run "creates_metrics_for_each_worker_count_with_workgroup" {
  command = plan

  variables {
    workgroup = "workgroup"
  }

  assert {
    condition = toset([for metric in google_logging_metric.worker_count_metrics : metric.name]) == toset([
      "environments/workgroup/ks-client-splitkey-decrypt-success-with-retries",
      "environments/workgroup/ks-client-splitkey-decrypt-failure-with-retries",
      "environments/workgroup/ks-client-splitkey-decrypt-failure",
      "environments/workgroup/ks-client-splitkey-decrypt-call"
    ])
    error_message = "Wrong metric_names"
  }
}

run "creates_logging_metric_for_linear_no_workgroup" {
  command = plan

  assert {
    condition = toset([for metric in google_logging_metric.worker_linear_distribution_metrics : metric.name]) == toset([
      "environments/ks-client-splitkey-decrypt-retries-to-success-count"
    ])
    error_message = "Wrong metric_names"
  }
  assert {
    condition = google_logging_metric.worker_linear_distribution_metrics["ks-client-splitkey-decrypt-retries-to-success-count"].bucket_options[0].linear_buckets[0] == {
      num_finite_buckets = 10
      width              = 1
      offset             = 0
    }
    error_message = "Wrong buckets"
  }
}

run "creates_logging_metric_for_linear_with_workgroup" {
  command = plan

  variables {
    workgroup = "workgroup"
  }

  assert {
    condition = toset([for metric in google_logging_metric.worker_linear_distribution_metrics : metric.name]) == toset([
      "environments/workgroup/ks-client-splitkey-decrypt-retries-to-success-count"
    ])
    error_message = "Wrong metric_names"
  }
}

run "doesnt_create_new_dashboard" {
  command = plan

  assert {
    condition     = length(google_monitoring_dashboard.worker_custom_metrics_new_dashboard) == 0
    error_message = "Created dashboard"
  }
}

run "creates_new_dashboard" {
  command = plan

  variables {
    enable_new_metrics = true
  }

  assert {
    condition     = length(google_monitoring_dashboard.worker_custom_metrics_new_dashboard) == 1
    error_message = "Didn't create dashboard"
  }
}
