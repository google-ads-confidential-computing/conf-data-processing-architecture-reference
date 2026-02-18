/*
 * Copyright 2022 Google LLC
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

package com.google.scp.coordinator.keymanagement.shared.model;

/** Parameters needed for the key generation configuration */
public final class KeyGenerationParameter {
  // COMMON
  public static final String POPULATE_MIGRATION_KEY_DATA = "POPULATE_MIGRATION_KEY_DATA";
  public static final String KEY_DB_NAME = "KEY_DB_NAME";
  public static final String KMS_KEY_BASE_URI = "KMS_KEY_BASE_URI";
  public static final String MIGRATION_KMS_KEY_BASE_URI = "MIGRATION_KMS_KEY_BASE_URI";

  // GCP
  public static final String KEY_STORAGE_SERVICE_BASE_URL = "KEY_STORAGE_SERVICE_BASE_URL";
  public static final String SPANNER_INSTANCE = "SPANNER_INSTANCE";
  public static final String SUBSCRIPTION_ID = "SUBSCRIPTION_ID";
  public static final String NUMBER_OF_KEYS_TO_CREATE = "NUMBER_OF_KEYS_TO_CREATE";
  public static final String KEYS_VALIDITY_IN_DAYS = "KEYS_VALIDITY_IN_DAYS";
  public static final String KEY_TTL_IN_DAYS = "KEY_TTL_IN_DAYS";
  public static final String CREATE_MAX_DAYS_AHEAD = "CREATE_MAX_DAYS_AHEAD";

  public static final String PEER_COORDINATOR_KMS_KEY_BASE_URI =
      "PEER_COORDINATOR_KMS_KEY_BASE_URI";
  public static final String MIGRATION_PEER_COORDINATOR_KMS_KEY_BASE_URI =
      "MIGRATION_PEER_COORDINATOR_KMS_KEY_BASE_URI";

  public static final String KEY_STORAGE_SERVICE_CLOUDFUNCTION_URL =
      "KEY_STORAGE_SERVICE_CLOUDFUNCTION_URL";

  public static final String PEER_COORDINATOR_WIP_PROVIDER = "PEER_COORDINATOR_WIP_PROVIDER";

  public static final String PEER_COORDINATOR_SERVICE_ACCOUNT = "PEER_COORDINATOR_SERVICE_ACCOUNT";

  public static final String KEY_ID_TYPE = "KEY_ID_TYPE";
}
