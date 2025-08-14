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

package com.google.scp.coordinator.keymanagement.keygeneration.tasks.common;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyset.KeySetConfig;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyset.KeySetManager;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ActuateKeySetTaskTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private KeySetManager keySetManager;
  @Mock private CreateSplitKeyTask createSplitKeyTask;

  @Test
  public void testExecute_multipleKeySetConfigs_correctlyInvokesCreateSplitKeyTask()
      throws Exception {
    // Given
    doReturn(
            ImmutableList.of(
                KeySetConfig.create("set-name-1", "test-template-1", 1, 2, 3, 20, 5),
                KeySetConfig.create("set-name-2", "test-template-2", 4, 5, 6, 30, 1)))
        .when(keySetManager)
        .getConfigs();

    // When
    new ActuateKeySetTask(keySetManager, createSplitKeyTask, new LogMetricHelper("test")).execute();

    // Then
    verify(createSplitKeyTask, times(1)).create("set-name-1", "test-template-1", 1, 2, 3);
    verify(createSplitKeyTask, times(1)).create("set-name-2", "test-template-2", 4, 5, 6);
  }

  @Test
  public void testExecute_creationFailureForOneConfig_continuesRemainingConfigs() throws Exception {
    // Given
    doReturn(
            ImmutableList.of(
                KeySetConfig.create("set-name-1", "test-template-1", 1, 2, 3, 10, 2),
                KeySetConfig.create("set-name-2", "test-template-2", 4, 5, 6, 20, 1),
                KeySetConfig.create("set-name-3", "test-template-3", 7, 8, 9, 30, 9)))
        .when(keySetManager)
        .getConfigs();

    doThrow(new RuntimeException("test exception"))
        .when(createSplitKeyTask)
        .create("set-name-2", "test-template-2", 4, 5, 6);

    // When
    new ActuateKeySetTask(keySetManager, createSplitKeyTask, new LogMetricHelper("test")).execute();

    // Then
    verify(createSplitKeyTask, times(1)).create("set-name-1", "test-template-1", 1, 2, 3);
    verify(createSplitKeyTask, times(1)).create("set-name-2", "test-template-2", 4, 5, 6);
    verify(createSplitKeyTask, times(1)).create("set-name-3", "test-template-3", 7, 8, 9);
  }
}
