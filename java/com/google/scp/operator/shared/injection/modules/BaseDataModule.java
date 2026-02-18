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

package com.google.scp.operator.shared.injection.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.scp.operator.shared.dao.metadatadb.common.JobMetadataDb;
import java.time.Clock;

/** Allows swappable/discoverable DataModules when an inheritor's package is referenced. */
public abstract class BaseDataModule extends AbstractModule {

  /**
   * Arbitrary configurations that can be done by the implementing class to support dependencies
   * that are specific to that implementation.
   */
  protected void configureModule() {}

  /** Gets the implementation of the {@code Clock} class. */
  public abstract Clock provideClock();

  /** Returns an implementation of the {@code JobMetadataDb} class. */
  public abstract Class<? extends JobMetadataDb> getJobMetadataDbImplementation();

  /** Configures injected dependencies for this module. */
  @Override
  protected void configure() {
    bind(getJobMetadataDbImplementation()).in(Singleton.class);
    bind(JobMetadataDb.class).to(getJobMetadataDbImplementation()).in(Singleton.class);
    bind(Clock.class).toInstance(provideClock());
    configureModule();
  }
}
