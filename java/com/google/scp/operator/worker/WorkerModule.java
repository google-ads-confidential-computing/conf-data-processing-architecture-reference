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

package com.google.scp.operator.worker;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.scp.operator.worker.Annotations.PullWorkService;
import com.google.scp.operator.worker.Annotations.WorkerServiceManager;
import com.google.scp.operator.worker.perf.StopwatchRegistry;
import java.time.Clock;
import javax.inject.Singleton;

final class WorkerModule extends AbstractModule {

  @Provides
  @WorkerServiceManager
  ServiceManager provideManager(@PullWorkService Service pullWorkService) {
    return new ServiceManager(ImmutableList.of(pullWorkService));
  }

  /** Provides a ticker used for capturing runtimes */
  @Provides
  Ticker provideTimingTicker() {
    return Ticker.systemTicker();
  }

  @Override
  protected void configure() {
    bind(Clock.class).toInstance(Clock.systemDefaultZone());
    bind(Service.class).annotatedWith(PullWorkService.class).to(WorkerPullWorkService.class);

    bind(StopwatchRegistry.class).in(Singleton.class);
  }
}
