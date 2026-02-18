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

package com.google.scp.coordinator.keymanagement.keyhosting.common.cache;

import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.CacheRefreshInMinutes;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.util.concurrent.TimeUnit;

@Singleton
public class AllKeysForSetNameCache extends KeyDbCache<String, ImmutableList<EncryptionKey>> {

  private final KeyDb keyDb;

  @Inject
  public AllKeysForSetNameCache(
      KeyDb keyDb, @CacheRefreshInMinutes Integer cacheRefresh, LogMetricHelper logMetricHelper) {
    this(keyDb, cacheRefresh, MINUTES, logMetricHelper);
  }

  @VisibleForTesting
  AllKeysForSetNameCache(
      KeyDb keyDb, Integer cacheRefresh, TimeUnit timeUnit, LogMetricHelper logMetricHelper) {
    super(CacheBuilder.newBuilder().refreshAfterWrite(cacheRefresh, timeUnit).maximumSize(200));
    this.keyDb = keyDb;
  }

  @Override
  ImmutableList<EncryptionKey> readDb(String setName) throws ServiceException {
    return keyDb.listAllKeysForSetName(setName);
  }
}
