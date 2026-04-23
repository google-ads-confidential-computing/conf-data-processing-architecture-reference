/*
 * Copyright 2026 Google LLC
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

package com.google.scp.coordinator.keymanagement.shared.util;

/** Interface for environment variable names used in key management. */
public interface EnvironmentVariables {
  String ENVIRONMENT_ENV_VAR = "ENVIRONMENT";
  String PROJECT_ID_ENV_VAR = "PROJECT_ID";
  String SPANNER_INSTANCE_ENV_VAR = "SPANNER_INSTANCE";
  String SPANNER_DATABASE_ENV_VAR = "SPANNER_DATABASE";
  String SPANNER_ENDPOINT = "SPANNER_ENDPOINT";
}
