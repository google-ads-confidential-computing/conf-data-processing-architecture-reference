/*
 * Copyright 2022 Google LLC
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

package com.google.scp.coordinator.keymanagement.keyhosting.service.aws;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.scp.coordinator.keymanagement.keyhosting.service.common.KeyHostingUtil.getMaxAgeCacheControlValue;
import static com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb.DEFAULT_SET_NAME;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.SERVICE_ERROR;
import static com.google.scp.shared.api.model.Code.INTERNAL;
import static com.google.scp.shared.api.util.RequestUtil.getVariableFromPath;
import static java.time.Duration.between;
import static java.time.Instant.now;
import static java.time.Instant.ofEpochMilli;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.CacheControlMaximum;
import com.google.scp.coordinator.keymanagement.keyhosting.common.Annotations.KeyLimit;
import com.google.scp.coordinator.keymanagement.keyhosting.service.common.GetActivePublicKeysResponseWithHeaders;
import com.google.scp.coordinator.keymanagement.shared.converter.EncodedPublicKeyListConverter;
import com.google.scp.coordinator.keymanagement.shared.converter.EncryptionKeyConverter;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetActivePublicKeysResponseProto.GetActivePublicKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.GetEncryptionKeyRequestProto.GetEncryptionKeyRequest;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.ListRecentEncryptionKeysRequestProto.ListRecentEncryptionKeysRequest;
import com.google.scp.coordinator.protos.keymanagement.keyhosting.api.v1.ListRecentEncryptionKeysResponseProto.ListRecentEncryptionKeysResponse;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Organizes tasks handling different parts of Key Hosting business logic */
public final class KeyService {

  private static final String KEY_ID_FIELD = "keyId";
  private static final String ENCRYPTION_KEY_RESOURCE_PATTERN = "encryptionKeys/:" + KEY_ID_FIELD;

  private final Logger logger = LoggerFactory.getLogger(KeyService.class);

  private final KeyDb keyDb;
  private final int keyLimit;
  private final EncodedPublicKeyListConverter encodedPublicKeyListConverter;
  private final Long cacheControlMaximum;

  @Inject
  public KeyService(
      KeyDb keyDb,
      @KeyLimit Integer keyLimit,
      EncodedPublicKeyListConverter encodedPublicKeyListConverter,
      @CacheControlMaximum Long cacheControlMaximum) {
    this.keyDb = keyDb;
    this.keyLimit = keyLimit;
    this.encodedPublicKeyListConverter = encodedPublicKeyListConverter;
    this.cacheControlMaximum = cacheControlMaximum;
  }

  /**
   * Implements a GET request for all active public keys, invoking task to get from database and
   * converting to response model
   */
  public GetActivePublicKeysResponseWithHeaders getActivePublicKeys() throws ServiceException {
    try {
      ImmutableList<EncryptionKey> keys =
          keyDb.getActiveKeysWithPublicKey(DEFAULT_SET_NAME, keyLimit);
      GetActivePublicKeysResponse.Builder responseBuilder =
          GetActivePublicKeysResponse.newBuilder()
              .addAllKeys(encodedPublicKeyListConverter.convert(keys));

      GetActivePublicKeysResponseWithHeaders.Builder responseWithHeadersBuilder =
          GetActivePublicKeysResponseWithHeaders.builder()
              .setGetActivePublicKeysResponse(responseBuilder.build());
      if (keys.isEmpty()) {
        logger.error("No active public keys available");
      } else {
        // All returned keys should contain same expiration time, so use first to calculate max age
        // cache control value
        responseWithHeadersBuilder.setCacheControlMaxAge(
            getMaxAgeCacheControlValue(
                calculateCacheControlValue(keys.get(0).getExpirationTime(), cacheControlMaximum)));
      }
      return responseWithHeadersBuilder.build();
    } catch (ServiceException serviceException) {
      throw serviceException;
    } catch (Exception exception) {
      String message = "Error encountered while getting public keys.";
      logger.error(message, exception);
      throw new ServiceException(INTERNAL, SERVICE_ERROR.name(), message, exception);
    }
  }

  /**
   * @see ListRecentEncryptionKeysRequest
   */
  public ListRecentEncryptionKeysResponse listRecentKeys(ListRecentEncryptionKeysRequest request)
      throws ServiceException {
    Stream<EncryptionKey> keys = keyDb.listRecentKeys(request.getMaxAgeSeconds());
    return ListRecentEncryptionKeysResponse.newBuilder()
        .addAllKeys(keys.map(EncryptionKeyConverter::toApiEncryptionKey).collect(toImmutableList()))
        .build();
  }

  /** Implements a GET request for a specific encryption key resource. */
  public com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyProto
          .EncryptionKey
      getEncryptionKey(GetEncryptionKeyRequest request) throws ServiceException {
    return EncryptionKeyConverter.toApiEncryptionKey(keyDb.getKey(getEncryptionKeyId(request)));
  }

  /**
   * Calculates remaining validity of an EncryptionKey as the difference between its expirationTime
   * of the keys and the current time now(). The result is capped to a given maximum time.
   *
   * @param expirationTime of an EncryptionKey to be served by the Public Key Service
   * @param maximumTime in seconds for the returned value
   * @return Long value of seconds
   */
  private static Long calculateCacheControlValue(Long expirationTime, Long maximumTime) {
    long remainingSeconds = between(now(), ofEpochMilli(expirationTime)).toSeconds();
    return Math.min(remainingSeconds, maximumTime);
  }

  /** Extracts the encryption key ID from the resource name in the request. */
  private static String getEncryptionKeyId(GetEncryptionKeyRequest request)
      throws ServiceException {
    return getVariableFromPath(ENCRYPTION_KEY_RESOURCE_PATTERN, request.getName(), KEY_ID_FIELD);
  }
}
