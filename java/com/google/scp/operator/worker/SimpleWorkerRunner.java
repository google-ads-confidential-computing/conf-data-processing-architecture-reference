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

import com.beust.jcommander.JCommander;

/**
 * Defines entry class used to run SimpleWorker and convert CLI Arguments to {@link
 * SimpleWorkerArgs}
 */
final class SimpleWorkerRunner {

  public static void main(String[] args) {
    SimpleWorkerArgs cliArgs = new SimpleWorkerArgs();
    JCommander.newBuilder().allowParameterOverwriting(true).addObject(cliArgs).build().parse(args);

    SimpleWorkerModule guiceModule = new SimpleWorkerModule(cliArgs);

    SimpleWorker worker = SimpleWorker.fromModule(guiceModule);
    worker.createServiceManager().startAsync();
  }
}
