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
import static com.google.scp.coordinator.keymanagement.keyhosting.tasks.KeyMigrationVendingUtil.vendAccordingToConfig;
import static com.google.scp.coordinator.keymanagement.keyhosting.tasks.common.RequestContextUtil.getPositiveNumberRequestValue;
import static java.time.Instant.ofEpochMilli;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.EnableCache;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigAllowedMigrators;
import com.google.scp.coordinator.keymanagement.keyhosting.common.cache.AllKeysForSetNameCache;
import com.google.scp.coordinator.keymanagement.shared.converter.EncryptionKeyConverter;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTask;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetActiveEncryptionKeysResponseProto.GetActiveEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.exception.SharedErrorReason;
import com.google.scp.shared.api.model.Code;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GetActiveEncryptionKeysTask extends ApiTask {

  static final String START_EPOCH_MILLI_PARAM = "startEpochMillis";
  static final String END_EPOCH_MILLI_PARAM = "endEpochMillis";

  private final KeyDb keyDb;
  private final AllKeysForSetNameCache cache;
  private final boolean enableCache;
  private final ImmutableSet<String> allowedMigrators;
  private final LogMetricHelper logMetricHelper;

  @Inject
  GetActiveEncryptionKeysTask(
      KeyDb keyDb,
      AllKeysForSetNameCache cache,
      @EnableCache Boolean enableCache,
      LogMetricHelper logMetricHelper,
      @KeySetsVendingConfigAllowedMigrators ImmutableSet<String> allowedMigrators) {
    super(
        "GET",
        Pattern.compile("/sets/(?<name>[a-zA-Z0-9\\-]*)/activeKeys"),
        "GetActiveEncryptionKeys",
        "v1Beta",
        logMetricHelper);
    this.keyDb = keyDb;
    this.cache = cache;
    this.enableCache = enableCache;
    this.allowedMigrators = allowedMigrators;
    this.logMetricHelper = logMetricHelper;
  }

  @Override
  protected void execute(Matcher matcher, RequestContext request, ResponseContext response)
      throws ServiceException {
    var start = getPositiveNumberRequestValue(request, START_EPOCH_MILLI_PARAM);
    var end = getPositiveNumberRequestValue(request, END_EPOCH_MILLI_PARAM);
    if (start > end) {
      throw new ServiceException(
          Code.INVALID_ARGUMENT,
          SharedErrorReason.INVALID_ARGUMENT.name(),
          String.format("Start time %d cannot be after end time %d.", start, end));
    }
    var keys = getActiveKeys(matcher.group("name"), start, end);
    response.setBody(
        GetActiveEncryptionKeysResponse.newBuilder()
            .addAllKeys(
                keys
                    .map(
                        key ->
                            vendAccordingToConfig(key, request, allowedMigrators, logMetricHelper))
                    .map(EncryptionKeyConverter::toApiEncryptionKey)
                    .collect(toImmutableList())));
  }

  private Stream<EncryptionKey> getActiveKeys(String setName, long startMilli, long endMilli)
      throws ServiceException {
    return enableCache
        ? cache.get(setName).stream().filter(key -> isKeyActive(key, startMilli, endMilli))
        : keyDb
            .getActiveKeys(setName, 0, ofEpochMilli(startMilli), ofEpochMilli(endMilli))
            .stream();
  }

  // This replicates the filtering done by SpannerKeyDb.listRecentKeys
  // ACTIVATION_COLUMN <= @NowParam AND ... AND
  // (EXPIRY_TIME_COLUMN > @nowParam OR EXPIRY_TIME_COLUMN IS NULL)
  private static Boolean isKeyActive(EncryptionKey key, long startMilli, long endMilli) {
    return
        key.getActivationTime() <= endMilli &&
            (key.getExpirationTime() == 0 || key.getExpirationTime() > startMilli);
  }
}
