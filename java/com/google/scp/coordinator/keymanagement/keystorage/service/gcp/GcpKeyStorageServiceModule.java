/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.scp.coordinator.keymanagement.keystorage.service.gcp;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KmsClient;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient;
import com.google.inject.AbstractModule;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.CoordinatorKekUri;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.CoordinatorKeyAead;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.DisableKeySetAcl;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.KmsAeadClient;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.KmsKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.MigrationKmsAeadClient;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.MigrationKmsKeyEncryptionKeyBaseUri;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.Annotations.PopulateMigrationKeyData;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.CreateKeyTask;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.SignDataKeyTask;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.gcp.GcpCreateKeyTask;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.gcp.GcpSignDataKeyTask;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbConfig;
import com.google.scp.coordinator.keymanagement.shared.dao.gcp.SpannerKeyDbModule;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Optional;

/**
 * Defines dependencies for GCP implementation of KeyStorageService. It is intended to be used with
 * the CreateKeyHttpFunction.
 */
public class GcpKeyStorageServiceModule extends AbstractModule {
  private static final String PROJECT_ID_ENV_VAR = "PROJECT_ID";
  private static final String SPANNER_INSTANCE_ENV_VAR = "SPANNER_INSTANCE";
  private static final String SPANNER_DATABASE_ENV_VAR = "SPANNER_DATABASE";
  private static final String SPANNER_ENDPOINT = "SPANNER_ENDPOINT";
  private static final String GCP_KMS_URI_ENV_VAR = "GCP_KMS_URI";
  private static final String GCP_KMS_BASE_URI_ENV_VAR = "GCP_KMS_BASE_URI";
  private static final String MIGRATION_GCP_KMS_BASE_URI_ENV_VAR = "MIGRATION_GCP_KMS_BASE_URI";
  private static final String POPULATE_MIGRATION_KEY_DATA_ENV_VAR = "POPULATE_MIGRATION_KEY_DATA";
  private static final String DISABLE_KEY_SET_ACL_ENV_VAR = "DISABLE_KEY_SET_ACL";

  private String getGcpKmsBaseUri() {
    Map<String, String> env = System.getenv();
    // TODO: b/428770204 - Remove disableKeySetAcl check and GCP_KMS_URI_ENV_VAR after migration.
    boolean disableKeySetAcl =
        Boolean.parseBoolean(env.getOrDefault(DISABLE_KEY_SET_ACL_ENV_VAR, "true"));
    return disableKeySetAcl
        ? env.getOrDefault(GCP_KMS_URI_ENV_VAR, "unknown_gcp_uri")
        : env.getOrDefault(GCP_KMS_BASE_URI_ENV_VAR, "unknown_gcp_uri");
  }

  private String getMigrationGcpKmsBaseUri() {
    Map<String, String> env = System.getenv();
    return env.getOrDefault(MIGRATION_GCP_KMS_BASE_URI_ENV_VAR, "");
  }

  private Boolean getPopulateMigrationKeyData() {
    Map<String, String> env = System.getenv();
    return Boolean.valueOf(env.getOrDefault(POPULATE_MIGRATION_KEY_DATA_ENV_VAR, "false"));
  }

  private KmsClient getKmsAeadClient() throws GeneralSecurityException {
    return new GcpKmsClient().withDefaultCredentials();
  }

  private KmsClient getMigrationKmsAeadClient() throws GeneralSecurityException {
    return new GcpKmsClient().withDefaultCredentials();
  }

  @Override
  protected void configure() {
    Map<String, String> env = System.getenv();
    String projectId = env.getOrDefault(PROJECT_ID_ENV_VAR, "adhcloud-tp2");
    String spannerInstanceId = env.getOrDefault(SPANNER_INSTANCE_ENV_VAR, "keydbinstance");
    String spannerDatabaseId = env.getOrDefault(SPANNER_DATABASE_ENV_VAR, "keydb");
    String spannerEndpoint = env.get(SPANNER_ENDPOINT);

    // Business layer bindings
    bind(CreateKeyTask.class).to(GcpCreateKeyTask.class);
    bind(SignDataKeyTask.class).to(GcpSignDataKeyTask.class);

    bind(String.class)
        .annotatedWith(KmsKeyEncryptionKeyBaseUri.class)
        .toInstance(getGcpKmsBaseUri());
    bind(String.class)
        .annotatedWith(MigrationKmsKeyEncryptionKeyBaseUri.class)
        .toInstance(getMigrationGcpKmsBaseUri());
    bind(Boolean.class)
        .annotatedWith(DisableKeySetAcl.class)
        .toInstance(Boolean.valueOf(env.getOrDefault(DISABLE_KEY_SET_ACL_ENV_VAR, "true")));
    bind(Boolean.class)
        .annotatedWith(PopulateMigrationKeyData.class)
        .toInstance(getPopulateMigrationKeyData());

    // TODO: refactor so that GCP does not need these. Placeholder values since they are not used
    bind(String.class).annotatedWith(CoordinatorKekUri.class).toInstance("");
    try {
      bind(KmsClient.class).annotatedWith(KmsAeadClient.class).toInstance(getKmsAeadClient());
      bind(KmsClient.class)
          .annotatedWith(MigrationKmsAeadClient.class)
          .toInstance(getMigrationKmsAeadClient());
      // Bind a valid Aead to this placeholder value
      AeadConfig.register();
      bind(Aead.class)
          .annotatedWith(CoordinatorKeyAead.class)
          .toInstance(
              KeysetHandle.generateNew(KeyTemplates.get("AES128_GCM")).getPrimitive(Aead.class));
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
    // Data layer bindings
    SpannerKeyDbConfig config =
        SpannerKeyDbConfig.builder()
            .setGcpProjectId(projectId)
            .setSpannerInstanceId(spannerInstanceId)
            .setSpannerDbName(spannerDatabaseId)
            .setReadStalenessSeconds(0)
            .setEndpointUrl(Optional.ofNullable(spannerEndpoint))
            .build();
    bind(SpannerKeyDbConfig.class).toInstance(config);
    install(new SpannerKeyDbModule());
  }
}
