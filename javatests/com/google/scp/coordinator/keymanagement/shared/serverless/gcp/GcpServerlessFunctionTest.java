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
package com.google.scp.coordinator.keymanagement.shared.serverless.gcp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.shared.api.model.Code.NOT_FOUND;

import com.google.cloud.functions.invoker.runner.Invoker;
import com.google.common.collect.ImmutableList;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTask;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GcpServerlessFunctionTest {

  private static final String TEST_RESPONSE = "test-response";
  private static final String TEST_REQUEST_HEADER = "test-header";
  private static final int WARMUP_REQUEST_COUNT = 100;
  private static final int BURST_WAVES = 4;
  private static final int REQUESTS_PER_WAVE = 500;
  private static final int BURST_CONCURRENCY = 20;
  // After the warmup wave saturates the 20-thread client pool and Jetty's worker pool, observed
  // thread growth is 0 (baseline, current, and peak all remain at ~62 threads). Allowing up to 10
  // extra threads provides headroom for incidental JVM background threads while still catching
  // per-request thread leaks (which spawn 500 threads per wave).
  private static final int MAX_ALLOWED_THREAD_GROWTH = 10;
  private static int testPort;
  private static Invoker invoker;

  @BeforeClass
  public static void setUp() throws Exception {
    // Find an available port.
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      testPort = socket.getLocalPort();
    }
    // Start the HTTP server for the test service.
    invoker =
        new Invoker(
            testPort,
            TestService.class.getCanonicalName(),
            "http",
            Thread.currentThread().getContextClassLoader());
    invoker.startTestServer();
  }

  @AfterClass
  public static void tearDown() throws Exception {
    invoker.stopServer();
  }

  @Test
  public void testService_happyPath_returnsExpected() throws Exception {
    // Given
    HttpGet request = new HttpGet(String.format("http://localhost:%d/test-base/path", testPort));

    // When
    String body;
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      HttpResponse response = client.execute(request);
      body = EntityUtils.toString(response.getEntity());
    }

    // Then
    assertThat(body).isEqualTo(TEST_RESPONSE);
  }

  @Test
  public void testService_nonExistingEndpoint_returnsNotFound() throws Exception {
    // Given
    HttpGet request = new HttpGet(String.format("http://localhost:%d/test-base/no-such", testPort));

    // When
    HttpResponse response;
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      response = client.execute(request);
    }

    // Then
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(NOT_FOUND.getHttpStatusCode());
  }

  @Test
  public void testService_withHeader_returnsHeaderInBody() throws Exception {
    // Given
    String headerValue = "header-value";
    HttpGet request =
        new HttpGet(String.format("http://localhost:%d/test-base/path-with-header", testPort));
    request.addHeader(TEST_REQUEST_HEADER, headerValue);
    request.addHeader("unused-header", "should-not-return");

    // When
    String body;
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      HttpResponse response = client.execute(request);
      body = EntityUtils.toString(response.getEntity());
    }

    // Then
    assertThat(body).isEqualTo(headerValue);
  }

  @Test
  public void testService_burstTraffic_returnsNoServerErrorsAndMaintainsStableThreadCount()
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(BURST_CONCURRENCY);
    try (CloseableHttpClient client =
        HttpClients.custom()
            .setMaxConnTotal(BURST_CONCURRENCY)
            .setMaxConnPerRoute(BURST_CONCURRENCY)
            .build()) {
      // Ensure Guice injector is initialized before concurrent warmup.
      executeAndVerifyOkRequest(client);

      // Warm up server and client thread/connection pools under concurrent load.
      executeConcurrentWave(executor, client, WARMUP_REQUEST_COUNT);
      System.gc();

      var threadBean = ManagementFactory.getThreadMXBean();
      threadBean.resetPeakThreadCount();
      int baselineThreadCount = threadBean.getThreadCount();
      int firstWaveThreadCount = baselineThreadCount;

      for (int wave = 0; wave < BURST_WAVES; wave++) {
        executeConcurrentWave(executor, client, REQUESTS_PER_WAVE);
        int currentThreadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        if (wave == 0) {
          firstWaveThreadCount = currentThreadCount;
        }
        assertThat(currentThreadCount - baselineThreadCount).isAtMost(MAX_ALLOWED_THREAD_GROWTH);
        assertThat(peakThreadCount - baselineThreadCount).isAtMost(MAX_ALLOWED_THREAD_GROWTH);
      }

      int finalThreadCount = threadBean.getThreadCount();
      assertThat(finalThreadCount - firstWaveThreadCount).isAtMost(MAX_ALLOWED_THREAD_GROWTH);
    } finally {
      executor.shutdownNow();
    }
  }

  private static void executeConcurrentWave(
      ExecutorService executor, CloseableHttpClient client, int requestCount) throws Exception {
    List<Future<?>> futures = new ArrayList<>(requestCount);
    for (int i = 0; i < requestCount; i++) {
      futures.add(
          executor.submit(
              () -> {
                executeAndVerifyOkRequest(client);
                return null;
              }));
    }
    for (Future<?> future : futures) {
      future.get();
    }
  }

  private static void executeAndVerifyOkRequest(CloseableHttpClient client) throws Exception {
    HttpGet request = new HttpGet(String.format("http://localhost:%d/test-base/path", testPort));
    HttpResponse response = client.execute(request);
    int statusCode = response.getStatusLine().getStatusCode();
    String body = EntityUtils.toString(response.getEntity());
    assertThat(statusCode).isEqualTo(200);
    assertThat(body).isEqualTo(TEST_RESPONSE);
  }

  public static final class TestService extends GcpServerlessFunction {
    @ProvidesIntoMap
    @StringMapKey("/test-base")
    List<ApiTask> provideApiTasks() {
      return ImmutableList.of(
          new ApiTask(
              "GET", Pattern.compile("/path"), "test", "v1Test", new LogMetricHelper("test")) {
            @Override
            protected void execute(
                Matcher matcher, RequestContext request, ResponseContext response) {
              response.setBody(TEST_RESPONSE);
            }
          },
          new ApiTask(
              "GET",
              Pattern.compile("/path-with-header"),
              "test",
              "v1Test",
              new LogMetricHelper("test")) {
            @Override
            protected void execute(
                Matcher matcher, RequestContext request, ResponseContext response) {
              Optional<String> headerValue = request.getFirstHeader(TEST_REQUEST_HEADER);
              if (headerValue.isPresent()) {
                response.setBody(headerValue.get());
              } else {
                response.setBody("");
              }
            }
          });
    }
  }
}
