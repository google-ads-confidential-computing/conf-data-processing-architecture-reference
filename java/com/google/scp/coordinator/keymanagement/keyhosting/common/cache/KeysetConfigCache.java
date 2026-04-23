/*
 * Copyright 2026 Google LLC
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

package com.google.scp.coordinator.keymanagement.keyhosting.common.cache;

import static com.google.scp.coordinator.keymanagement.keyhosting.common.KeySetConfig.KEY_SETS_CONFIG_ENV_VAR;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.MISSING_SET_NAME;
import static com.google.scp.shared.api.model.Code.NOT_FOUND;

import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.ParameterVersionName;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.Environment;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetConfigMap;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.ProjectId;
import com.google.scp.coordinator.keymanagement.keyhosting.common.KeySetConfig;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.shared.api.exception.ServiceException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cache used by Private Key Service to retrieve latest keyset config. */
public class KeysetConfigCache {
  private static final Logger logger = LoggerFactory.getLogger(KeysetConfigCache.class);
  private static final String LOCATION_ID = "global";
  private static final String PARAM_MANAGER_READ_ERROR = "paramManagerReadError";
  private static final String LOADING_KEY = "key";
  private static final String VERSION_ID = "v1";

  // TODO: b/451590190 - Remove once parameter client has been tested in prod
  private final ImmutableMap<String, KeySetConfig> keysetConfigMap;
  private final LoadingCache<String, ImmutableMap<String, KeySetConfig>> cache;
  private final ParameterManagerClient parameterManagerClient;
  private final ParameterVersionName parameterVersionName;
  private final LogMetricHelper logMetricHelper;

  @Inject
  public KeysetConfigCache(
      @ProjectId String projectId,
      @Environment String environment,
      @KeySetConfigMap ImmutableMap<String, KeySetConfig> keysetConfigMap,
      ParameterManagerClient parameterManagerClient,
      LogMetricHelper logMetricHelper) {
    this.keysetConfigMap = keysetConfigMap;
    this.parameterManagerClient = parameterManagerClient;
    this.logMetricHelper = logMetricHelper;

    var param = String.format("scp-%s-%s", environment, KEY_SETS_CONFIG_ENV_VAR);
    parameterVersionName = ParameterVersionName.of(projectId, LOCATION_ID, param, VERSION_ID);
    cache =
        CacheBuilder.newBuilder()
            .maximumSize(1)
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build(
                CacheLoader.asyncReloading(
                    new CacheLoader<>() {
                      @Override
                      public ImmutableMap<String, KeySetConfig> load(String unused) {
                        return retrieveKeySetConfig();
                      }
                    },
                    Executors.newSingleThreadExecutor()));
  }

  private ImmutableMap<String, KeySetConfig> retrieveKeySetConfig() {
    var cacheParam = parameterManagerClient.getParameterVersion(parameterVersionName);
    return KeySetConfig.buildKeySetConfigMap(
        cacheParam.getPayload().getData().toStringUtf8(), logMetricHelper);
  }

  public KeySetConfig get(String setName) throws ServiceException {
    try {
      var config = cache.get(LOADING_KEY).get(setName);
      return config == null ? handlePossibleError(setName, "Parameter parse error") : config;
    } catch (RuntimeException | ExecutionException e) {
      return handlePossibleError(setName, e.getMessage());
    }
  }

  private KeySetConfig handlePossibleError(String setName, String errorReason)
      throws ServiceException {
    if (keysetConfigMap.containsKey(setName)) {
      logger.error(
          logMetricHelper.format(
              PARAM_MANAGER_READ_ERROR,
              ImmutableMap.of("keySet", setName, "errorReason", errorReason)));
      return keysetConfigMap.get(setName);
    }
    throw new ServiceException(
        NOT_FOUND, MISSING_SET_NAME.name(), "Do not have config for setName " + setName);
  }
}
