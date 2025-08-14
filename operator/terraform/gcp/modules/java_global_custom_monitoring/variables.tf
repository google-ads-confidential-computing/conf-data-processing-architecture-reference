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

variable "environment" {
  description = "Environment where this service is deployed (e.g. dev, prod)."
  type        = string
}

variable "enable_new_metrics" {
  description = "When true, enable new metrics created after enable remote metric aggregation"
  type        = bool
}

variable "enable_legacy_metrics" {
  description = "When true, disable legacy metrics created before enable remote metric aggregation"
  type        = bool
}
