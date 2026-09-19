# Copyright 2022 Google LLC
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

output "load_balancer_ip" {
  value = module.load_balancer.loadbalancer_ip
}

output "key_storage_service_account_email" {
  value = module.service_account.service_account_email
}

output "key_storage_cloud_run_url" {
  value = module.cloud_run.url
}

output "cloud_run_5xx_error_alarm" {
  value = module.cloud_run.error_5xx_alarm
}

output "cloud_run_execution_time_alarm" {
  value = module.cloud_run.execution_time_alarm
}

output "load_balancer_5xx_error_ratio_alarm" {
  value = module.load_balancer.load_balancer_5xx_error_ratio_alarm
}

output "load_balancer_95_percent_latency_alarm" {
  value = module.load_balancer.load_balancer_95_percent_latency_alarm
}

output "load_balancer_99_percent_latency_alarm" {
  value = module.load_balancer.load_balancer_99_percent_latency_alarm
}
