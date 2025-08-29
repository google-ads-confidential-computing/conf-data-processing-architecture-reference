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

package com.google.scp.coordinator.keymanagement.keyhosting.tasks.v1;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.EnableCache;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetsVendingConfigAllowedMigrators;
import com.google.scp.coordinator.keymanagement.keyhosting.common.cache.GetEncryptedKeyCache;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;

/** Performs the lookup for a specific private key. */
public final class GetEncryptedPrivateKeyTask
    extends com.google.scp.coordinator.keymanagement.keyhosting.tasks.GetEncryptedPrivateKeyTask {

  @Inject
  public GetEncryptedPrivateKeyTask(
      KeyDb keyDb,
      GetEncryptedKeyCache cache,
      @EnableCache Boolean enableCache,
      LogMetricHelper logMetricHelper,
      @KeySetsVendingConfigAllowedMigrators ImmutableSet<String> allowedMigrators) {
    super(keyDb, cache, enableCache, logMetricHelper, "v1Beta", allowedMigrators);
  }
}
