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

package com.google.scp.coordinator.keymanagement.keyhosting.common.cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.shared.api.exception.ServiceException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KeyDbCache<K, V> {
  private final Logger logger = LoggerFactory.getLogger(KeyDbCache.class);

  private static final String PRIVATE_KEY_READ_DB = "privateKeyReadDb";
  private static final String PRIVATE_KEY_READ_CACHE = "privateKeyReadCache";

  private final LoadingCache<K, V> keyCache;
  private final boolean enableCache;
  private final LogMetricHelper logMetricHelper;
  private final ImmutableMap<String, String> labelMap;

  protected KeyDbCache(
      CacheBuilder<Object, Object> cacheBuilder,
      boolean enableCache,
      String methodName,
      LogMetricHelper logMetricHelper) {
    this.keyCache = cacheBuilder
        .build(
            CacheLoader.asyncReloading(
                new CacheLoader<>() {
                  public V load(K key) throws ServiceException {
                    return getFromDb(key);
                  }
                },
                Executors.newSingleThreadExecutor()));
    this.enableCache = enableCache;
    this.logMetricHelper = logMetricHelper;
    this.labelMap = ImmutableMap.of("methodName", methodName);
  }

  abstract V readDb(K key) throws ServiceException;

  private V getFromDb(K key) throws ServiceException {
    logger.info(logMetricHelper.format(PRIVATE_KEY_READ_DB, labelMap));
    return readDb(key);
  }

  public V get(K key) throws ServiceException {
    if (!enableCache) {
      return readDb(key);
    }
    try {
      logger.info(logMetricHelper.format(PRIVATE_KEY_READ_CACHE, labelMap));
      return keyCache.get(key);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ServiceException) {
        throw (ServiceException) e.getCause();
      }
      throw ServiceException.ofUnknownException(e);
    }
  }
}
