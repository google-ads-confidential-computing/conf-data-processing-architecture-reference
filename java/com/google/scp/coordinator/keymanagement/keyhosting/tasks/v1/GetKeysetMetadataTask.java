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

package com.google.scp.coordinator.keymanagement.keyhosting.tasks.v1;

import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.MISSING_SET_NAME;
import static com.google.scp.shared.api.model.Code.NOT_FOUND;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetConfigMap;
import com.google.scp.coordinator.keymanagement.keyhosting.common.KeySetConfig;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTask;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetKeysetMetadataResponseProto.GetKeysetMetadataResponse;
import com.google.scp.shared.api.exception.ServiceException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetKeysetMetadataTask extends ApiTask {

  private final ImmutableMap<String, KeySetConfig> keySetConfigMap;

  @Inject
  protected GetKeysetMetadataTask(
      @KeySetConfigMap ImmutableMap<String, KeySetConfig> keySetConfigMap,
      LogMetricHelper logMetricHelper) {
    super(
        "GET",
        Pattern.compile("/sets/(?<name>[a-zA-Z0-9\\-]*)/keysetMetadata"),
        "GetKeysetMetadata",
        "v1Beta",
        logMetricHelper);
    this.keySetConfigMap = keySetConfigMap;
  }

  @Override
  protected void execute(Matcher matcher, RequestContext request, ResponseContext response)
      throws ServiceException {
    var setName = matcher.group("name");
    if (keySetConfigMap.containsKey(setName)) {
      response.setBody(
          GetKeysetMetadataResponse.newBuilder()
              .setActiveKeyCount(computeExpectedActiveKeyCount(keySetConfigMap.get(setName)))
              .build());
    } else {
      throw new ServiceException(
          NOT_FOUND, MISSING_SET_NAME.name(), "Do not have config for setName " + setName);
    }
  }

  private static int computeExpectedActiveKeyCount(KeySetConfig config) {
    return config.getOverlapPeriodDays() > 0
        ? config.getValidityInDays() / (config.getValidityInDays() - config.getOverlapPeriodDays())
        : config.getCount();
  }
}
