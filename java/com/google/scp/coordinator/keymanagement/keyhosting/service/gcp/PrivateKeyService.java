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

package com.google.scp.coordinator.keymanagement.keyhosting.service.gcp;

import com.google.inject.Provides;
import com.google.scp.coordinator.keymanagement.keyhosting.service.common.PrivateKeyServiceModule;
import com.google.scp.coordinator.keymanagement.shared.serverless.gcp.GcpServerlessFunction;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;

/** Encryption Key Service GCP Serverless Function. */
public class PrivateKeyService extends GcpServerlessFunction {
  @Override
  protected void configure() {
    install(new PrivateKeyServiceModule());
    install(new GcpKeyServiceModule());
    install(new GcpPrivateKeyServiceModule());
  }

  @Provides
  public LogMetricHelper provideLogMetricHelper() {
    return new LogMetricHelper("key_service/private_key_service");
  }
}
