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

output "get_public_key_cloudfunction_urls" {
  value = [for cloud_function in google_cloudfunctions2_function.get_public_key_cloudfunction : cloud_function.service_config[0].uri]
}

output "cloud_function_ids" {
  value = [
    for cf in google_cloudfunctions2_function.get_public_key_cloudfunction : {
      name     = cf.name
      location = cf.location
    }
  ]
}
