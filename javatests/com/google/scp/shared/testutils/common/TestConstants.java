/*
 * Copyright 2025 Google LLC
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

package com.google.scp.shared.testutils.common;

/** Contains the constants for unit tests. */
public class TestConstants {
  // Latest `eclipse-temurin:21` image as of 2026-03-25
  public static final String JAVA_21_IMAGE_NAME =
      "eclipse-temurin@sha256:3b0a98dfbdf1067c20a7854cec159551777d2ee1381bc76cd4bd0719f543b148";

  // Latest `gcr.io/distroless/python3-debian12:debug` image as of 2026-03-25
  public static final String PYTHON3_IMAGE_NAME =
      "gcr.io/distroless/python3-debian12@sha256:455b7326d5d8bdf7f2d51f49feec9c243dd270658f27762781316acc5e0d6f60";

  // localstack image for AWS tests
  // Latest `localstack/localstack:1.0.4` image as of 2022-08-11
  public static final String LOCALSTACK_IMAGE_NAME =
      "localstack/localstack@sha256:808d8ac3bdcd2ed553b3a4c8830c83530cc017a0bd5dc15fb029726d0b682b71";

  // Latest `gcr.io/cloud-devrel-public-resources/storage-testbench` image as of 2026-03-25
  public static final String GCS_IMAGE_NAME =
      "gcr.io/cloud-devrel-public-resources/storage-testbench@sha256:4d7713db9282234cb64a65e54f872bd7059f9793a94232dd9bd51b2be56b223f";

  // Latest `gcr.io/google.com/cloudsdktool/cloud-sdk:emulators` image as of 2026-03-25
  public static final String GCP_CLOUDSDK_IMAGE_NAME =
      "gcr.io/google.com/cloudsdktool/cloud-sdk@sha256:5ce660bcac0e2dd14d3b4309a95ba3c3b82af0ab0fef4ecb1d4fccdaa4bf28c9";

  // Latest `gcr.io/cloud-spanner-emulator/emulator:latest` image as of 2026-03-25
  public static final String GCP_SPANNER_IMAGE_NAME =
      "gcr.io/cloud-spanner-emulator/emulator@sha256:f450d0c2a73164ed9bbda1f3b675931f133a78bf046314db86268cbeecc67fb6";
}
