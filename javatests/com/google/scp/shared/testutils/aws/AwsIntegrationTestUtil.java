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

package com.google.scp.shared.testutils.aws;

import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;

/** Helper class providing methods to test AWS features locally. */
public final class AwsIntegrationTestUtil {

  // localstack version is pinned so that tests are repeatable
  private static final DockerImageName IMAGE = DockerImageName.parse("localstack/localstack:1.0.4");

  private AwsIntegrationTestUtil() {}

  /**
   * Return LocalStackContainer for ClassRule to locally test AWS features via a Docker container.
   */
  public static LocalStackContainer createContainer(LocalStackContainer.Service... services) {
    return new LocalStackContainer(IMAGE).withServices(services);
  }

  /** Creates a symmetric AWS KMS key and returns its key ID. */
  public static String createKmsKey(KmsClient kmsClient) {
    String keyId = kmsClient.createKey().keyMetadata().keyId();
    kmsClient.generateDataKey(r -> r.keyId(keyId).keySpec(DataKeySpec.AES_256));
    return keyId;
  }
}
