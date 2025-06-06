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

package com.google.scp.shared.clients.configclient.gcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import com.google.scp.shared.mapper.GuavaObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Helper class to generate Google credentials. */
public final class CredentialsHelper {
  private static final ObjectMapper mapper = new GuavaObjectMapper();

  /**
   * Provides credentials which can be used by TEE to impersonate Service Account to access
   * protected resources.
   */
  public static GoogleCredentials getAttestedCredentials(
      String wipProvider, String serviceAccountToImpersonate) throws IOException {
    String credentialConfig = getCredentialConfig(wipProvider, serviceAccountToImpersonate);
    return GoogleCredentials.fromStream(new ByteArrayInputStream(credentialConfig.getBytes()));
  }

  /** Provides credentials which can be used by TEE to access protected resources. */
  public static GoogleCredentials getAttestedCredentials(String wipProvider) throws IOException {
    String credentialConfig = getCredentialConfig(wipProvider, "");
    return GoogleCredentials.fromStream(new ByteArrayInputStream(credentialConfig.getBytes()));
  }

  private static String getCredentialConfig(String wipProvider, String serviceAccountToImpersonate)
      throws JsonProcessingException {
    CredentialConfig.Builder credentialConfigBuilder =
        CredentialConfig.builder()
            .type("external_account")
            .audience(String.format("//iam.googleapis.com/%s", wipProvider))
            .credentialSource(
                CredentialSource.builder()
                    .file("/run/container_launcher/attestation_verifier_claims_token")
                    .build())
            .subjectTokenType("urn:ietf:params:oauth:token-type:jwt")
            .tokenUrl("https://sts.googleapis.com/v1/token");

    if (!Strings.isNullOrEmpty(serviceAccountToImpersonate)) {
      credentialConfigBuilder.serviceAccountImpersonationUrl(
          String.format(
              "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/%s:generateAccessToken",
              serviceAccountToImpersonate));
    }

    return mapper.writeValueAsString(credentialConfigBuilder.build());
  }
}
