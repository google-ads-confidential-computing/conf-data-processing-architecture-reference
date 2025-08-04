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
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keyhosting.common.cache.GetActiveEncryptionKeysCache;
import com.google.scp.coordinator.keymanagement.shared.converter.EncryptionKeyConverter;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTask;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetActiveEncryptionKeysResponseProto.GetActiveEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetActiveEncryptionKeysTask extends ApiTask {
  private final GetActiveEncryptionKeysCache cache;

  @Inject
  GetActiveEncryptionKeysTask(GetActiveEncryptionKeysCache cache, LogMetricHelper logMetricHelper) {
    super("GET",
        Pattern.compile("/sets/(?<name>[a-zA-Z0-9\\-]*)/activeKeys"),
        "GetActiveEncryptionKeys",
        "v1Beta",
        logMetricHelper);
    this.cache = cache;
  }

  @Override
  protected void execute(Matcher matcher, RequestContext request, ResponseContext response)
      throws ServiceException {
    ImmutableList<EncryptionKey> keys = cache.get(matcher.group("name"));
    response.setBody(
        GetActiveEncryptionKeysResponse.newBuilder()
            .addAllKeys(
                keys.stream()
                    .map(EncryptionKeyConverter::toApiEncryptionKey)
                    .collect(toImmutableList())));
  }
}
