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

##### WORKER METRICS #########

locals {
  worker_kms_decrypt_log_prefix = "cloud-kms-split-key-aead-decrypt-"
  // Map from metric name to config
  worker_count_metrics = {
    "ks-client-splitkey-decrypt-success-with-retries" = {
      description = "Occurrences of calls that resulted in a SUCCESS after retries."
      text_filter = "${local.worker_kms_decrypt_log_prefix}RETRY_SUCCESS"
    }
    "ks-client-splitkey-decrypt-failure-with-retries" = {
      description = "Occurrences of calls that resulted in a FAILURE after retries."
      text_filter = "${local.worker_kms_decrypt_log_prefix}RETRY_FAILURE"
    }
    "ks-client-splitkey-decrypt-failure" = {
      description = "Occurrences of calls that resulted in a FAILURE without retries."
      text_filter = "${local.worker_kms_decrypt_log_prefix}FAILURE"
    }
    "ks-client-splitkey-decrypt-call" = {
      description = "Occurrences of calls to decrypt."
      text_filter = "${local.worker_kms_decrypt_log_prefix}CALL"
    }
  }

  worker_linear_distribution_metrics = {
    "ks-client-splitkey-decrypt-retries-to-success-count" = {
      description = "Number of retries needed for a success."
      text_filter = "${local.worker_kms_decrypt_log_prefix}RETRY_SUCCESS_COUNT"
      distribution = {
        num_finite_buckets = 10
        width              = 1
        offset             = 0
      }
    }
  }
}

resource "google_logging_metric" "worker_count_metrics" {
  for_each = local.worker_count_metrics

  name        = var.workgroup == null ? "${var.environment}/${each.key}" : "${var.environment}/${var.workgroup}/${each.key}"
  description = each.value.description
  filter      = "resource.type=\"gce_instance\" AND jsonPayload._HOSTNAME=~\"${var.vm_instance_group_base_instance_name}\" AND jsonPayload.MESSAGE=~\"${each.value.text_filter}\""

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
  }
}

resource "google_logging_metric" "worker_linear_distribution_metrics" {
  for_each = local.worker_linear_distribution_metrics

  name        = var.workgroup == null ? "${var.environment}/${each.key}" : "${var.environment}/${var.workgroup}/${each.key}"
  description = each.value.description
  filter      = "resource.type=\"gce_instance\" AND jsonPayload._HOSTNAME=~\"${var.vm_instance_group_base_instance_name}\" AND jsonPayload.MESSAGE=~\"${each.value.text_filter}\""

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "DISTRIBUTION"
  }

  bucket_options {
    linear_buckets {
      num_finite_buckets = each.value.distribution.num_finite_buckets
      width              = each.value.distribution.width
      offset             = each.value.distribution.offset
    }
  }

  value_extractor = "REGEXP_EXTRACT(jsonPayload.MESSAGE,\"${each.value.text_filter}:(\\\\d+)\")"
}

##############################
