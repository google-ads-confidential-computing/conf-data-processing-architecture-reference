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

// The provider is not being properly mocked so we stub it out.
override_data {
  target = module.domain_a_records.data.google_dns_managed_zone.dns_zone
  values = {}
}
override_module {
  target = module.workload_identity_pool
  outputs = {
    wip_verified_service_account = "wip_verified"
    wip_allowed_service_account  = "wip_allowed"
  }
}

variables {
  project_id                                         = "project_id"
  wipp_project_id_override                           = ""
  wip_allowed_service_account_project_id_override    = ""
  environment                                        = "env"
  primary_region                                     = "us-central1"
  primary_region_zone                                = "us-central1-a"
  secondary_region                                   = ""
  secondary_region_zone                              = ""
  alarms_enabled                                     = false
  spanner_instance_config                            = ""
  spanner_processing_units                           = 0
  spanner_staleness_read_sec                         = ""
  spanner_custom_configuration_name                  = ""
  spanner_custom_configuration_display_name          = ""
  spanner_custom_configuration_base_config           = ""
  spanner_custom_configuration_read_replica_location = ""
  parent_domain_name                                 = "domain"
  parent_domain_name_project                         = "domain_project"
  service_subdomain_suffix                           = ""

  key_storage_service_subdomain                        = "subdomain"
  private_key_service_additional_regions               = []
  private_key_service_subdomain                        = ""
  private_key_service_container_image_url              = ""
  private_key_service_addon_container_image_url        = ""
  private_key_service_addon_alert_severity_overrides   = {}
  private_key_service_cloud_run_cpu_count              = 1
  private_key_service_cloud_run_concurrency            = 0
  private_key_service_cloud_run_memory_mb              = 0
  private_key_service_cloud_run_min_instances          = 0
  private_key_service_cloud_run_max_instances          = 0
  private_key_service_execution_environment            = ""
  private_key_service_addon_cloud_run_config           = {}
  enable_private_key_service_cache                     = false
  private_key_service_cache_refresh_in_minutes         = 0
  private_key_service_enable_canary_feature            = false
  private_key_service_stable_revision                  = null
  private_key_service_canary_revision                  = null
  private_key_service_canary_traffic_percentage        = 0
  key_storage_service_memory_mb                        = 0
  key_storage_service_min_instances                    = 0
  key_storage_service_max_instances                    = 0
  key_storage_service_execution_environment            = ""
  location_new_key_ring                                = ""
  key_storage_service_container_image_url              = ""
  enable_attestation                                   = false
  assertion_tee_swname                                 = ""
  assertion_tee_support_attributes                     = []
  assertion_tee_container_image_reference_list         = []
  assertion_tee_container_image_hash_list              = []
  key_storage_service_alarm_eval_period_sec            = 60
  key_storage_service_cloud_run_max_execution_time_max = 0
  key_storage_service_cloud_run_5xx_threshold          = 0
  key_storage_service_lb_5xx_threshold                 = 0
  key_storage_service_lb_max_95_percent_latency_ms     = 50
  key_storage_service_lb_max_99_percent_latency_ms     = 100
  key_storage_service_alarm_duration_sec               = 0

  private_key_service_alarm_eval_period_sec                      = 60
  private_key_service_cloud_run_max_execution_time_max           = 0
  private_key_service_cloud_run_5xx_threshold                    = 0
  private_key_service_cloud_run_memory_usage_important_threshold = 0.6
  private_key_service_cloud_run_memory_usage_urgent_threshold    = 0.9
  private_key_service_cloud_run_cpu_usage_important_threshold    = 0.6
  private_key_service_cloud_run_cpu_usage_urgent_threshold       = 0.9
  private_key_service_cloud_run_timeout_sec                      = 60
  private_key_service_lb_max_latency_ms                          = 0
  private_key_service_lb_alert_5xx_error_ratio                   = 0.1
  private_key_service_lb_max_95_percent_latency_ms               = 50
  private_key_service_lb_max_99_percent_latency_ms               = 100
  private_key_service_alarm_duration_sec                         = 0
  get_encrypted_private_key_general_error_threshold              = 0
  get_encrypted_private_key_missing_key_threshold                = 0
  private_key_service_exception_alert_threshold                  = 0
  private_key_service_config_read_alert_threshold                = 0
  alert_severity_overrides                                       = {}

  quota_alert_duration_sec        = 60
  quota_alert_eval_period_sec     = 180
  quota_alert_max_over_minutes    = 5
  quota_alert_threshold_important = 0.6
  quota_alert_threshold_urgent    = 0.8

  private_key_service_load_balancer_allowed_paths       = ["/*"]
  private_key_service_addon_load_balancer_allowed_paths = ["/*"]

  private_key_service_load_balancer_protocol                                     = "HTTP"
  private_key_service_lb_outlier_detection_enabled                               = false
  private_key_service_lb_outlier_detection_consecutive_errors                    = 0
  private_key_service_lb_outlier_detection_interval_seconds                      = 0
  private_key_service_lb_outlier_detection_base_ejection_time_seconds            = 0
  private_key_service_lb_outlier_detection_max_ejection_percent                  = 0
  private_key_service_lb_outlier_detection_enforcing_consecutive_errors          = 0
  private_key_service_lb_outlier_detection_consecutive_gateway_failure           = 0
  private_key_service_lb_outlier_detection_enforcing_consecutive_gateway_failure = 0

  key_storage_service_load_balancer_allowed_paths = ["/*"]

  key_storage_service_lb_outlier_detection_enabled                               = false
  key_storage_service_lb_outlier_detection_consecutive_errors                    = 0
  key_storage_service_lb_outlier_detection_interval_seconds                      = 0
  key_storage_service_lb_outlier_detection_base_ejection_time_seconds            = 0
  key_storage_service_lb_outlier_detection_max_ejection_percent                  = 0
  key_storage_service_lb_outlier_detection_enforcing_consecutive_errors          = 0
  key_storage_service_lb_outlier_detection_consecutive_gateway_failure           = 0
  key_storage_service_lb_outlier_detection_enforcing_consecutive_gateway_failure = 0

  key_sets_config = {
    key_sets = []
  }
  populate_migration_key_data = false
  key_sets_vending_config = {
    allowed_migrators = []
  }
  migration_peer_coordinator_kms_key_base_uri = ""
  key_migration_tool_container_image_url      = ""
  key_migration_tool_cpu_count                = 1
  key_migration_tool_memory_mb                = 0
  key_migration_tool_max_retries              = 0
  key_migration_tool_task_timeout_seconds     = 1
  key_migration_tool_migrator_mode            = ""
  key_migration_tool_dry_run                  = false
  run_key_migration_tool                      = false
  key_migration_tool_key_sets = {
    allowed_keysets = []
  }
  private_key_service_enable_revision_pinning = false
  private_key_service_stable_revisions        = {}

  key_storage_service_alarms       = {}
  private_key_service_alarms       = {}
  private_key_service_addon_alarms = {}
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".

run "creates_key_set_pool_for_each_operator" {
  command = plan

  variables {
    allowed_operators = {
      op1 = {
        image_digests           = []
        image_references        = []
        image_signature_key_ids = []
        service_accounts        = []
        key_sets                = []
      }
      op2 = {
        image_digests           = []
        image_references        = []
        image_signature_key_ids = []
        service_accounts        = []
        key_sets                = []
      }
    }
  }

  assert {
    condition     = length(module.key_set_acl_kek_pool) == 2
    error_message = "Wrong pools"
  }
}

run "doesnt_create_private_key_service_addon" {
  command = plan

  assert {
    condition     = length(module.private_key_service_addon) == 0
    error_message = "Created addon"
  }
}

run "creates_private_key_service_addon" {
  command = plan

  variables {
    private_key_service_addon_container_image_url = "url"
  }

  assert {
    condition     = length(module.private_key_service_addon) == 1
    error_message = "Didn't create addon"
  }
}

run "doesnt_create_key_sets_parameter_config" {
  command = plan

  assert {
    condition     = length(module.key_sets_parameter_config) == 0
    error_message = "Created parameter config"
  }
}


run "doesnt_create_key_migration_tool" {
  command = plan

  assert {
    condition     = length(module.key_migration_tool) == 0
    error_message = "Created tool"
  }
}

run "creates_key_migration_tool" {
  command = plan

  variables {
    run_key_migration_tool = true
    # Must provide a valid config for cleanup, generation, or migration
    key_migration_tool_container_image_url = "image"
    key_migration_tool_migrator_mode       = "cleanup"
    key_sets_vending_config = {
      allowed_migrators = []
    }
    key_migration_tool_key_sets = {
      allowed_keysets = ["set1"]
    }
  }

  assert {
    condition     = length(module.key_migration_tool) == 1
    error_message = "Didn't create tool"
  }
}

run "creates_crypto_key_members" {
  command = plan

  variables {
    key_sets_config = {
      key_sets = [
        {
          name             = "set1"
          count            = 1
          tink_template    = ""
          ttl_in_days      = 1
          validity_in_days = 1
        }
      ]
    }
  }

  override_module {
    target = module.key_management_service
    outputs = {
      kms_key_ring_id = "ID"
      kms_key_ids     = { "key" = "key1" }
    }
  }

  assert {
    condition     = [for _, member in google_kms_crypto_key_iam_member.key_set_wip_sa : member.member] == ["serviceAccount:wip_verified"]
    error_message = "Wrong member"
  }
}

run "fails_migration_config_if_null_new_key_ring_location" {
  command = plan

  variables {
    populate_migration_key_data = true
    location_new_key_ring       = null
  }

  // Must override this module as it tries to be evaluated before the
  // null_resource
  override_module {
    target = module.key_management_service
    outputs = {
      kms_key_ring_id = "ID"
      kms_key_ids     = {}
    }
  }

  expect_failures = [null_resource.has_valid_migration_configuration]
}

run "fails_key_migration_tool_for_generate_mode" {
  command = plan

  variables {
    run_key_migration_tool                 = true
    populate_migration_key_data            = false
    key_migration_tool_container_image_url = "url"
    key_migration_tool_migrator_mode       = "generate"
    key_sets_vending_config = {
      allowed_migrators = []
    }
    key_migration_tool_key_sets = { allowed_keysets = ["set1"] }
  }

  expect_failures = [null_resource.can_safely_run_key_migration_tool]
}

run "fails_key_migration_tool_for_migrate_mode" {
  command = plan

  variables {
    run_key_migration_tool                 = true
    populate_migration_key_data            = true
    key_migration_tool_container_image_url = "url"
    key_migration_tool_migrator_mode       = "migrate"
    key_sets_vending_config = {
      allowed_migrators = []
    }
    key_migration_tool_key_sets = { allowed_keysets = ["set1"] }
  }

  expect_failures = [null_resource.can_safely_run_key_migration_tool]
}

run "fails_key_migration_tool_for_cleanup_mode" {
  command = plan

  variables {
    run_key_migration_tool                 = true
    populate_migration_key_data            = false
    key_migration_tool_container_image_url = "url"
    key_migration_tool_migrator_mode       = "cleanup"
    key_sets_vending_config = {
      allowed_migrators = ["migrator1"]
    }
    key_migration_tool_key_sets = { allowed_keysets = ["set1"] }
  }

  expect_failures = [null_resource.can_safely_run_key_migration_tool]
}

run "generates_outputs_with_plan" {
  command = plan

  variables {
    allowed_operators = {
      op1 = {
        image_digests           = []
        image_references        = []
        image_signature_key_ids = []
        service_accounts        = []
        key_sets                = []
      }
      op2 = {
        image_digests           = []
        image_references        = []
        image_signature_key_ids = []
        service_accounts        = []
        key_sets                = []
      }
    }

    private_key_service_subdomain               = "private_subdomain"
    migration_peer_coordinator_kms_key_base_uri = "peer_coordinator_base_uri"
  }

  override_module {
    target = module.key_management_service
    outputs = {
      kms_key_ring_id = "ring_id"
      kms_key_ids     = {}
    }
  }

  assert {
    condition     = output.private_key_service_loadbalancer_ip == "cloud_run_fe_ip_address"
    error_message = "Wrong IP"
  }
  assert {
    condition     = output.private_key_service_loadbalancer_ip_additional == "cloud_run_fe_ip_address"
    error_message = "Wrong IP"
  }
  assert {
    condition     = output.private_key_base_url == "https://private_subdomain.domain"
    error_message = "Wrong URL"
  }
  assert {
    condition     = output.private_key_base_url_additional == "https://private_subdomain-pre-env.domain"
    error_message = "Wrong URL"
  }
  assert {
    condition     = output.key_storage_cloud_run_url == "cloud_run_v2_uri"
    error_message = "Wrong URL"
  }
  assert {
    condition     = output.key_storage_service_loadbalancer_ip == "cloud_run_fe_ip_address"
    error_message = "Wrong IP"
  }
  assert {
    condition     = output.key_storage_base_url == "https://subdomain.domain"
    error_message = "Wrong URL"
  }
  assert {
    condition     = output.workload_identity_pool_provider_name == null
    error_message = "Wrong WIPP name"
  }
  assert {
    condition     = output.wip_allowed_service_account == "wip_allowed"
    error_message = "Wrong account"
  }
  assert {
    condition     = output.wip_verified_service_account == "wip_verified"
    error_message = "Wrong account"
  }
  assert {
    condition     = length(output.allowed_operators_wipp_names) == 2
    error_message = "Wrong WIPPs"
  }
  assert {
    condition     = output.kms_key_base_uri == "gcp-kms://ring_id/cryptoKeys/env_$setName$_kms_key"
    error_message = "Wrong URL"
  }
  assert {
    condition     = output.migration_kms_key_base_uri == "gcp-kms://ring_id/cryptoKeys/env_$setName$_kms_key"
    error_message = "Wrong URL"
  }
}

run "propagates_structured_alarm_configs_to_private_ks_and_key_storage_service" {
  command = plan

  variables {
    alarms_enabled = true
    private_key_service_alarms = {
      cloud_run_execution_time_alarm_by_region = {
        "us-central1" = {
          threshold    = 1600
          duration_sec = 240
        }
      }
      config_read_error_alarm = {
        threshold    = 6
        duration_sec = 300
      }
      load_balancer_95_percent_latency_alarm = {
        threshold    = 450
        duration_sec = 600
      }
    }
    key_storage_service_alarms = {
      cloud_run_execution_time_alarm = {
        threshold    = 2500
        duration_sec = 180
      }
      load_balancer_99_percent_latency_alarm = {
        threshold    = 950
        duration_sec = 300
      }
    }
  }

  override_module {
    target = module.key_management_service
    outputs = {
      kms_key_ring_id = "ring_id"
      kms_key_ids     = {}
    }
  }

  assert {
    condition = (
      module.private_key_service.cloud_run_execution_time_alarms_by_region["us-central1"].threshold == 1600 &&
      module.private_key_service.cloud_run_execution_time_alarms_by_region["us-central1"].auto_close_sec == 1800
    )
    error_message = <<-EOT
      Expected private_key_service per-region Cloud Run execution time threshold
      override (1600) and resolved defaults to propagate.
    EOT
  }

  assert {
    condition = (
      module.private_key_service.config_read_error_alarm.threshold == 6 &&
      module.private_key_service.config_read_error_alarm.auto_close_sec == 1800
    )
    error_message = <<-EOT
      Expected private_key_service_alarms.config_read_error_alarm threshold
      override (6) and resolved defaults to propagate.
    EOT
  }

  assert {
    condition = (
      module.private_key_service.load_balancer_95_percent_latency_alarm.threshold == 450 &&
      module.private_key_service.load_balancer_95_percent_latency_alarm.auto_close_sec == 1800
    )
    error_message = <<-EOT
      Expected private_key_service_alarms.load_balancer_95_percent_latency_alarm
      threshold override (450) and resolved defaults to propagate.
    EOT
  }

  assert {
    condition = (
      module.keystorageservice.cloud_run_execution_time_alarm.threshold == 2500 &&
      module.keystorageservice.cloud_run_execution_time_alarm.auto_close_sec == 1800
    )
    error_message = <<-EOT
      Expected key_storage_service_alarms.cloud_run_execution_time_alarm threshold
      override (2500) and resolved defaults to propagate.
    EOT
  }

  assert {
    condition = (
      module.keystorageservice.load_balancer_99_percent_latency_alarm.threshold == 950 &&
      module.keystorageservice.load_balancer_99_percent_latency_alarm.auto_close_sec == 1800
    )
    error_message = <<-EOT
      Expected key_storage_service_alarms.load_balancer_99_percent_latency_alarm
      threshold override (950) and resolved defaults to propagate.
    EOT
  }
}

run "overrides_private_key_service_addon_alarms_independently_of_primary" {
  command = plan

  variables {
    alarms_enabled                                   = true
    secondary_region                                 = "us-east1"
    secondary_region_zone                            = "us-east1-b"
    private_key_service_addon_container_image_url    = "url"
    private_key_service_alarm_duration_sec           = 600
    private_key_service_lb_max_95_percent_latency_ms = 450

    private_key_service_alarms = {
      cloud_run_execution_time_alarm_by_region = {
        "us-central1" = {
          threshold    = 1600
          duration_sec = 240
        }
      }
      config_read_error_alarm = {
        threshold    = 6
        duration_sec = 300
      }
      get_encrypted_private_key_error_alarm = {
        threshold = 2
      }
      get_encrypted_private_key_missing_key_alarm = {
        threshold    = 50
        duration_sec = 240
      }
      load_balancer_95_percent_latency_alarm = {
        threshold = 200
      }
    }

    private_key_service_addon_alarms = {
      cloud_run_execution_time_alarm = {
        threshold    = 2000
        duration_sec = 180
      }
      cloud_run_execution_time_alarm_by_region = {
        "us-central1" = {
          threshold = 2500
        }
      }
      config_read_error_alarm = {
        threshold = 9
      }
      get_encrypted_private_key_missing_key_alarm = {
        enable_alarm = false
        threshold    = 100
      }
      load_balancer_99_percent_latency_alarm = {
        enable_alarm = false
      }
    }
  }

  override_module {
    target = module.key_management_service
    outputs = {
      kms_key_ring_id = "ring_id"
      kms_key_ids     = {}
    }
  }

  assert {
    condition = (
      module.private_key_service.cloud_run_execution_time_alarms_by_region["us-central1"].threshold == 1600 &&
      module.private_key_service.cloud_run_execution_time_alarms_by_region["us-central1"].duration_sec == 240 &&
      module.private_key_service_addon[0].cloud_run_execution_time_alarms_by_region["us-central1"].threshold == 2500 &&
      module.private_key_service_addon[0].cloud_run_execution_time_alarms_by_region["us-central1"].duration_sec == 180 &&
      module.private_key_service_addon[0].cloud_run_execution_time_alarms_by_region["us-east1"].threshold == 2000 &&
      module.private_key_service_addon[0].cloud_run_execution_time_alarms_by_region["us-east1"].duration_sec == 180
    )
    error_message = <<-EOT
      Expected addon regional Cloud Run execution time override (2500) to take
      precedence over addon non-regional override (2000, 180s) in us-central1,
      while us-east1 uses the addon non-regional override independently of
      primary.
    EOT
  }

  assert {
    condition = (
      module.private_key_service.config_read_error_alarm.threshold == 6 &&
      module.private_key_service.config_read_error_alarm.duration_sec == 300 &&
      module.private_key_service_addon[0].config_read_error_alarm.threshold == 9 &&
      module.private_key_service_addon[0].config_read_error_alarm.duration_sec == 600
    )
    error_message = <<-EOT
      Expected addon config_read_error_alarm threshold override (9) to apply
      while falling back to flat baseline duration_sec (600) rather than primary
      alarm duration_sec (300).
    EOT
  }

  assert {
    condition = (
      module.private_key_service.get_encrypted_private_key_error_alarm.threshold == 2 &&
      module.private_key_service.get_encrypted_private_key_missing_key_alarm.threshold == 50 &&
      module.private_key_service.get_encrypted_private_key_missing_key_alarm.duration_sec == 240 &&
      module.private_key_service.get_encrypted_private_key_missing_key_alarm.enable_alarm == true &&
      module.private_key_service_addon[0].get_encrypted_private_key_error_alarm.threshold == 0 &&
      module.private_key_service_addon[0].get_encrypted_private_key_missing_key_alarm.enable_alarm == false &&
      module.private_key_service_addon[0].get_encrypted_private_key_missing_key_alarm.threshold == 100 &&
      module.private_key_service_addon[0].get_encrypted_private_key_missing_key_alarm.duration_sec == 600
    )
    error_message = <<-EOT
      Expected get_encrypted_private_key_missing_key_alarm and
      get_encrypted_private_key_error_alarm to resolve independently across
      primary and addon Private Key Service modules.
    EOT
  }

  assert {
    condition = (
      module.private_key_service.load_balancer_99_percent_latency_alarm.enable_alarm == true &&
      module.private_key_service_addon[0].load_balancer_99_percent_latency_alarm.enable_alarm == false
    )
    error_message = <<-EOT
      Expected private_key_service_addon load_balancer_99_percent_latency_alarm
      override (enable_alarm = false) to apply independently of primary.
    EOT
  }

  assert {
    condition = (
      module.private_key_service.load_balancer_95_percent_latency_alarm.threshold == 200 &&
      module.private_key_service_addon[0].load_balancer_95_percent_latency_alarm.threshold == 450
    )
    error_message = <<-EOT
      Expected unset addon load_balancer_95_percent_latency_alarm to fall back
      to flat baseline private_key_service_lb_max_95_percent_latency_ms (450)
      independently of primary override (200).
    EOT
  }
}

run "disables_private_key_service_addon_alarms_when_addon_alarms_enabled_is_false" {
  command = plan

  variables {
    alarms_enabled                                = true
    private_key_service_addon_container_image_url = "url"

    private_key_service_alarms = {
      config_read_error_alarm = {
        enable_alarm = true
        threshold    = 5
      }
      cloud_run_execution_time_alarm_by_region = {
        "us-central1" = {
          enable_alarm = true
          threshold    = 1500
        }
      }
      load_balancer_95_percent_latency_alarm = {
        enable_alarm = true
        threshold    = 400
      }
    }

    private_key_service_addon_alarms = {
      alarms_enabled = false
    }
  }

  override_module {
    target = module.key_management_service
    outputs = {
      kms_key_ring_id = "ring_id"
      kms_key_ids     = {}
    }
  }

  assert {
    condition = (
      module.private_key_service.config_read_error_alarm.enable_alarm == true &&
      module.private_key_service.cloud_run_execution_time_alarms_by_region["us-central1"].enable_alarm == true &&
      module.private_key_service.load_balancer_95_percent_latency_alarm.enable_alarm == true &&
      module.private_key_service_addon[0].config_read_error_alarm.enable_alarm == false &&
      module.private_key_service_addon[0].cloud_run_execution_time_alarms_by_region["us-central1"].enable_alarm == false &&
      module.private_key_service_addon[0].load_balancer_95_percent_latency_alarm.enable_alarm == false
    )
    error_message = <<-EOT
      Expected private_key_service_addon_alarms.alarms_enabled = false to disable
      addon alarms across log-based, regional Cloud Run, and Load Balancer alerts
      even when primary_alarm.enable_alarm is true.
    EOT
  }
}

run "overrides_private_key_service_addon_cloud_run_config_independently_of_primary" {
  command = plan

  variables {
    private_key_service_addon_container_image_url = "addon_image"
    private_key_service_cloud_run_cpu_count       = 8
    private_key_service_cloud_run_memory_mb       = 8192
    private_key_service_cloud_run_concurrency     = 50
    private_key_service_cloud_run_min_instances   = 5
    private_key_service_cloud_run_max_instances   = 100
    private_key_service_cloud_run_timeout_sec     = 120

    private_key_service_addon_cloud_run_config = {
      concurrency        = 250
      min_instance_count = 2
      max_instance_count = 20
      cpu_count          = 4
    }
  }

  override_module {
    target = module.key_management_service
    outputs = {
      kms_key_ring_id = "ring_id"
      kms_key_ids     = {}
    }
  }

  assert {
    condition = module.private_key_service.cloud_run_configs_by_region["us-central1"] == {
      concurrency        = 50
      max_instance_count = 100
      min_instance_count = 5
      cpu_count          = 8
      memory_mb          = 8192
      timeout_sec        = 120
    }
    error_message = "Primary private_key_service should retain its original Cloud Run configuration."
  }

  assert {
    condition = module.private_key_service_addon[0].cloud_run_configs_by_region["us-central1"] == {
      concurrency        = 250
      max_instance_count = 20
      min_instance_count = 2
      cpu_count          = 4
      memory_mb          = 8192
      timeout_sec        = 120
    }
    error_message = <<-EOT
      Addon private_key_service_addon should apply overridden Cloud Run settings
      and inherit unset fields from primary.
    EOT
  }
}
