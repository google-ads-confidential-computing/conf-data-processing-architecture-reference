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
import com.google.scp.shared.api.exception.ServiceException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public abstract class KeyDbCache<K, V> {
  private final LoadingCache<K, V> keyCache;

  protected KeyDbCache(CacheBuilder<Object, Object> cacheBuilder) {
    this.keyCache =
        cacheBuilder.build(
            CacheLoader.asyncReloading(
                new CacheLoader<>() {
                  public V load(K key) throws ServiceException {
                    return getFromDb(key);
                  }
                },
                Executors.newSingleThreadExecutor()));
  }

  abstract V readDb(K key) throws ServiceException;

  private V getFromDb(K key) throws ServiceException {
    return readDb(key);
  }

  public V get(K key) throws ServiceException {
    try {
      return keyCache.get(key);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ServiceException) {
        throw (ServiceException) e.getCause();
      }
      throw ServiceException.ofUnknownException(e);
    }
  }
}
