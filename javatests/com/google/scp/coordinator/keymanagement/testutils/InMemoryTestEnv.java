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

package com.google.scp.coordinator.keymanagement.testutils;

import static com.google.scp.coordinator.keymanagement.testutils.DynamoKeyDbTestUtil.KEY_LIMIT;
import static com.google.scp.coordinator.keymanagement.testutils.InMemoryKeyDbTestUtil.CACHE_CONTROL_MAX;

import com.google.acai.TestScoped;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.CacheControlMaximum;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.DisableActivationTime;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.EnableCache;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeyLimit;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;

public final class InMemoryTestEnv extends AbstractModule {

  @Provides
  @TestScoped
  public InMemoryKeyDb getInMemoryKeyDb() {
    return new InMemoryKeyDb();
  }

  @Provides
  @TestScoped
  public KeyDb getKeyDb(InMemoryKeyDb inMemoryKeyDb) {
    return inMemoryKeyDb;
  }

  @Override
  public void configure() {
    bind(Integer.class).annotatedWith(KeyLimit.class).toInstance(KEY_LIMIT);
    bind(Boolean.class).annotatedWith(EnableCache.class).toInstance(true);
    bind(Long.class).annotatedWith(CacheControlMaximum.class).toInstance(CACHE_CONTROL_MAX);
    bind(Boolean.class).annotatedWith(DisableActivationTime.class).toInstance(false);
  }
}
