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

package com.google.scp.coordinator.keymanagement.shared.dao.gcp;

import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.NoCredentials;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.InstanceId;
import com.google.cloud.spanner.LazySpannerInitializer;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.coordinator.keymanagement.shared.dao.common.Annotations.KeyDbClient;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import java.time.Duration;

/** Module for spanner key db. */
public final class SpannerKeyDbModule extends AbstractModule {

  private static final LazySpannerInitializer SPANNER_INITIALIZER = new LazySpannerInitializer();

  /** Caller is expected to bind {@link SpannerKeyDbConfig}. */
  public SpannerKeyDbModule() {}

  @Provides
  @Singleton
  @KeyDbClient
  public DatabaseClient getDatabaseClient(SpannerKeyDbConfig config) throws Exception {
    if (config.endpointUrl().isPresent()) {
      return getDatabaseClientByEndpointUrl(config);
    }
    DatabaseId dbId =
        DatabaseId.of(config.gcpProjectId(), config.spannerInstanceId(), config.spannerDbName());
    return SPANNER_INITIALIZER.get().getDatabaseClient(dbId);
  }

  private static DatabaseClient getDatabaseClientByEndpointUrl(SpannerKeyDbConfig config) {
    String endpointUrl = config.endpointUrl().get();
    SpannerOptions.Builder spannerOptions =
        SpannerOptions.newBuilder().setProjectId(config.gcpProjectId());
    if (isEmulatorEndpoint(endpointUrl)) {
      spannerOptions.setEmulatorHost(endpointUrl).setCredentials(NoCredentials.getInstance());
    } else {
      spannerOptions.setHost(endpointUrl);
    }
    spannerOptions
        .getSpannerStubSettingsBuilder()
        .executeSqlSettings()
        .setRetryableCodes(Code.DEADLINE_EXCEEDED)
        .setRetrySettings(
            RetrySettings.newBuilder()
                // Configure retry delay settings.
                // The initial amount of time to wait before retrying the request.
                .setInitialRetryDelayDuration(Duration.ofMillis(500))
                // The maximum amount of time to wait before retrying. I.e. after this value is
                // reached, the wait time will not increase further by the multiplier.
                .setMaxRetryDelayDuration(Duration.ofSeconds(10))
                // The previous wait time is multiplied by this multiplier to come up with the next
                // wait time, until the max is reached.
                .setRetryDelayMultiplier(1.5)
                // Configure RPC and total timeout settings.
                // Timeout for the first RPC call. Subsequent retries will be based off this value.
                .setInitialRpcTimeoutDuration(Duration.ofSeconds(10))
                // The max for the per RPC timeout.
                .setMaxRpcTimeoutDuration(Duration.ofSeconds(10))
                // Controls the change of timeout for each retry.
                .setRpcTimeoutMultiplier(1.0)
                // The timeout for all calls (first call + all retries).
                .setTotalTimeoutDuration(Duration.ofSeconds(60))
                .build());
    Spanner spanner = spannerOptions.build().getService();
    InstanceId instanceId = InstanceId.of(config.gcpProjectId(), config.spannerInstanceId());
    DatabaseId databaseId = DatabaseId.of(instanceId, config.spannerDbName());
    return spanner.getDatabaseClient(databaseId);
  }

  private static boolean isEmulatorEndpoint(String endpointUrl) {
    return !endpointUrl.toLowerCase().startsWith("https://");
  }

  @Override
  protected void configure() {
    bind(KeyDb.class).to(SpannerKeyDb.class);
  }
}
