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

package com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyid;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.scp.shared.api.exception.ServiceException;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(TestParameterInjector.class)
public final class KeyIdFactoryTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock(answer = CALLS_REAL_METHODS)
  private KeyIdFactory factory;

  @Test
  public void testGetNextKeyId_illegalId_returnsExpectedError(
      @TestParameter({
            "foo.bar", "foo~bar",
          })
          String invalidId)
      throws Exception {

    // Given
    when(factory.getNextKeyIdBase(any())).thenReturn(invalidId);

    // When
    ThrowingRunnable when = () -> factory.getNextKeyId(null);

    // Then
    ServiceException exception = assertThrows(ServiceException.class, when);
    assertThat(exception).hasMessageThat().contains("Unexpected illegal character(s)");
  }

  @Test
  public void testGetNextKeyId_legalId_returnsExpected(
      @TestParameter({
            "foobar", "foo-bar",
          })
          String validId)
      throws Exception {

    // Given
    when(factory.getNextKeyIdBase(any())).thenReturn(validId);

    // When/Then
    factory.getNextKeyId(null);
  }
}
