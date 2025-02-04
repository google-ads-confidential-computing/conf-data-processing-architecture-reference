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

package com.google.scp.operator.worker.selector;

import com.google.scp.operator.worker.logger.ResultLoggerModule;
import com.google.scp.operator.worker.logger.inmemory.InMemoryResultLoggerModule;
import com.google.scp.operator.worker.logger.localtocloud.LocalToCloudLoggerModule;

/** CLI enum to select a {@code ResultLoggerModule} to use */
public enum ResultLoggerModuleSelector {
  IN_MEMORY(new InMemoryResultLoggerModule()),
  LOCAL_TO_CLOUD(new LocalToCloudLoggerModule());

  private final ResultLoggerModule resultLoggerModule;

  ResultLoggerModuleSelector(ResultLoggerModule resultLoggerModule) {
    this.resultLoggerModule = resultLoggerModule;
  }

  public ResultLoggerModule getResultLoggerModule() {
    return resultLoggerModule;
  }
}
