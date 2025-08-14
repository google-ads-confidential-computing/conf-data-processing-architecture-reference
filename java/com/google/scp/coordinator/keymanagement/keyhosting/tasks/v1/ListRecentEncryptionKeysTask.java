/*
 * Copyright 2024 Google LLC
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.EnableCache;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigAllowedMigrators;
import com.google.scp.coordinator.keymanagement.keyhosting.common.cache.ListRecentEncryptionKeysCache;
import com.google.scp.coordinator.keymanagement.keyhosting.tasks.KeyMigrationVendingUtil;
import com.google.scp.coordinator.keymanagement.shared.converter.EncryptionKeyConverter;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTask;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.ListRecentEncryptionKeysResponseProto.ListRecentEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.exception.SharedErrorReason;
import com.google.scp.shared.api.model.Code;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListRecentEncryptionKeysTask extends ApiTask {
  private static final Logger logger = LoggerFactory.getLogger(ListRecentEncryptionKeysTask.class);

  static final String MAX_AGE_SECONDS_PARAM_NAME = "maxAgeSeconds";

  private final KeyDb keyDb;
  private final ListRecentEncryptionKeysCache cache;
  private final boolean enableCache;
  private final LogMetricHelper logMetricHelper;
  private final ImmutableSet<String> allowedMigrators;

  @Inject
  ListRecentEncryptionKeysTask(
      KeyDb keyDb,
      ListRecentEncryptionKeysCache cache,
      @EnableCache Boolean enableCache,
      LogMetricHelper logMetricHelper,
      @KeySetsVendingConfigAllowedMigrators ImmutableSet<String> allowedMigrators) {
    super(
        "GET",
        Pattern.compile("/sets/(?<name>[a-zA-Z0-9\\-]*)/encryptionKeys:recent"),
        "ListRecentEncryptionKeys",
        "v1Beta",
        logMetricHelper);
    this.keyDb = keyDb;
    this.cache = cache;
    this.enableCache = enableCache;
    this.logMetricHelper = logMetricHelper;
    this.allowedMigrators = allowedMigrators;
  }

  @Override
  protected void execute(Matcher matcher, RequestContext request, ResponseContext response)
      throws ServiceException {
    var setName = matcher.group("name");
    var maxAge = getMaxAage(request);
    logger.info(
        logMetricHelper.format(
            "list_recent_encrypted_keys/age_in_days",
            ImmutableMap.of("setName", setName, "days", Long.toString(maxAge.toDays()))));
    var keys = getListOfKeys(setName, maxAge);
    response.setBody(
        ListRecentEncryptionKeysResponse.newBuilder()
            .addAllKeys(
                keys
                    .map(
                        key ->
                            KeyMigrationVendingUtil.vendAccordingToConfig(
                                key, request, allowedMigrators, logMetricHelper))
                    .map(EncryptionKeyConverter::toApiEncryptionKey)
                    .collect(toImmutableList())));
  }

  private Stream<EncryptionKey> getListOfKeys(String setName, Duration maxAge)
      throws ServiceException {
    if (enableCache) {
      var expiryTimeCutoff = Instant.now().minus(maxAge).toEpochMilli();
      ImmutableList<EncryptionKey> keys = cache.get(setName);
      return keys
          .stream()
          .filter(key -> isKeyRecent(key, expiryTimeCutoff));
    }

    return keyDb.listRecentKeys(setName, maxAge);
  }

  // This replicates the filtering done by SpannerKeyDb.listRecentKeys
  // CREATED_AT_COLUMN >= @NowParam AND ... AND
  // (EXPIRY_TIME_COLUMN >= @nowParam OR EXPIRY_TIME_COLUMN IS NULL)
  private static Boolean isKeyRecent(EncryptionKey key, long epochMilliCutoff) {
    return
        key.getCreationTime() >= epochMilliCutoff &&
            (!key.hasExpirationTime() || key.getExpirationTime() >= epochMilliCutoff);
  }

  private static Duration getMaxAage(RequestContext request) throws ServiceException {
    String maxAgeSecondsString =
        request
            .getFirstQueryParameter(MAX_AGE_SECONDS_PARAM_NAME)
            .orElseThrow(
                () ->
                    new ServiceException(
                        Code.INVALID_ARGUMENT,
                        SharedErrorReason.INVALID_ARGUMENT.name(),
                        String.format(
                            "%s query parameter is required.", MAX_AGE_SECONDS_PARAM_NAME)));
    try {
      int maxAgeSeconds = Integer.parseInt(maxAgeSecondsString);
      if (maxAgeSeconds < 0) {
        throw new ServiceException(
            Code.INVALID_ARGUMENT,
            SharedErrorReason.INVALID_ARGUMENT.name(),
            String.format(
                "%s should be positive, found (%s) instead.",
                MAX_AGE_SECONDS_PARAM_NAME, maxAgeSeconds));
      }
      return Duration.ofSeconds(maxAgeSeconds);
    } catch (NumberFormatException e) {
      throw new ServiceException(
          Code.INVALID_ARGUMENT,
          SharedErrorReason.INVALID_ARGUMENT.name(),
          String.format("%s should be a valid integer.", MAX_AGE_SECONDS_PARAM_NAME),
          e);
    }
  }
}
