/*
 * Copyright 2026 Google LLC
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

package com.google.scp.coordinator.keymanagement.keyhosting.common.cache;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.ParameterVersion;
import com.google.cloud.parametermanager.v1.ParameterVersionName;
import com.google.cloud.parametermanager.v1.ParameterVersionPayload;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.scp.coordinator.keymanagement.keyhosting.common.KeySetConfig;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.shared.api.exception.ServiceException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class KeysetConfigCacheTest {
  private static final String PROJECT_ID = "project-id";
  private static final String ENVIRONMENT = "environ";
  private static final String SET_NAME_1 = "setName1";
  private static final String SET_NAME_2 = "setName2";
  private static final String KEY_SETS_CONFIG =
      """
      {
        "key_sets": [
          {
            "name": "setName1",
            "tink_template": "test_template1",
            "count": 5,
            "validity_in_days": 10,
            "ttl_in_days": 7
          },
          {
            "name": "test-set-2",
            "tink_template": "test_template1",
            "count": 3,
            "validity_in_days": 20,
            "ttl_in_days": 7
          }
        ]
      }
      """;
  private static final KeySetConfig KEY_SET_CONFIG_1 = new KeySetConfig(SET_NAME_1, 5, 10, 0, 0);
  private static final KeySetConfig KEY_SET_CONFIG_FAIL_1 =
      new KeySetConfig(SET_NAME_1, 10, 20, 0, 0);
  private static final KeySetConfig KEY_SET_CONFIG_FAIL_2 =
      new KeySetConfig(SET_NAME_2, 1, 0, 0, 0);
  private static final ImmutableMap<String, KeySetConfig> keysetConfigMap =
      ImmutableMap.of(SET_NAME_1, KEY_SET_CONFIG_FAIL_1, SET_NAME_2, KEY_SET_CONFIG_FAIL_2);

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock ParameterManagerClient mockClient;
  @Mock ParameterVersion mockParam;
  @Mock ParameterVersionPayload mockPayload;

  private ArgumentCaptor<ParameterVersionName> parameterNameCaptor;
  private KeysetConfigCache keysetConfigCache;

  @Before
  public void before() {
    when(mockParam.getPayload()).thenReturn(mockPayload);
    when(mockPayload.getData()).thenReturn(ByteString.copyFromUtf8(KEY_SETS_CONFIG));
    when(mockClient.getParameterVersion(any(ParameterVersionName.class))).thenReturn(mockParam);
    parameterNameCaptor = ArgumentCaptor.forClass(ParameterVersionName.class);

    keysetConfigCache =
        new KeysetConfigCache(
            PROJECT_ID, ENVIRONMENT, keysetConfigMap, mockClient, new LogMetricHelper("test"));
  }

  @Test
  public void validateParameterNameTest() throws Exception {
    keysetConfigCache.get(SET_NAME_1);
    verify(mockClient).getParameterVersion(parameterNameCaptor.capture());

    assertThat(parameterNameCaptor.getValue().getProject()).isEqualTo(PROJECT_ID);
    assertThat(parameterNameCaptor.getValue().getLocation()).isEqualTo("global");
    assertThat(parameterNameCaptor.getValue().getParameter())
        .isEqualTo("scp-environ-KEY_SETS_CONFIG");
    assertThat(parameterNameCaptor.getValue().getParameterVersion()).isEqualTo("v1");
  }

  @Test
  public void validateCacheWorksTest() throws Exception {
    keysetConfigCache.get(SET_NAME_1);
    keysetConfigCache.get(SET_NAME_1);
    var config = keysetConfigCache.get(SET_NAME_1);

    assertThat(config).isEqualTo(KEY_SET_CONFIG_1);
    verify(mockClient).getParameterVersion(any(ParameterVersionName.class));
  }

  @Test
  public void failoverToEnvironmentVariable_Test() throws Exception {
    keysetConfigCache.get(SET_NAME_2);
    keysetConfigCache.get(SET_NAME_2);
    var config = keysetConfigCache.get(SET_NAME_2);

    assertThat(config).isEqualTo(KEY_SET_CONFIG_FAIL_2);
    verify(mockClient).getParameterVersion(any(ParameterVersionName.class));
  }

  @Test
  public void failoverToEnvironmentVariable_missingKeyset_test() throws Exception {
    keysetConfigCache.get(SET_NAME_2);
    keysetConfigCache.get(SET_NAME_2);
    assertThrows(ServiceException.class, () -> keysetConfigCache.get("notThere"));
  }
}
