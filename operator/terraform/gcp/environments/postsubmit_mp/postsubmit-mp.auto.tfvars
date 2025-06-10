environment = "postsubmit-mp"
project_id  = "admcloud-adtech1"
region      = "us-central1"
region_zone = "us-central1-c"

# Multi region location
# https://cloud.google.com/storage/docs/locations
operator_package_bucket_location = "US"

spanner_instance_config              = "regional-us-central1"
spanner_processing_units             = 100
spanner_database_deletion_protection = false

worker_image = "us-docker.pkg.dev/admcloud-scp/docker-repo-dev/worker_app_mp_gcp:postsubmit"
# Temporarily use the demo coordinator service until we are ready to integrate dev environments
allowed_operator_service_account = ""
user_provided_worker_sa_email    = "postsubmit-mp-worker-sa@admcloud-adtech1.iam.gserviceaccount.com"
worker_logging_enabled           = true
worker_container_log_redirect    = "true"
worker_instance_force_replace    = true
worker_memory_monitoring_enabled = true
max_worker_instances             = 2
alarms_enabled                   = true
alarms_notification_email        = "fakeemail@google.com"

auto_create_subnetworks = false
network_name_suffix     = "network-with-custom-subnet"
worker_subnet_cidr      = "14.16.0.0/20"

enable_job_completion_notifications = true

enable_job_completion_notifications_per_job           = true
job_completion_notifications_cloud_function_jar       = "/tmp/postsubmit_mp/jars/JobNotificationCloudFunction_deploy.jar"
job_completion_notifications_cloud_function_cpu_count = "1"
job_completion_notifications_cloud_function_memory_mb = "512"

worker_scale_in_jar  = "/tmp/postsubmit_mp/jars/WorkerScaleInCloudFunction_deploy.jar"
frontend_service_jar = "/tmp/postsubmit_mp/jars/FrontendServiceHttpCloudFunction_deploy.jar"

enable_native_metric_aggregation   = false
enable_remote_metric_aggregation   = true
metric_exporter_interval_in_millis = 5000
collector_domain_name              = "collector.metrics"
collector_dns_name                 = "scp.testings.postsubmit"
collector_subnet_cidr              = "14.20.0.0/20"
proxy_subnet_cidr                  = "14.32.0.0/20"

collector_exceed_cpu_usage_alarm = {
  enable_alarm : true,
  duration_sec : 300,
  alignment_period_sec : 600,
  threshold : 0.9,
  severity : "moderate",
  auto_close_sec : 1800
}
collector_exceed_memory_usage_alarm = {
  enable_alarm : true,
  duration_sec : 300,
  alignment_period_sec : 600,
  threshold : 6442450944, # 6 GB
  severity : "moderate",
  auto_close_sec : 1800
}
collector_export_error_alarm = {
  enable_alarm : true,
  duration_sec : 300,
  alignment_period_sec : 600,
  threshold : 50,
  severity : "moderate",
  auto_close_sec : 1800
}
collector_crash_error_alarm = {
  enable_alarm : true,
  duration_sec : 300,
  alignment_period_sec : 600,
  threshold : 50,
  severity : "moderate",
  auto_close_sec : 1800
}
collector_run_error_alarm = {
  enable_alarm : true,
  duration_sec : 300,
  alignment_period_sec : 600,
  threshold : 50,
  severity : "moderate",
  auto_close_sec : 1800
}
worker_exporting_metrics_error_alarm = {
  enable_alarm : true,
  duration_sec : 300,
  alignment_period_sec : 600,
  threshold : 50,
  severity : "moderate",
  auto_close_sec : 1800
}

frontend_cloudfunction_use_java21_runtime     = true
notification_cloudfunction_use_java21_runtime = true
autoscaling_cloudfunction_use_java21_runtime  = true

# Keeping around in case it's needed. See commit message in: tg/2445824
# frontend_service_cloudfunction_runtime_sa_email = "java-postsubmit-fe@admcloud-adtech1.iam.gserviceaccount.com"

instance_disk_image_family = {
  image_project = "confidential-space-images",
  image_family  = "confidential-space"
}
# Needs to be a stable image for proper coordinator attestation
instance_disk_image = "projects/confidential-space-images/global/images/confidential-space-241000"

vpcsc_compatible = true
