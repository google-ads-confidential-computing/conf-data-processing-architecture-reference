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

import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;

/**
 * Provides a wrapper class for args related to running the SimpleWorker and ServiceManager to
 * manage the running worker
 */
public final class SimpleWorker {

  private final Injector injector;

  /** Creates a {@code ServiceManager} to run the worker */
  public ServiceManager createServiceManager() {
    return injector.getInstance(
        Key.get(ServiceManager.class, Annotations.WorkerServiceManager.class));
  }

  /**
   * Creates a worker from a Guice module that provides all the component wirings
   *
   * @param module Guice module that provides bindings for the worker.
   */
  public static SimpleWorker fromModule(Module module) {
    return new SimpleWorker(Guice.createInjector(module));
  }

  public Injector getInjector() {
    return injector;
  }

  private SimpleWorker(Injector injector) {
    this.injector = injector;
  }
}
