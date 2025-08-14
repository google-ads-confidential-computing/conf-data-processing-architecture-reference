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
package com.google.scp.coordinator.keymanagement.shared.serverless.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public abstract class ApiTaskTestBase {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock protected RequestContext request;
  @Mock protected ResponseContext response;

  protected ApiTask task;

  @Test
  public void testTryService_mismatchingRequest_failsAndDoesNotInvokeExecute() throws Exception {
    // Given
    doReturn("GET").when(request).getMethod();
    doReturn("/test-base/wrong").when(request).getPath();
    doReturn(Optional.of("key-service-caller-email@google.com"))
        .when(request)
        .getFirstHeader("email");

    // When
    boolean success = task.tryService("/test-base", request, response);

    // Then
    assertThat(success).isFalse();
    verify(task, never()).execute(any(), any(), any());
  }
}
