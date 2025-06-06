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

package com.google.scp.coordinator.keymanagement.shared.serverless.common;

import com.google.common.collect.ImmutableMap;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.shared.api.exception.ServiceException;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An API task to handle requests to a specific endpoint. */
public abstract class ApiTask {
  private static final Logger logger = LoggerFactory.getLogger(ApiTask.class);

  private final String method;
  private final Pattern path;
  private final String methodId;
  private final String apiVersion;
  private final LogMetricHelper logMetricHelper;

  protected ApiTask(
      String method, Pattern path, String methodId, String apiVersion, LogMetricHelper logHelper) {
    this.method = method;
    this.path = path;
    this.methodId = methodId;
    this.apiVersion = apiVersion;
    this.logMetricHelper = logHelper;
  }

  /** Executes the task for the matched request. */
  protected abstract void execute(Matcher matcher, RequestContext request, ResponseContext response)
      throws ServiceException;

  /**
   * Services the request if it matches the task, otherwise returns false.
   *
   * @param basePath the base URL path.
   */
  protected boolean tryService(String basePath, RequestContext request, ResponseContext response)
      throws ServiceException {
    if (!Objects.equals(request.getMethod(), method) || !request.getPath().startsWith(basePath)) {
      return false;
    }
    String subPath = request.getPath().substring(basePath.length());
    Matcher matcher = path.matcher(subPath);
    if (!matcher.matches()) {
      return false;
    }

    logger.info(
        logMetricHelper.format(
            "count", ImmutableMap.of("apiVersion", apiVersion, "methodId", methodId)));
    var start = Instant.now().toEpochMilli();
    execute(matcher, request, response);
    var end = Instant.now().toEpochMilli();
    logger.info(
        logMetricHelper.format(
            "duration",
            ImmutableMap.of(
                "apiVersion",
                apiVersion,
                "methodId",
                methodId,
                "timeMs",
                Long.toString(end - start))));
    return true;
  }
}
