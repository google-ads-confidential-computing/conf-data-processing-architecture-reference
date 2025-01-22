# Copyright 2024 Google LLC
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


resource "google_monitoring_alert_policy" "key_db_spanner_alert" {
  display_name = "${var.environment} Key DB Cpu Utiliziation Too High Alert "
  project      = var.project_id

  # Required but not relevant since there is only 1 condition
  combiner = "OR"

  documentation {
    content   = "TODO(b/380281539): add link to oncall playbook"
    mime_type = "text/markdown"
  }

  conditions {
    display_name = "Alert on key db using too much memory"

    condition_threshold {
      # The amount of time that a time series must violate the threshold to be considered failing.
      duration   = "900s" # 15m
      comparison = "COMPARISON_GT"

      threshold_value = 0.8
      filter          = "resource.type = \"spanner_instance\" AND metric.type = \"spanner.googleapis.com/instance/cpu/utilization\" AND metric.labels.database = \"${google_spanner_database.keydb.name}\""

      aggregations {
        alignment_period     = "300s" # 5m
        per_series_aligner   = "ALIGN_MAX"
        cross_series_reducer = "REDUCE_NONE"
      }
    }
  }

  alert_strategy {
    # 30 minutes.
    auto_close = "1800s"
  }

  user_labels = {
    severity = "important"
  }
}
