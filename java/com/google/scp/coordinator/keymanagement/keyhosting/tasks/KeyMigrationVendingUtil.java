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

package com.google.scp.coordinator.keymanagement.keyhosting.tasks;

import static com.google.scp.shared.gcp.util.JsonHelper.getField;
import static com.google.scp.shared.gcp.util.JsonHelper.parseJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeyMigrationVendingUtil {
  private static final Logger logger = LoggerFactory.getLogger(KeyMigrationVendingUtil.class);
  private static final String AUTH_HEADER_EMAIL_FIELD = "email";
  private static final String METRIC_SCOPE = "migration_vending/";

  // Indicates if a key was vended with or without migration data.
  private static final class Vended {
    private static final String WITH_MIGRATION_DATA = "with_migration_data";
    private static final String WITHOUT_MIGRATION_DATA = "without_migration_data";
  }

  // Indicates why a key was vended with or without migration data
  private static final class Reason {
    private static final String ALLOWED_CALLER = "allowed_caller";
    private static final String ALLOWED_SET_NAME = "allowed_set_name";
    private static final String DISALLOWED = "disallowed";
    private static final String MISSING_MIGRATION_DATA = "missing_migration_data";
  }

  private KeyMigrationVendingUtil() {}

  /**
   * Returns an {@link EncryptionKey} with either its standard private key material or with
   * migration key data substituted, based on the provided configuration.
   *
   * @param encryptionKey the key to be vended.
   * @param request the context of the incoming request.
   * @param allowedMigrators a set of strings containing service account emails or key set names
   *     that are approved for migration.
   * @implNote <b>WARNING</b>: For performance reasons, JWT signatures from requests are not
   *     validated. Key access is controlled through attestation. Do not use this utility in other
   *     contexts.
   */
  public static EncryptionKey vendAccordingToConfig(
      EncryptionKey encryptionKey,
      RequestContext request,
      ImmutableSet<String> allowedMigrators,
      LogMetricHelper logMetricHelper) {
    // Break out early if migration vending is disabled.
    if (allowedMigrators.isEmpty()) {
      return clearMigrationData(encryptionKey);
    }

    // Capture the caller email early for logging purposes.
    Optional<String> callerEmail = getCallerEmail(request);
    String email = callerEmail.orElse("unknown");
    String keyId = encryptionKey.getKeyId();
    String setName = encryptionKey.getSetName();

    // Return the original key data if any migration data is missing.
    final boolean hasMigrationData =
        !encryptionKey.getMigrationJsonEncodedKeyset().isEmpty()
            && !encryptionKey.getMigrationKeyEncryptionKeyUri().isEmpty()
            && !encryptionKey.getMigrationKeySplitDataList().isEmpty();
    if (!hasMigrationData) {
      logger.info(
          format(
              logMetricHelper,
              Vended.WITHOUT_MIGRATION_DATA,
              Reason.MISSING_MIGRATION_DATA,
              setName,
              email,
              keyId));
      return clearMigrationData(encryptionKey);
    }
    // Validate set names for migration key vending.
    if (allowedMigrators.contains(encryptionKey.getSetName())) {
      logger.info(
          format(
              logMetricHelper,
              Vended.WITH_MIGRATION_DATA,
              Reason.ALLOWED_SET_NAME,
              setName,
              email,
              keyId));
      return vendMigrationData(encryptionKey);
    }
    // Validate callers for migration key vending.
    if (callerEmail.isPresent() && allowedMigrators.contains(email)) {
      logger.info(
          format(
              logMetricHelper,
              Vended.WITH_MIGRATION_DATA,
              Reason.ALLOWED_CALLER,
              setName,
              email,
              keyId));
      return vendMigrationData(encryptionKey);
    }
    // Caller or key set is not allow listed for migration.
    logger.info(
        format(
            logMetricHelper,
            Vended.WITHOUT_MIGRATION_DATA,
            Reason.DISALLOWED,
            setName,
            email,
            keyId));
    return clearMigrationData(encryptionKey);
  }

  /**
   * Returns and Optional populated with the email associated with a {@link RequestContext}. If an
   * email cannot be parsed from the request, an empty Optional will be returned.
   */
  private static Optional<String> getCallerEmail(RequestContext request) {
    return request
        .getFirstHeader("Authorization")
        .flatMap(
            authHeader -> {
              try {
                // Removes the "Bearer " prefix from the Authorization header
                String[] authHeaderParts = authHeader.replace("Bearer ", "").split("\\.");
                // Valid JWTs are formatted as <header>.<payload>.<signature>.
                if (authHeaderParts.length < 2) {
                  logger.warn("Authorization header is not in a valid JWT format.");
                  return Optional.empty();
                }
                // Extracts, decodes, and parses the payload which will contain the email claim.
                String encodedPayload = authHeaderParts[1];
                String decodedPayload = new String(Base64.getUrlDecoder().decode(encodedPayload));
                JsonNode payloadNode = parseJson(decodedPayload);
                return Optional.of(getField(payloadNode, AUTH_HEADER_EMAIL_FIELD).asText());
              } catch (RuntimeException e) {
                logger.warn(
                    "Unable to parse Authorization header in private key request. {}",
                    e.getMessage());
                return Optional.empty();
              }
            });
  }

  /** Clears any migration related fields from the provided {@link EncryptionKey} */
  private static EncryptionKey clearMigrationData(EncryptionKey encryptionKey) {
    return encryptionKey.toBuilder()
        .clearMigrationJsonEncodedKeyset()
        .clearMigrationKeyEncryptionKeyUri()
        .clearMigrationKeySplitData()
        .build();
  }

  /**
   * Replaces the standard private key fields with the migration key data for a provided {@link
   * EncryptionKey}. The migration related fields are then cleared.
   */
  private static EncryptionKey vendMigrationData(EncryptionKey encryptionKey) {
    return encryptionKey.toBuilder()
        .setJsonEncodedKeyset(encryptionKey.getMigrationJsonEncodedKeyset())
        .setKeyEncryptionKeyUri(encryptionKey.getMigrationKeyEncryptionKeyUri())
        .clearKeySplitData()
        .addAllKeySplitData(encryptionKey.getMigrationKeySplitDataList())
        .clearMigrationJsonEncodedKeyset()
        .clearMigrationKeyEncryptionKeyUri()
        .clearMigrationKeySplitData()
        .build();
  }

  /** Helper method for creating properly formatted log metrics. */
  private static String format(
      LogMetricHelper logMetricHelper,
      String metricName,
      String reason,
      String setName,
      String callerEmail,
      String keyId) {
    return logMetricHelper.format(
        METRIC_SCOPE + metricName,
        ImmutableMap.of(
            "reason", reason,
            "setName", setName,
            "callerEmail", callerEmail,
            "keyId", keyId));
  }
}
