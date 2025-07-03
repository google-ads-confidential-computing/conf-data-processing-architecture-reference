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

public abstract class KeyDbCache<K, V> {
  static final ImmutableMap<String, String> READ_DB_MAP = ImmutableMap.of("readDb", "1");
  static final ImmutableMap<String, String> READ_CACHE_MAP = ImmutableMap.of("cacheAttempt", "1");

  private final LoadingCache<K, V> keyCache;
  private final boolean enableCache;
  private final LogMetricHelper logMetricHelper;

  protected KeyDbCache(
      CacheBuilder<Object, Object> cacheBuilder, boolean enableCache, LogMetricHelper logMetricHelper) {
    this.keyCache = cacheBuilder
        .build(
            new CacheLoader<>() {
              public V load(K key) throws ServiceException {
                return getFromDb(key);
              }
            });
    this.enableCache = enableCache;
    this.logMetricHelper = logMetricHelper;
  }

  abstract V readDb(K key) throws ServiceException;

  abstract String metricName();

  private V getFromDb(K key) throws ServiceException {
    logMetricHelper.format(metricName(), READ_DB_MAP);
    return readDb(key);
  }

  public V getKey(K key) throws ServiceException {
    if (!enableCache) {
      return readDb(key);
    }
    try {
      logMetricHelper.format(metricName(), READ_CACHE_MAP);
      return keyCache.get(key);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ServiceException) {
        throw (ServiceException) e.getCause();
      }
      throw ServiceException.ofUnknownException(e);
    }
  }
}
