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

import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.MISSING_KEY;
import static com.google.scp.shared.api.model.Code.NOT_FOUND;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.EnableCache;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.util.concurrent.TimeUnit;

public final class GetEncryptedKeyCache extends KeyDbCache<String, EncryptionKey> {

  private final KeyDb keyDb;
  private final LoadingCache<String, Boolean> missingKeyCache;
  private final boolean enableCache;

  @Inject
  public GetEncryptedKeyCache(
      KeyDb keyDb, @EnableCache Boolean enableCache, LogMetricHelper logMetricHelper) {
    super(
        CacheBuilder.newBuilder().maximumSize(2000),
        enableCache,
        "getEncryptedKeyCache",
        logMetricHelper);
    this.keyDb = keyDb;
    this.missingKeyCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(2, TimeUnit.HOURS)
        .build(
            // Never used
            new CacheLoader<>() {
              public Boolean load(String key) {
                return true;
              }
            });
    this.enableCache = enableCache;
  }

  @Override
  EncryptionKey readDb(String key) throws ServiceException {
    // Does not cause values to be loaded.
    if (enableCache && missingKeyCache.getIfPresent(key) != null) {
      throw new ServiceException(
          NOT_FOUND, MISSING_KEY.name(), "Unable to find item with keyId " + key);
    }
    try {
      return keyDb.getKey(key);
    } catch (ServiceException e) {
      if (enableCache) {
        missingKeyCache.put(key, true);
      }
      throw e;
    }
  }
}
