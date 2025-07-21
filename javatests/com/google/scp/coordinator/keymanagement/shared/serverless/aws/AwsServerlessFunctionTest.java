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
package com.google.scp.coordinator.keymanagement.shared.serverless.aws;

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.shared.api.model.Code.NOT_FOUND;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.common.collect.ImmutableList;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTask;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AwsServerlessFunctionTest {

  private static final String TEST_RESPONSE = "test-response";
  private static final String TEST_REQUEST_HEADER = "test-header";

  @Test
  public void testService_happyPath_returnsExpected() {
    // Given
    APIGatewayProxyRequestEvent request =
        new APIGatewayProxyRequestEvent().withHttpMethod("GET").withPath("/test-base/path");

    // When
    APIGatewayProxyResponseEvent response = new TestService().handleRequest(request, null);

    // Then
    assertThat(response.getBody()).isEqualTo(TEST_RESPONSE);
  }

  @Test
  public void testService_nonExistingEndpoint_returnsNotFound() {
    // Given
    APIGatewayProxyRequestEvent request =
        new APIGatewayProxyRequestEvent().withHttpMethod("GET").withPath("/test-base/no-such");

    // When
    APIGatewayProxyResponseEvent response = new TestService().handleRequest(request, null);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND.getHttpStatusCode());
  }

  @Test
  public void testService_withHeader_returnsHeaderInBody() throws Exception {
    // Given
    String expectedHeaderValue = "header-value";
    Map<String, String> headers = new HashMap<>();
    headers.put(TEST_REQUEST_HEADER, expectedHeaderValue);
    headers.put("unused-header", "should-not-return");
    APIGatewayProxyRequestEvent request =
        new APIGatewayProxyRequestEvent()
            .withHttpMethod("GET")
            .withPath("/test-base/path-with-header")
            .withHeaders(headers);
    // .withMultiValueHeaders(headers);

    // When
    APIGatewayProxyResponseEvent response = new TestService().handleRequest(request, null);

    // Then
    assertThat(response.getBody()).isEqualTo(expectedHeaderValue);
  }

  @Test
  public void testService_withMultiValueHeader_returnsHeaderInBody() throws Exception {
    // Given
    String expectedHeaderValue = "header-value";
    Map<String, List<String>> headers = new HashMap<>();
    headers.put(TEST_REQUEST_HEADER, List.of(expectedHeaderValue));
    headers.put("unused-header", List.of("should-not-return"));
    APIGatewayProxyRequestEvent request =
        new APIGatewayProxyRequestEvent()
            .withHttpMethod("GET")
            .withPath("/test-base/path-with-header")
            .withMultiValueHeaders(headers);

    // When
    APIGatewayProxyResponseEvent response = new TestService().handleRequest(request, null);

    // Then
    assertThat(response.getBody()).isEqualTo(expectedHeaderValue);
  }

  public static final class TestService extends AwsServerlessFunction {
    @ProvidesIntoMap
    @StringMapKey("/test-base")
    List<ApiTask> provideApiTasks() {
      return ImmutableList.of(
          new ApiTask(
              "GET", Pattern.compile("/path"), "test", "v1Beta", new LogMetricHelper("test")) {
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
