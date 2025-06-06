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

package com.google.scp.shared.clients.configclient.gcp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.protobuf.ByteString;
import com.google.scp.shared.clients.configclient.model.WorkerParameter;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class GcpParameterClientTest {

  private static final String PROJECT_ID = "projectId1";
  private static final String ENVIRONMENT = "environment";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SecretManagerServiceClientProxy secretManagerServiceClientProxy;
  @Mock private GcpMetadataServiceClient metadataServiceClient;

  private GcpParameterClient parameterClient;

  @Before
  public void setUp() {
    parameterClient =
        new GcpParameterClient(secretManagerServiceClientProxy, metadataServiceClient, PROJECT_ID);
  }

  @Test
  public void getParameter_returnsParameter() throws Exception {
    String secretName = getSecretName("scp-environment-JOB_PUBSUB_TOPIC_ID");
    String secretValue = "testVal";
    ByteString data = ByteString.copyFrom(secretValue.getBytes(StandardCharsets.UTF_8));
    when(metadataServiceClient.getMetadata(any())).thenReturn(Optional.of(ENVIRONMENT));
    when(secretManagerServiceClientProxy.accessSecretVersion(secretName))
        .thenReturn(
            AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(data))
                .build());

    Optional<String> val = parameterClient.getParameter(WorkerParameter.JOB_PUBSUB_TOPIC_ID.name());

    assertThat(val).isPresent();
    assertThat(val.get()).isEqualTo(secretValue);
    verify(metadataServiceClient).getMetadata(eq("scp-environment"));
    verify(secretManagerServiceClientProxy).accessSecretVersion(secretName);
  }

  @Test
  public void getParameter_withPrefixAndEnvironment() throws Exception {
    String secretName = getSecretName("prefix-environment-param");
    String secretValue = "testVal";
    ByteString data = ByteString.copyFrom(secretValue.getBytes(StandardCharsets.UTF_8));
    when(metadataServiceClient.getMetadata(any())).thenReturn(Optional.of(ENVIRONMENT));
    when(secretManagerServiceClientProxy.accessSecretVersion(secretName))
        .thenReturn(
            AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(data))
                .build());

    Optional<String> val =
        parameterClient.getParameter(
            "param", Optional.of("prefix"), /* includeEnvironmentParam= */ true);

    assertThat(val).isPresent();
    assertThat(val.get()).isEqualTo(secretValue);
    verify(metadataServiceClient).getMetadata(eq("scp-environment"));
    verify(secretManagerServiceClientProxy).accessSecretVersion(secretName);
  }

  @Test
  public void getParameter_withNoPrefixNoEnvironment() throws Exception {
    String secretName = getSecretName("testParam");
    String secretValue = "testVal";
    ByteString data = ByteString.copyFrom(secretValue.getBytes(StandardCharsets.UTF_8));
    when(metadataServiceClient.getMetadata(any())).thenReturn(Optional.of(ENVIRONMENT));
    when(secretManagerServiceClientProxy.accessSecretVersion(secretName))
        .thenReturn(
            AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(data))
                .build());

    Optional<String> val =
        parameterClient.getParameter(
            "testParam", Optional.empty(), /* includeEnvironmentParam= */ false);

    assertThat(val).isPresent();
    assertThat(val.get()).isEqualTo(secretValue);
    verify(metadataServiceClient, never()).getMetadata(eq("scp-environment"));
    verify(secretManagerServiceClientProxy).accessSecretVersion(secretName);
  }

  @Test
  public void getParameter_notFound() throws Exception {
    String secretName = getSecretName("scp-environment-JOB_QUEUE");
    when(metadataServiceClient.getMetadata(any())).thenReturn(Optional.of(ENVIRONMENT));
    when(secretManagerServiceClientProxy.accessSecretVersion(secretName))
        .thenThrow(
            new NotFoundException(
                new StatusRuntimeException(Status.NOT_FOUND),
                GrpcStatusCode.of(Code.NOT_FOUND),
                /* retryable= */ false));

    Optional<String> val = parameterClient.getParameter(WorkerParameter.JOB_QUEUE.name());

    assertThat(val).isEmpty();
    verify(secretManagerServiceClientProxy).accessSecretVersion(secretName);
  }

  @Test
  public void getParameter_sourceUpdated_returnsCachedValue() throws Exception {
    // Given
    String secretName = getSecretName("scp-environment-JOB_PUBSUB_TOPIC_ID");
    String originalValue = "original-value";
    String updatedValue = "updated-value";
    ByteString originalData = ByteString.copyFrom(originalValue.getBytes(StandardCharsets.UTF_8));
    ByteString updatedData = ByteString.copyFrom(updatedValue.getBytes(StandardCharsets.UTF_8));

    doReturn(Optional.of(ENVIRONMENT)).when(metadataServiceClient).getMetadata(any());
    doReturn(
            AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(originalData))
                .build(),
            AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(updatedData))
                .build())
        .when(secretManagerServiceClientProxy)
        .accessSecretVersion(secretName);

    // When
    Optional<String> firstReturned =
        parameterClient.getParameter(WorkerParameter.JOB_PUBSUB_TOPIC_ID.name());
    Optional<String> secondReturned =
        parameterClient.getParameter(WorkerParameter.JOB_PUBSUB_TOPIC_ID.name());

    // Then
    assertThat(firstReturned).hasValue(originalValue);
    assertThat(secondReturned).hasValue(originalValue);
  }

  @Test
  public void getLatestParameter_sourceUpdated_returnsUpdatedValue() throws Exception {
    // Given
    String secretName = getSecretName("scp-environment-JOB_PUBSUB_TOPIC_ID");
    String originalValue = "original-value";
    String updatedValue = "updated-value";
    ByteString originalData = ByteString.copyFrom(originalValue.getBytes(StandardCharsets.UTF_8));
    ByteString updatedData = ByteString.copyFrom(updatedValue.getBytes(StandardCharsets.UTF_8));

    doReturn(Optional.of(ENVIRONMENT)).when(metadataServiceClient).getMetadata(any());
    doReturn(
            AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(originalData))
                .build(),
            AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(updatedData))
                .build())
        .when(secretManagerServiceClientProxy)
        .accessSecretVersion(secretName);

    // When
    Optional<String> firstReturned =
        parameterClient.getParameter(WorkerParameter.JOB_PUBSUB_TOPIC_ID.name());
    Optional<String> secondReturned =
        parameterClient.getParameter(WorkerParameter.JOB_PUBSUB_TOPIC_ID.name());

    // Then
    assertThat(firstReturned).hasValue(originalValue);
    assertThat(secondReturned).hasValue(originalValue);
  }

  private String getSecretName(String parameter) {
    return String.format("projects/%s/secrets/%s/versions/latest", PROJECT_ID, parameter);
  }
}
