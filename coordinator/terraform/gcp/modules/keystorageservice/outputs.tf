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
  value = module.lb-http_serverless_negs.external_ip
}

output "key_storage_cloudfunction_url" {
  value = google_cloudfunctions2_function.key_storage_cloudfunction.service_config[0].uri
}

output "key_storage_service_account_email" {
  value = google_service_account.key_storage_service_account.email
}

output "key_storage_cloud_run_url" {
  value = module.cloud_run.url
}
