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

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_BAD_GATEWAY;
import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_SERVER_ERROR;
import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.rpc.ApiException;
import com.google.common.collect.ImmutableSet;
import com.google.scp.operator.cpio.cryptoclient.HybridEncryptionKeyService.KeyFetchException;
import com.google.scp.shared.api.exception.ServiceException;
import java.net.ConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class to parse external/internal service exceptions to KeyFetchException. */
public final class KeyFetchExceptionUtils {

  private static final Logger logger = LoggerFactory.getLogger(KeyFetchExceptionUtils.class);

  private static final String WIP_AUTH_EXCEPTION = "OAuthException";
  private static final String INVALID_WIP_FORMAT_CODE = "invalid_request";
  private static final String INVALID_WIP_CODE = "invalid_target";
  private static final String WIP_CONDITION_FAILED_CODE = "unauthorized_client";
  private static final String INVALID_CYPHERTEXT = "ciphertext is invalid";
  private static final String QUOTA_LIMIT_CODE = "quota_exceeded";
  private static final int BAD_REQUEST_STATUS_CODE = 400;
  private static final int FORBIDDEN_STATUS_CODE = 403;

  private static final ImmutableSet<Integer> RETRYABLE_HTTP_STATUS_CODES =
      ImmutableSet.of(
          STATUS_CODE_SERVER_ERROR /* 500 */,
          STATUS_CODE_SERVICE_UNAVAILABLE /* 503 */,
          STATUS_CODE_BAD_GATEWAY /* 504 */);

  private KeyFetchExceptionUtils() {}

  /** Handles potential ServiceException errors and converts to KeyFetchException */
  public static KeyFetchException parseServiceException(Throwable e) {
    logger.error("Exception for key fetching: ", e);
    if (e.getCause() instanceof ServiceException) {
      switch (((ServiceException) e.getCause()).getErrorCode()) {
        case NOT_FOUND:
          return new KeyFetchException(e, ErrorReason.KEY_NOT_FOUND);
        case PERMISSION_DENIED:
        case UNAUTHENTICATED:
          return new KeyFetchException(e, ErrorReason.PERMISSION_DENIED);
        case INTERNAL:
          return new KeyFetchException(e, ErrorReason.INTERNAL);
        case UNAVAILABLE:
        case DEADLINE_EXCEEDED:
          return new KeyFetchException(e, ErrorReason.KEY_SERVICE_UNAVAILABLE);
        default:
          return new KeyFetchException(e, ErrorReason.UNKNOWN_ERROR);
      }
    }
    return new KeyFetchException(e, ErrorReason.UNKNOWN_ERROR);
  }

  /**
   * Handles potential GCP Cloud KMS errors and converts to EncryptionKeyFetchingServiceException
   */
  public static KeyFetchException parseGrpcException(Throwable e) {
    if (e == null) {
      return new KeyFetchException(e, ErrorReason.KEY_DECRYPTION_ERROR);
    }
    logger.error("Exception for key decryption: ", e);
    logger.info("End of exception log.");

    // First check WIP failures.
    if (e.getClass().getSimpleName().equals(WIP_AUTH_EXCEPTION)) {
      return parseWipAuthorizationException(e);
    }

    // GCP KMS Server returns GoogleJsonResponseException for API errors, so we check the error code
    // and message.
    if (e instanceof GoogleJsonResponseException) {
      return parseGoogleJsonResponseException(e);
    }

    //  Check for connection exceptions.
    if (e instanceof ConnectException) {
      return new KeyFetchException(e, ErrorReason.KEY_SERVICE_UNAVAILABLE);
    }

    // Check for ApiException from gRPC calls.
    if (e instanceof ApiException) {
      return parseApiException(e);
    }

    if (e.getCause() == null) {
      return new KeyFetchException(e, ErrorReason.KEY_DECRYPTION_ERROR);
    }

    return parseGrpcException(e.getCause());
  }

  /**
   * Handles potential Wip Authorization errors from GCP KMS Client and converts to
   * KeyFetchException.
   */
  public static KeyFetchException parseWipAuthorizationException(Throwable e) {
    logger.error("Exception for key fetching: ", e);
    String msg = "WIP Exception occurred.";
    if (e.getMessage().contains(INVALID_WIP_CODE)) {
      msg = "WIP parameter invalid.";
    } else if (e.getMessage().contains(INVALID_WIP_FORMAT_CODE)) {
      msg = "WIP parameter in an invalid format.";
    } else if (e.getMessage().contains(WIP_CONDITION_FAILED_CODE)) {
      msg = "WIP conditions failed.";
    } else if (e.getMessage().contains(QUOTA_LIMIT_CODE)) {
      msg = "Quota exceeded.";
      return new KeyFetchException(msg, ErrorReason.RESOURCE_EXHAUSTED, e);
    }
    return new KeyFetchException(msg, ErrorReason.UNAUTHENTICATED, e);
  }

  // Handles potential GoogleJsonResponseException errors and converts to KeyFetchException.
  public static KeyFetchException parseGoogleJsonResponseException(Throwable e) {
    logger.error("Exception for key fetching: ", e);
    var apiResponse = (GoogleJsonResponseException) e;
    int statusCode = apiResponse.getStatusCode();
    String msg = "GCP KMS Client Exception occurred.";
    if (RETRYABLE_HTTP_STATUS_CODES.contains(statusCode)) {
      msg = "Retryable error occurred when trying to contact GCP KMS.";
      return new KeyFetchException(msg, ErrorReason.KEY_SERVICE_UNAVAILABLE, e);
    } else if (statusCode == BAD_REQUEST_STATUS_CODE) {
      if (apiResponse.getDetails().getMessage() != null
          && apiResponse.getDetails().getMessage().contains(INVALID_CYPHERTEXT)) {
        msg = "Cloud KMS marked DEK as invalid and cannot be decrypted.";
      } else {
        msg = "KEK could not decrypt data, most likely incorrect KEK.";
      }
    } else if (statusCode == FORBIDDEN_STATUS_CODE) {
      msg = "Permission denied when trying to use KEK.";
    }
    return new KeyFetchException(msg, ErrorReason.KEY_DECRYPTION_ERROR, e);
  }

  // Handles potential ApiException errors and converts to KeyFetchException.
  public static KeyFetchException parseApiException(Throwable e) {
    logger.error("Exception for key fetching: ", e);
    switch (((ApiException) e).getStatusCode().getCode()) {
      case NOT_FOUND:
        return new KeyFetchException(e, ErrorReason.KEY_NOT_FOUND);
      case PERMISSION_DENIED:
        return new KeyFetchException(e, ErrorReason.PERMISSION_DENIED);
      case UNAUTHENTICATED:
        return new KeyFetchException(e, ErrorReason.UNAUTHENTICATED);
      case INTERNAL:
        return new KeyFetchException(e, ErrorReason.INTERNAL);
      case UNAVAILABLE:
        return new KeyFetchException(e, ErrorReason.KEY_SERVICE_UNAVAILABLE);
      case DEADLINE_EXCEEDED:
        return new KeyFetchException(e, ErrorReason.DEADLINE_EXCEEDED);
      case RESOURCE_EXHAUSTED:
        return new KeyFetchException(e, ErrorReason.RESOURCE_EXHAUSTED);
      case INVALID_ARGUMENT:
        return new KeyFetchException(e, ErrorReason.INVALID_ARGUMENT);
      default:
        return new KeyFetchException(e, ErrorReason.UNKNOWN_ERROR);
    }
  }
}
