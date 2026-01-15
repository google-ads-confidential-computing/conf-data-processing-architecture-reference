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

package com.google.scp.operator.cpio.cryptoclient.model;

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_SERVER_ERROR;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiException;
import com.google.scp.operator.cpio.cryptoclient.EncryptionKeyFetchingService.EncryptionKeyFetchingServiceException;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import com.google.scp.shared.testutils.gcp.OAuthException;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KeyFetchExceptionUtilsTest {

  @Test
  public void parseServiceException_ErrorCode_KEY_NOT_FOUND() throws Exception {
    var ex =
        new EncryptionKeyFetchingServiceException(
            new ServiceException(Code.NOT_FOUND, "test", "test"));

    var result = KeyFetchExceptionUtils.parseServiceException(ex);

    assertEquals(ErrorReason.KEY_NOT_FOUND, result.getReason());
  }

  @Test
  public void parseServiceException_ErrorCode_PERMISSION_DENIED() throws Exception {
    var ex =
        new EncryptionKeyFetchingServiceException(
            new ServiceException(Code.PERMISSION_DENIED, "test", "test"));

    var result = KeyFetchExceptionUtils.parseServiceException(ex);

    assertEquals(ErrorReason.PERMISSION_DENIED, result.getReason());
  }

  @Test
  public void parseServiceException_ErrorCode_INTERNAL() throws Exception {
    var ex =
        new EncryptionKeyFetchingServiceException(
            new ServiceException(Code.INTERNAL, "test", "test"));

    var result = KeyFetchExceptionUtils.parseServiceException(ex);

    assertEquals(ErrorReason.INTERNAL, result.getReason());
  }

  @Test
  public void parseServiceException_ErrorCode_Null() throws Exception {
    var ex = new EncryptionKeyFetchingServiceException(new Exception());

    var result = KeyFetchExceptionUtils.parseServiceException(ex);

    assertEquals(ErrorReason.UNKNOWN_ERROR, result.getReason());
  }

  @Test
  public void parseServiceException_ErrorCode_UNAVAILABLE() throws Exception {
    var ex =
        new EncryptionKeyFetchingServiceException(
            new ServiceException(Code.UNAVAILABLE, "test", "test"));

    var result = KeyFetchExceptionUtils.parseServiceException(ex);

    assertEquals(ErrorReason.KEY_SERVICE_UNAVAILABLE, result.getReason());
  }

  @Test
  public void parseServiceException_ErrorCode_DEADLINE_EXCEEDED() throws Exception {
    var ex =
        new EncryptionKeyFetchingServiceException(
            new ServiceException(Code.DEADLINE_EXCEEDED, "test", "test"));

    var result = KeyFetchExceptionUtils.parseServiceException(ex);

    assertEquals(ErrorReason.KEY_SERVICE_UNAVAILABLE, result.getReason());
  }

  @Test
  public void parseServiceException_ErrorCode_UNKNOWN() throws Exception {
    var ex =
        new EncryptionKeyFetchingServiceException(
            new ServiceException(Code.UNKNOWN, "test", "test"));

    var result = KeyFetchExceptionUtils.parseServiceException(ex);

    assertEquals(ErrorReason.UNKNOWN_ERROR, result.getReason());
  }

  @Test
  public void parseGrpcException_handleNullException() {
    var result = KeyFetchExceptionUtils.parseGrpcException(null);

    assertEquals(ErrorReason.KEY_DECRYPTION_ERROR, result.getReason());
  }

  @Test
  public void parseGrpcException_WipException() {
    var ex = new GeneralSecurityException(new OAuthException("unauthorized_client"));

    var result = KeyFetchExceptionUtils.parseGrpcException(ex);

    assertEquals(ErrorReason.UNAUTHENTICATED, result.getReason());
  }

  @Test
  public void parseGrpcException_WipExceptionInsideRuntimeException() {
    var ex =
        new GeneralSecurityException(
            new RuntimeException(new OAuthException("unauthorized_client")));

    var result = KeyFetchExceptionUtils.parseGrpcException(ex);

    assertEquals(ErrorReason.UNAUTHENTICATED, result.getReason());
  }

  @Test
  public void parseGrpcException_GoogleJsonResponseException() {
    var ex =
        new GeneralSecurityException(
            new GoogleJsonResponseException(
                new HttpResponseException.Builder(400, "error", new HttpHeaders()),
                new GoogleJsonError()));

    var result = KeyFetchExceptionUtils.parseGrpcException(ex);

    assertEquals(ErrorReason.KEY_DECRYPTION_ERROR, result.getReason());
  }

  @Test
  public void parseGrpcException_ConnectException() {
    var ex = new GeneralSecurityException(new ConnectException("Connection exception triggered."));

    var result = KeyFetchExceptionUtils.parseGrpcException(ex);

    assertEquals(ErrorReason.KEY_SERVICE_UNAVAILABLE, result.getReason());
  }

  @Test
  public void parseGrpcException_ApiException() {
    var ex =
        new GeneralSecurityException(
            new ApiException(
                new RuntimeException("Key not found."),
                GrpcStatusCode.of(io.grpc.Status.Code.NOT_FOUND),
                false));

    var result = KeyFetchExceptionUtils.parseGrpcException(ex);

    assertEquals(ErrorReason.KEY_NOT_FOUND, result.getReason());
  }

  @Test
  public void parseWipAuthorizationException_authFailed() {
    var ex = new OAuthException("unauthorized_client");

    var result = KeyFetchExceptionUtils.parseWipAuthorizationException(ex);

    assertThat(result).hasMessageThat().isEqualTo("WIP conditions failed.");
    assertEquals(ErrorReason.UNAUTHENTICATED, result.getReason());
  }

  @Test
  public void parseWipAuthorizationException_invalidWipFormat() {
    var ex = new OAuthException("invalid_request");

    var result = KeyFetchExceptionUtils.parseWipAuthorizationException(ex);

    assertThat(result).hasMessageThat().isEqualTo("WIP parameter in an invalid format.");
    assertEquals(ErrorReason.UNAUTHENTICATED, result.getReason());
  }

  @Test
  public void parseWipAuthorizationException_invalidWip() {
    var ex = new OAuthException("invalid_target");

    var result = KeyFetchExceptionUtils.parseWipAuthorizationException(ex);

    assertThat(result).hasMessageThat().isEqualTo("WIP parameter invalid.");
    assertEquals(ErrorReason.UNAUTHENTICATED, result.getReason());
  }

  @Test
  public void parseWipAuthorizationException_quotaExceeded() {
    var ex = new OAuthException("quota_exceeded");

    var result = KeyFetchExceptionUtils.parseWipAuthorizationException(ex);

    assertThat(result).hasMessageThat().isEqualTo("Quota exceeded.");
    assertEquals(ErrorReason.RESOURCE_EXHAUSTED, result.getReason());
  }

  @Test
  public void parseGoogleJsonResponseException_invalidKek() {
    var details = new GoogleJsonError();
    details.setCode(400);
    details.setMessage("");
    var ex =
        new GoogleJsonResponseException(
            new HttpResponseException.Builder(400, "error", new HttpHeaders()), details);

    var result = KeyFetchExceptionUtils.parseGoogleJsonResponseException(ex);

    assertThat(result)
        .hasMessageThat()
        .isEqualTo("KEK could not decrypt data, most likely incorrect KEK.");
    assertEquals(ErrorReason.KEY_DECRYPTION_ERROR, result.getReason());
  }

  @Test
  public void parseGoogleJsonResponseException_invalidDek() {
    var details = new GoogleJsonError();
    details.setCode(400);
    details.setMessage("Decryption failed: the ciphertext is invalid");
    var ex =
        new GoogleJsonResponseException(
            new HttpResponseException.Builder(400, "error", new HttpHeaders()), details);

    var result = KeyFetchExceptionUtils.parseGoogleJsonResponseException(ex);

    assertThat(result)
        .hasMessageThat()
        .isEqualTo("Cloud KMS marked DEK as invalid and cannot be decrypted.");
    assertEquals(ErrorReason.KEY_DECRYPTION_ERROR, result.getReason());
  }

  @Test
  public void parseGoogleJsonResponseException_kekPermissionDenied() {
    var details = new GoogleJsonError();
    details.setCode(403);
    details.setMessage("");
    var ex =
        new GoogleJsonResponseException(
            new HttpResponseException.Builder(403, "error", new HttpHeaders()), details);

    var result = KeyFetchExceptionUtils.parseGoogleJsonResponseException(ex);

    assertThat(result).hasMessageThat().isEqualTo("Permission denied when trying to use KEK.");
    assertEquals(ErrorReason.KEY_DECRYPTION_ERROR, result.getReason());
  }

  @Test
  public void parseGoogleJsonResponseException_serverError() throws Exception {
    var jsonException =
        new GoogleJsonResponseException(
            new HttpResponseException.Builder(
                STATUS_CODE_SERVER_ERROR, "Internal Server Error", new HttpHeaders()),
            new GoogleJsonError());

    var result = KeyFetchExceptionUtils.parseGoogleJsonResponseException(jsonException);

    assertEquals(ErrorReason.KEY_SERVICE_UNAVAILABLE, result.getReason());
  }

  @Test
  public void parseApiException_GrpcStatusCode_NOT_FOUND() throws Exception {
    var ex =
        new ApiException(
            new RuntimeException("Key not found exception triggered."),
            GrpcStatusCode.of(io.grpc.Status.Code.NOT_FOUND),
            false);

    var result = KeyFetchExceptionUtils.parseApiException(ex);

    assertEquals(ErrorReason.KEY_NOT_FOUND, result.getReason());
  }

  @Test
  public void parseApiException_GrpcStatusCode_PERMISSION_DENIED() throws Exception {
    var ex =
        new ApiException(
            new RuntimeException("Permission denied exception triggered."),
            GrpcStatusCode.of(io.grpc.Status.Code.PERMISSION_DENIED),
            false);

    var result = KeyFetchExceptionUtils.parseApiException(ex);

    assertEquals(ErrorReason.PERMISSION_DENIED, result.getReason());
  }

  @Test
  public void parseApiException_GrpcStatusCode_UNAUTHENTICATED() throws Exception {
    var ex =
        new ApiException(
            new RuntimeException("Unauthenticated exception triggered."),
            GrpcStatusCode.of(io.grpc.Status.Code.UNAUTHENTICATED),
            false);

    var result = KeyFetchExceptionUtils.parseApiException(ex);

    assertEquals(ErrorReason.UNAUTHENTICATED, result.getReason());
  }

  @Test
  public void parseApiException_GrpcStatusCode_INTERNAL() throws Exception {
    var ex =
        new ApiException(
            new RuntimeException("Internal exception triggered."),
            GrpcStatusCode.of(io.grpc.Status.Code.INTERNAL),
            false);

    var result = KeyFetchExceptionUtils.parseApiException(ex);

    assertEquals(ErrorReason.INTERNAL, result.getReason());
  }

  @Test
  public void parseApiException_GrpcStatusCode_UNAVAILABLE() throws Exception {
    var ex =
        new ApiException(
            new RuntimeException("Unavailable exception triggered."),
            GrpcStatusCode.of(io.grpc.Status.Code.UNAVAILABLE),
            false);

    var result = KeyFetchExceptionUtils.parseApiException(ex);

    assertEquals(ErrorReason.KEY_SERVICE_UNAVAILABLE, result.getReason());
  }

  @Test
  public void parseApiException_GrpcStatusCode_DEADLINE_EXCEEDED() throws Exception {
    var ex =
        new ApiException(
            new RuntimeException("Deadline exceeded exception triggered."),
            GrpcStatusCode.of(io.grpc.Status.Code.DEADLINE_EXCEEDED),
            false);

    var result = KeyFetchExceptionUtils.parseApiException(ex);

    assertEquals(ErrorReason.DEADLINE_EXCEEDED, result.getReason());
  }

  @Test
  public void parseApiException_GrpcStatusCode_RESOURCE_EXHAUSTED() throws Exception {
    var ex =
        new ApiException(
            new RuntimeException("Resource exhausted exception triggered."),
            GrpcStatusCode.of(io.grpc.Status.Code.RESOURCE_EXHAUSTED),
            false);

    var result = KeyFetchExceptionUtils.parseApiException(ex);

    assertEquals(ErrorReason.RESOURCE_EXHAUSTED, result.getReason());
  }

  @Test
  public void parseApiException_GrpcStatusCode_INVALID_ARGUMENT() throws Exception {
    var ex =
        new ApiException(
            new RuntimeException("Invalid argument exception triggered."),
            GrpcStatusCode.of(io.grpc.Status.Code.INVALID_ARGUMENT),
            false);

    var result = KeyFetchExceptionUtils.parseApiException(ex);

    assertEquals(ErrorReason.INVALID_ARGUMENT, result.getReason());
  }

  @Test
  public void parseApiException_GrpcStatusCode_OtherApiException() throws Exception {
    var ex =
        new ApiException(
            new RuntimeException("Other exception triggered."),
            GrpcStatusCode.of(io.grpc.Status.Code.CANCELLED),
            false);

    var result = KeyFetchExceptionUtils.parseApiException(ex);

    assertEquals(ErrorReason.UNKNOWN_ERROR, result.getReason());
  }
}
