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

package com.google.scp.coordinator.keymanagement.keyhosting.tasks.v1;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.acai.Acai;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeySetConfigMap;
import com.google.scp.coordinator.keymanagement.keyhosting.common.KeySetConfig;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ApiTaskTestBase;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.ResponseContext;
import com.google.scp.coordinator.keymanagement.testutils.InMemoryTestEnv;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetKeysetMetadataResponseProto.GetKeysetMetadataResponse;
import com.google.scp.shared.api.exception.ServiceException;
import java.util.regex.Matcher;
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
public class GetKeysetMetadataTaskTest extends ApiTaskTestBase {

  @Rule public final Acai acai = new Acai(InMemoryTestEnv.class);
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Inject private GetKeysetMetadataTask task;
  @Inject @KeySetConfigMap ImmutableMap<String, KeySetConfig> keyConfigMap;

  @Mock private RequestContext request;
  @Mock private Matcher matcher;

  @Mock(answer = CALLS_REAL_METHODS)
  private ResponseContext response;

  @Before
  public void setUp() throws Exception {
    super.task = spy(this.task);
  }

  @Test
  public void missingSetThrowsExceptionTest() {
    doReturn("missing").when(matcher).group("name");
    assertThrows(ServiceException.class, () -> task.execute(matcher, request, response));
  }

  @Test
  public void noOverlapConfigTest() throws Exception {
    doReturn("noOverlap").when(matcher).group("name");
    task.execute(matcher, request, response);

    var metadata = verifyResponse(response);
    assertThat(metadata.getActiveKeyCount()).isEqualTo(keyConfigMap.get("noOverlap").getCount());
    assertThat(metadata.getActiveKeyCadenceDays())
        .isEqualTo(keyConfigMap.get("noOverlap").getValidityInDays());
    assertThat(metadata.getBackfillDays())
        .isEqualTo(keyConfigMap.get("noOverlap").getBackfillDays());
  }

  @Test
  public void overlapConfigTest() throws Exception {
    doReturn("overlap").when(matcher).group("name");
    task.execute(matcher, request, response);

    var metadata = verifyResponse(response);
    assertThat(metadata.getActiveKeyCount()).isEqualTo(keyConfigMap.get("overlap").getCount() * 4);
    assertThat(metadata.getActiveKeyCadenceDays()).isEqualTo(2);
    assertThat(metadata.getBackfillDays())
        .isEqualTo(keyConfigMap.get("overlap").getBackfillDays());
  }

  @Test
  public void backfillConfigTest() throws Exception {
    doReturn("backfill").when(matcher).group("name");
    task.execute(matcher, request, response);

    var metadata = verifyResponse(response);
    assertThat(metadata.getActiveKeyCount()).isEqualTo(keyConfigMap.get("backfill").getCount());
    assertThat(metadata.getBackfillDays())
        .isEqualTo(keyConfigMap.get("backfill").getBackfillDays());
  }

  private static GetKeysetMetadataResponse verifyResponse(ResponseContext response)
      throws Exception {
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(response).setBody(body.capture());
    var countBuilder = GetKeysetMetadataResponse.newBuilder();
    JsonFormat.parser().merge(body.getValue(), countBuilder);
    return countBuilder.build();
  }
}
