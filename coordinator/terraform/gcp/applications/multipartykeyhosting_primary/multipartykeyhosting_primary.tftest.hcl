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

mock_provider "google-beta" {
  source          = "../../../../../tools/tftesting/tfmocks/google-beta/"
  override_during = plan
}
mock_provider "google" {
  source          = "../../../../../tools/tftesting/tfmocks/google/"
  override_during = plan
}

// The provider is not being properly mocked so we stub it out.
override_data {
  target = module.domain_a_records.data.google_dns_managed_zone.dns_zone
  values = {}
}

variables {
  project_id = "project_id"
  # Must be shorter due to length restrictions on subsequent value
  environment                                                   = "env"
  primary_region                                                = "us-central1"
  primary_region_zone                                           = "us-central1-a"
  secondary_region                                              = ""
  secondary_region_zone                                         = ""
  alarms_enabled                                                = false
  spanner_instance_config                                       = ""
  spanner_processing_units                                      = 0
  spanner_staleness_read_sec                                    = ""
  spanner_custom_configuration_name                             = ""
  spanner_custom_configuration_display_name                     = ""
  spanner_custom_configuration_base_config                      = ""
  spanner_custom_configuration_read_replica_location            = ""
  key_generation_region                                         = ""
  key_generation_service_container_image_url                    = ""
  key_generation_count                                          = 1
  key_generation_validity_in_days                               = 1
  key_generation_ttl_in_days                                    = 1
  key_generation_max_days_ahead                                 = 2
  key_generation_cron_schedule                                  = ""
  key_generation_cron_time_zone                                 = ""
  instance_disk_image                                           = ""
  key_generation_logging_enabled                                = false
  key_generation_monitoring_enabled                             = false
  key_generation_tee_restart_policy                             = ""
  key_generation_tee_allowed_sa                                 = ""
  key_generation_undelivered_messages_threshold                 = 0
  key_generation_error_threshold                                = 0
  key_generation_alignment_period                               = 1
  key_generation_single_keyset_alignment_periods                = 0
  key_generation_create_alert_alignment_periods                 = 0
  key_storage_service_base_url                                  = ""
  key_storage_service_cloudfunction_url                         = ""
  peer_coordinator_wip_provider                                 = ""
  peer_coordinator_service_account                              = ""
  key_id_type                                                   = ""
  location_new_key_ring                                         = ""
  parent_domain_name                                            = "domain"
  parent_domain_name_project                                    = "domain_project"
  service_subdomain_suffix                                      = ""
  public_key_service_subdomain                                  = ""
  public_key_service_container_image_url                        = ""
  public_key_service_load_balancing_scheme                      = ""
  public_key_service_cr_regions                                 = []
  public_key_service_cloud_run_cpu_count                        = 1
  public_key_service_max_cloud_run_concurrency                  = 0
  public_key_service_memory_mb                                  = 0
  public_key_service_min_instances                              = 0
  public_key_service_max_instances                              = 0
  public_key_service_execution_environment                      = ""
  enable_public_key_service_cdn                                 = false
  public_key_service_load_balancer_protocol                     = "HTTP"
  public_key_service_cdn_default_ttl_seconds                    = 0
  public_key_service_cdn_max_ttl_seconds                        = 0
  public_key_service_cdn_serve_while_stale_seconds              = 0
  public_key_service_alarm_eval_period_sec                      = ""
  public_key_service_alarm_duration_sec                         = ""
  public_key_service_max_execution_time_max                     = 0
  public_key_service_5xx_threshold                              = 0
  public_key_service_cloud_run_alert_on_memory_usage_threshold  = 0
  public_key_service_lb_max_latency_ms                          = 0
  public_key_service_lb_5xx_threshold                           = 0
  public_key_service_lb_5xx_ratio_threshold                     = 0
  public_key_service_empty_key_set_error_threshold              = 0
  public_key_service_general_error_threshold                    = 0
  private_key_service_additional_regions                        = []
  private_key_service_subdomain                                 = ""
  private_key_service_container_image_url                       = ""
  private_key_service_addon_container_image_url                 = ""
  private_key_service_cloud_run_cpu_count                       = 1
  private_key_service_cloud_run_concurrency                     = 0
  private_key_service_cloud_run_ingress                         = ""
  enable_private_key_service_cache                              = false
  private_key_service_load_balancer_protocol                    = "HTTP"
  private_key_service_cache_refresh_in_minutes                  = 0
  private_key_service_load_balancing_scheme                     = ""
  private_key_service_cloud_run_memory_mb                       = 0
  private_key_service_cloud_run_min_instances                   = 0
  private_key_service_cloud_run_max_instances                   = 0
  private_key_service_execution_environment                     = ""
  private_key_service_alarm_eval_period_sec                     = ""
  private_key_service_cloud_run_max_execution_time_max          = 0
  private_key_service_cloud_run_5xx_threshold                   = 0
  private_key_service_cloud_run_alert_on_memory_usage_threshold = 0
  private_key_service_lb_max_latency_ms                         = 0
  private_key_service_lb_5xx_threshold                          = 0
  private_key_service_lb_5xx_ratio_threshold                    = 0
  private_key_service_alarm_duration_sec                        = 0

  use_vpc_new_module = false

  private_key_service_external_managed_migration_state              = "PREPARE"
  private_key_service_external_managed_migration_testing_percentage = 0

  private_key_service_forwarding_rule_load_balancing_scheme                        = "EXTERNAL"
  private_key_service_external_managed_backend_bucket_migration_state              = null
  private_key_service_external_managed_backend_bucket_migration_testing_percentage = null

  public_key_service_external_managed_migration_state              = "PREPARE"
  public_key_service_external_managed_migration_testing_percentage = 0

  public_key_service_forwarding_rule_load_balancing_scheme                        = "EXTERNAL"
  public_key_service_external_managed_backend_bucket_migration_state              = null
  public_key_service_external_managed_backend_bucket_migration_testing_percentage = null

  public_key_service_lb_outlier_detection_enabled                               = false
  public_key_service_lb_outlier_detection_consecutive_errors                    = 0
  public_key_service_lb_outlier_detection_interval_seconds                      = 0
  public_key_service_lb_outlier_detection_base_ejection_time_seconds            = 0
  public_key_service_lb_outlier_detection_max_ejection_percent                  = 0
  public_key_service_lb_outlier_detection_enforcing_consecutive_errors          = 0
  public_key_service_lb_outlier_detection_consecutive_gateway_failure           = 0
  public_key_service_lb_outlier_detection_enforcing_consecutive_gateway_failure = 0

  private_key_service_lb_outlier_detection_enabled                               = false
  private_key_service_lb_outlier_detection_consecutive_errors                    = 0
  private_key_service_lb_outlier_detection_interval_seconds                      = 0
  private_key_service_lb_outlier_detection_base_ejection_time_seconds            = 0
  private_key_service_lb_outlier_detection_max_ejection_percent                  = 0
  private_key_service_lb_outlier_detection_enforcing_consecutive_errors          = 0
  private_key_service_lb_outlier_detection_consecutive_gateway_failure           = 0
  private_key_service_lb_outlier_detection_enforcing_consecutive_gateway_failure = 0

  key_sets_config = {
    key_sets = []
  }
  get_encrypted_private_key_general_error_threshold = 0
  private_key_service_exception_alert_threshold     = 0
  private_key_service_config_read_alert_threshold   = 0
  alert_severity_overrides                          = {}
  peer_coordinator_kms_key_base_uri                 = ""
  populate_migration_key_data                       = false
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

run "doesnt_create_key_migration_tool" {
  command = plan

  assert {
    condition     = length(module.key_migration_tool) == 0
    error_message = "Created tool"
  }
}

run "creates_both_vpc_modules" {
  command = plan

  assert {
    condition     = module.vpc.egress_internet_tag == "egress-internet"
    error_message = "Should have created old VPC module"
  }

  assert {
    condition     = module.vpc_new.egress_internet_tag == "vpc-egress-internet"
    error_message = "Should have created new VPC module"
  }

  assert {
    condition     = length(module.vpc_nat) == 2
    error_message = "Should have created new VPC NAT module for each region"
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

run "doesnt_create_key_id_type_param" {
  command = plan

  assert {
    condition     = length(module.key_id_type) == 0
    error_message = "Created param"
  }
}

run "creates_key_id_type_param" {
  command = plan

  variables {
    key_id_type = "encryption"
  }

  assert {
    condition     = length(module.key_id_type) == 1
    error_message = "Didn't create param"
  }
}

run "fails_migration_config_if_null_base_uri" {
  command = plan

  variables {
    populate_migration_key_data                 = true
    migration_peer_coordinator_kms_key_base_uri = null
  }

  expect_failures = [null_resource.has_valid_migration_configuration]
}

run "fails_migration_config_if_null_new_key_ring_location" {
  command = plan

  variables {
    populate_migration_key_data                 = true
    migration_peer_coordinator_kms_key_base_uri = "uri"
    location_new_key_ring                       = null
  }

  // Must override this module as it tries to be evaluated before the
  // null_resource
  override_module {
    target = module.key_management_service
    outputs = {
      kms_key_ring_id = "ID"
    }
  }

  expect_failures = [null_resource.has_valid_migration_configuration]
}

run "fails_key_migration_tool_for_generate_mode" {
  command = plan

  variables {
    run_key_migration_tool                 = true
    populate_migration_key_data            = true
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

    public_key_service_subdomain                = "public_subdomain"
    private_key_service_subdomain               = "private_subdomain"
    migration_peer_coordinator_kms_key_base_uri = "peer_coordinator_base_uri"
  }

  override_module {
    target = module.key_management_service
    outputs = {
      kms_key_ring_id = "ring_id"
    }
  }

  assert {
    condition     = output.get_public_key_loadbalancer_ip == "cloud_run_fe_ip_address"
    error_message = "Wrong IP"
  }
  assert {
    condition     = output.public_key_base_url == "https://public_subdomain.domain"
    error_message = "Wrong url"
  }
  assert {
    condition     = output.private_key_service_loadbalancer_ip == "cloud_run_fe_ip_address"
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
    condition     = output.key_generation_service_account == "google_service_account_email"
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
  assert {
    condition     = output.migration_peer_coordinator_kms_key_base_uri == "peer_coordinator_base_uri"
    error_message = "Wrong URL"
  }
}
