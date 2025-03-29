/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.testing;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.util.*;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.KeyGenerationArgs;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.KeyGenerationModule;
import com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.listener.PubSubListener;
import com.google.scp.coordinator.keymanagement.testutils.gcp.Annotations.LocalKmsEnvironmentVariables;
import com.google.scp.coordinator.keymanagement.testutils.gcp.GcpKeyGenerationTestEnv;
import java.util.Map;
import java.util.Optional;

/** Starts a KeyGeneration pubsub listener. */
public final class KeyGenerationStarter {

  private final KeyGenerationArgs args;
  private final String kmsUrl;

  @Inject
  public KeyGenerationStarter(
      KeyGenerationArgs args,
      @LocalKmsEnvironmentVariables Optional<Map<String, String>> localKmsEnvironmentVariables) {
    this.args = args;
    kmsUrl =
        localKmsEnvironmentVariables.isPresent()
            ? localKmsEnvironmentVariables.get().get("KMS_ENDPOINT")
            : "";
  }

  public void start() {
    PubSubListener pubSubListner = createPubSubListener(args, kmsUrl);
    new Thread(() -> pubSubListner.start()).start();
  }

  private static PubSubListener createPubSubListener(KeyGenerationArgs args, String kmsUrl) {
    Injector injector =
        Guice.createInjector(
            Modules.override(new KeyGenerationModule(args))
                .with(new GcpKeyGenerationTestEnv(args, kmsUrl)));
    return injector.getInstance(PubSubListener.class);
  }
}
