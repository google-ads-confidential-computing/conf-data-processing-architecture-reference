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

package com.google.scp.coordinator.keymanagement.keygeneration.app.common;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.model.GetParameterRequest;
import java.io.IOException;
import java.util.Optional;

/** Module for providing test dependencies for KeyGeneration parameters. */
public class KeyGenerationParameterTestModule extends AbstractModule {
  private static final String KEY_SETS_CONFIG =
      """
      {
        "key_sets": [
          {
            "name": "test-set",
            "count": 3,
            "validity_in_days": 20
          }
        ]
      }
      """;
  private static final ImmutableMap<String, String> PARAMETER_MAP =
      ImmutableMap.of("KEY_SETS_CONFIG", KEY_SETS_CONFIG);

  @Override
  public void configure() {
    bind(ParameterClient.class).to(KeyGenerationParameterClient.class);
  }

  @Provides
  @Singleton
  SecretManagerServiceClient provideSecretManagerServiceClient() throws IOException {
    return SecretManagerServiceClient.create();
  }

  public static class KeyGenerationParameterClient implements ParameterClient {

    @Override
    public Optional<String> getParameter(String param) throws ParameterClientException {
      return Optional.ofNullable(PARAMETER_MAP.get(param));
    }

    @Override
    public Optional<String> getLatestParameter(String param) throws ParameterClientException {
      return getParameter(param);
    }

    @Override
    public Optional<String> getParameter(
        String param, Optional<String> paramPrefix, boolean includeEnvironmentParam, boolean latest)
        throws ParameterClientException {
      return Optional.empty();
    }

    @Override
    public Optional<String> getParameter(GetParameterRequest getParameterRequest)
        throws ParameterClientException {
      return Optional.empty();
    }

    @Override
    public Optional<String> getEnvironmentName() {
      return Optional.empty();
    }

    @Override
    public Optional<String> getWorkgroupId() {
      return Optional.empty();
    }
  }
}
