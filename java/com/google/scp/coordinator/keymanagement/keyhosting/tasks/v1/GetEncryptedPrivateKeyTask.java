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

package com.google.scp.coordinator.keymanagement.keyhosting.tasks.v1;

import static com.google.scp.coordinator.keymanagement.keyhosting.tasks.KeyMigrationVendingUtil.vendAccordingToConfig;
import static com.google.scp.coordinator.keymanagement.shared.converter.EncryptionKeyConverter.toApiEncryptionKey;
import static com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestHeaderParsingUtil.getCallerEmail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.EnableCache;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigAllowedMigrators;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigCacheUsers;
import com.google.scp.coordinator.keymanagement.keyhosting.common.cache.GetEncryptedKeyCache;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTask;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Performs the lookup for a specific private key. */
public final class GetEncryptedPrivateKeyTask extends ApiTask {
  private static final Logger logger = LoggerFactory.getLogger(GetEncryptedPrivateKeyTask.class);

  private final KeyDb keyDb;
  private final GetEncryptedKeyCache cache;
  private final boolean enableCache;
  private final LogMetricHelper logMetricHelper;
  private final ImmutableSet<String> allowedMigrators;
  private final ImmutableSet<String> cacheUsers;

  @Inject
  public GetEncryptedPrivateKeyTask(
      KeyDb keyDb,
      GetEncryptedKeyCache cache,
      @EnableCache Boolean enableCache,
      LogMetricHelper logMetricHelper,
      @KeySetsVendingConfigAllowedMigrators ImmutableSet<String> allowedMigrators,
      @KeySetsVendingConfigCacheUsers ImmutableSet<String> cacheUsers) {
    super(
        "GET",
        Pattern.compile("/encryptionKeys/(?<id>[a-zA-Z0-9\\-]+)"),
        "GetEncryptedPrivateKey",
        "v1Beta",
        logMetricHelper);
    this.keyDb = keyDb;
    this.cache = cache;
    this.enableCache = enableCache;
    this.logMetricHelper = logMetricHelper;
    this.allowedMigrators = allowedMigrators;
    this.cacheUsers = cacheUsers;
  }

  @Override
  protected void execute(Matcher matcher, RequestContext request, ResponseContext response)
      throws ServiceException {
    String email = getCallerEmail(request).orElse("unknown");
    String id = matcher.group("id");
    try {
      EncryptionKey vendedEncryptionKey =
          vendAccordingToConfig(getKey(id, email), email, allowedMigrators, logMetricHelper);
      response.setBody(toApiEncryptionKey(vendedEncryptionKey));
    } catch (ServiceException e) {
      logger.error(
          logMetricHelper.format(
              "get_encrypted_private_key/error",
              ImmutableMap.of(
                  "errorReason", e.getErrorReason(), "keyId", id, "callerEmail", email)));
      throw e;
    }
  }

  /** Returns an {@link EncryptionKey} for a provided key ID. */
  private EncryptionKey getKey(String id, String email) throws ServiceException {
    var key = isCacheEnabled(email) ? cache.get(id) : keyDb.getKey(id);
    var nowMilli = Instant.now().toEpochMilli();
    var activationAgeInMillis = nowMilli - key.getActivationTime();
    var dayInMillis = TimeUnit.DAYS.toMillis(1);
    var days = activationAgeInMillis / dayInMillis;
    logger.info(
        logMetricHelper.format(
            "get_encrypted_private_key/age_in_days",
            ImmutableMap.of(
                "setName", key.getSetName(), "keyId", id, "days", Long.toString(days))));
    return key;
  }

  private boolean isCacheEnabled(String email) {
    return enableCache || cacheUsers.contains(email);
  }
}
