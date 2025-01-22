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
package com.google.scp.operator.worker.testing;

import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Inject;
import com.google.scp.operator.worker.SimpleWorker;
import com.google.scp.operator.worker.SimpleWorkerArgs;
import com.google.scp.operator.worker.SimpleWorkerModule;
import java.util.Optional;

/** Test helper for starting a {@link SimpleWorker}, injecting {@link SimpleWorkerArgs}. */
public final class SimpleWorkerStarter {
  private final SimpleWorkerArgs args;

  /** Present if the service manager is started and healthy, otherwise empty. */
  private Optional<ServiceManager> currentServiceManager = Optional.empty();

  @Inject
  public SimpleWorkerStarter(SimpleWorkerArgs args) {
    this.args = args;
  }

  /**
   * Starts the SimpleWorker, throwing if it's already started. Blocks until it's healthy.
   *
   * <p>Note: SimpleWorker cannot truly be "stopped" because the job listener is disconnected from
   * the parent service manager (see b/240768973) -- create a new SimpleWorkerStarter per test.
   *
   * @throws IllegalStateException If started while an existing service manager is running.
   */
  public void start() {
    if (currentServiceManager.isPresent()) {
      throw new IllegalStateException(
          "SimpleWorker cannot be started more than once, start a new SimpleWorkerStarter"
              + " configured with a new queue");
    }

    var serviceManager = createServiceManager(args);
    serviceManager.startAsync().awaitHealthy();

    currentServiceManager = Optional.of(serviceManager);
  }

  /**
   * Stops the SplitKeyGenerationApplication, does not throw if the application is already stopped
   * to allow this to safely be used in an @After block.
   *
   * <p>NOTE: this only stops the parent service manager, the underlying queue is still listened to.
   */
  public void stop() {
    if (currentServiceManager.isEmpty()) {
      return;
    }
    // TODO(b/240768973): stopping the service manager doesn't currently work and the job queue
    // listener isn't actually stopped -- don't awaitStopped because that will time out.
    currentServiceManager.get().stopAsync();
  }

  private static ServiceManager createServiceManager(SimpleWorkerArgs args) {
    var simpleWorkerModule = new SimpleWorkerModule(args);
    var simpleWorker = SimpleWorker.fromModule(simpleWorkerModule);
    return simpleWorker.createServiceManager();
  }
}
