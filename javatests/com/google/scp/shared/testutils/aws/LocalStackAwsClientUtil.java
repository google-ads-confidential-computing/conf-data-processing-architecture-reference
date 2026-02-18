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

import java.time.Duration;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * Helper class for providing AWS clients configured to talk to LocalStack with test-friendly
 * configurations for retries and timeouts.
 *
 * <p>Methods should be free of side effects (e.g. creating tables or buckets) and only return the
 * configured client.
 */
public final class LocalStackAwsClientUtil {
  private static final Region REGION = Region.US_EAST_1;
  private static final Duration HTTP_CLIENT_CONNECTION_TIMEOUT_DURATION = Duration.ofSeconds(10);
  private static final Duration HTTP_CLIENT_SOCKET_TIMEOUT_DURATION = Duration.ofSeconds(10);
  private static final int RETRY_POLICY_MAX_RETRIES = 5;
  // Maximum wait time for exponential backoff retry policy.
  private static final Duration CLIENT_MAX_BACKOFF_TIME = Duration.ofSeconds(10);
  // The base delay for exponential backoff retry policy.
  private static final Duration CLIENT_BASE_DELAY = Duration.ofSeconds(2);

  /**
   * HTTP Client with increased timeouts for better resiliency in tests and TCP keepalives enabled
   * for increased performance.
   */
  private static final SdkHttpClient defaultHttpClient =
      ApacheHttpClient.builder()
          .connectionTimeout(HTTP_CLIENT_CONNECTION_TIMEOUT_DURATION)
          .socketTimeout(HTTP_CLIENT_SOCKET_TIMEOUT_DURATION)
          .tcpKeepAlive(true)
          .build();

  /**
   * AWS Client configuration which increases the number of retries and alters the backoff strategy
   * to accomodate some of the network issues encountered in e2e test.
   */
  private static final ClientOverrideConfiguration defaultClientConfiguration =
      ClientOverrideConfiguration.builder()
          .retryPolicy(
              RetryPolicy.builder()
                  .numRetries(RETRY_POLICY_MAX_RETRIES)
                  .backoffStrategy(
                      EqualJitterBackoffStrategy.builder()
                          .maxBackoffTime(CLIENT_MAX_BACKOFF_TIME)
                          .baseDelay(CLIENT_BASE_DELAY)
                          .build())
                  .build())
          .build();

  private static final AwsCredentialsProvider createCredentialsProvider(
      LocalStackContainer localStack) {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey()));
  }

  private LocalStackAwsClientUtil() {}

  /** Returns an AWS KMS Client configured to use the specified localStack container. */
  public static KmsClient createKmsClient(LocalStackContainer localStack) {
    return KmsClient.builder()
        .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.KMS))
        .httpClient(defaultHttpClient)
        .credentialsProvider(createCredentialsProvider(localStack))
        .region(REGION)
        .overrideConfiguration(defaultClientConfiguration)
        .build();
  }
}
