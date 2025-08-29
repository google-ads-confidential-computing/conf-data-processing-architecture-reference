/*
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

package com.google.scp.coordinator.keymanagement.shared.serverless.common;

import static com.google.scp.shared.gcp.util.JsonHelper.getField;
import static com.google.scp.shared.gcp.util.JsonHelper.parseJson;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import java.util.Optional;

public final class RequestHeaderParsingUtil {
  private static final String AUTH_HEADER_EMAIL_FIELD = "email";

  /**
   * Returns and Optional populated with the email associated with a {@link RequestContext}. If an
   * email cannot be parsed from the request, an empty Optional will be returned.
   */
  public static Optional<String> getCallerEmail(RequestContext request) {
    return request
        .getFirstHeader("Authorization")
        .flatMap(
            authHeader -> {
              try {
                // Removes the "Bearer " prefix from the Authorization header
                String[] authHeaderParts = authHeader.replace("Bearer ", "").split("\\.");
                // Valid JWTs are formatted as <header>.<payload>.<signature>.
                if (authHeaderParts.length < 2) {
                  return Optional.empty();
                }
                // Extracts, decodes, and parses the payload which will contain the email claim.
                String encodedPayload = authHeaderParts[1];
                String decodedPayload = new String(Base64.getUrlDecoder().decode(encodedPayload));
                JsonNode payloadNode = parseJson(decodedPayload);
                return Optional.of(getField(payloadNode, AUTH_HEADER_EMAIL_FIELD).asText());
              } catch (RuntimeException e) {
                return Optional.empty();
              }
            });
  }
}
