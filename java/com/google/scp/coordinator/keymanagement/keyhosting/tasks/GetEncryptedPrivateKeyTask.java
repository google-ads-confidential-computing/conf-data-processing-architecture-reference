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

package com.google.scp.coordinator.keymanagement.keyhosting.tasks;

import static com.google.scp.coordinator.keymanagement.keyhosting.service.common.converter.EncryptionKeyConverter.toApiEncryptionKey;

import com.google.inject.Inject;
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
  private final LogMetricHelper logMetricHelper;

  @Inject
  public GetEncryptedPrivateKeyTask(KeyDb keyDb, LogMetricHelper logMetricHelper) {
    super("GET", Pattern.compile("/encryptionKeys/(?<id>[a-zA-Z0-9\\-]+)"));
    this.keyDb = keyDb;
    this.logMetricHelper = logMetricHelper;
  }

  @Override
  protected void execute(Matcher matcher, RequestContext request, ResponseContext response)
      throws ServiceException {
    String id = matcher.group("id");
    try {
      response.setBody(toApiEncryptionKey(getKey(id)));
    } catch (ServiceException e) {
      logger.error(
          logMetricHelper.format(
              "get_encrypted_private_key/error",
              "errorReason",
              e.getErrorReason(),
              "keyId",
              id));
      throw e;
    }
  }

  private EncryptionKey getKey(String id) throws ServiceException {
    var key = keyDb.getKey(id);
    var nowMilli = Instant.now().toEpochMilli();
    var activationAgeInMillis = nowMilli - key.getActivationTime();
    var dayInMillis = TimeUnit.DAYS.toMillis(1);
    var logMsg = "{"
        + "\"metricName\":\"get_encrypted_private_key/age_in_days\","
        + "\"setName\":\"" + key.getSetName() + "\","
        + "\"keyId\":\"" + id + "\","
        + "\"days\":" + (activationAgeInMillis / dayInMillis)
        + "}";
    logger.info(logMsg);
    return key;
  }
}
