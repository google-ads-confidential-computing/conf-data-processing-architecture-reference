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

package com.google.scp.operator.cpio.cryptoclient;

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_BAD_GATEWAY;
import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_SERVER_ERROR;
import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.rpc.ApiException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.KeyDataProto.KeyData;
import com.google.scp.operator.cpio.cryptoclient.Annotations.CoordinatorAAead;
import com.google.scp.operator.cpio.cryptoclient.Annotations.CoordinatorBAead;
import com.google.scp.operator.cpio.cryptoclient.EncryptionKeyFetchingService.EncryptionKeyFetchingServiceException;
import com.google.scp.operator.cpio.cryptoclient.model.ErrorReason;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.crypto.tink.CloudAeadSelector;
import com.google.scp.shared.util.KeySplitUtil;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation for retrieving and decrypting keys from the KMS. This version uses the encryption
 * key API which also supports multi-party keys.
 */
public final class MultiPartyHybridEncryptionKeyServiceImpl implements HybridEncryptionKeyService {

  private static final Logger logger =
      LoggerFactory.getLogger(MultiPartyHybridEncryptionKeyServiceImpl.class);

  // NOTE: This value is used to extract log-based metrics. So the metrics should be updated
  // accordingly, if this value is changed.
  private static final String SPLIT_KEY_AEAD_DECRYPT_LOG_TEMPLATE =
      "cloud-kms-split-key-aead-decrypt-%s";

  private static final ImmutableSet<Integer> RETRYABLE_HTTP_STATUS_CODES =
      ImmutableSet.of(
          STATUS_CODE_SERVER_ERROR /* 500 */,
          STATUS_CODE_SERVICE_UNAVAILABLE /* 503 */,
          STATUS_CODE_BAD_GATEWAY /* 504 */);

  private static final int MAX_CACHE_SIZE = 100;
  private static final long CACHE_ENTRY_TTL_SEC = 3600;
  private static final int CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();
  private final CloudAeadSelector coordinatorAAeadService;
  private final CloudAeadSelector coordinatorBAeadService;
  private final EncryptionKeyFetchingService coordinatorAEncryptionKeyFetchingService;
  private final EncryptionKeyFetchingService coordinatorBEncryptionKeyFetchingService;
  private final LoadingCache<String, KeysetHandle> keysetHandleCache =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_CACHE_SIZE)
          .expireAfterWrite(CACHE_ENTRY_TTL_SEC, TimeUnit.SECONDS)
          .concurrencyLevel(CONCURRENCY_LEVEL)
          .build(
              new CacheLoader<String, KeysetHandle>() {
                @Override
                public KeysetHandle load(final String keyId) throws KeyFetchException {
                  return createDecrypter(keyId);
                }
              });

  private static final ImmutableSet<ErrorReason> KEY_DECRYPTION_NON_RETRYABLE_FAILURE_REASONS =
      ImmutableSet.of(
          ErrorReason.UNAUTHENTICATED,
          ErrorReason.PERMISSION_DENIED,
          ErrorReason.KEY_NOT_FOUND,
          ErrorReason.INVALID_ARGUMENT);

  private static final RetryConfig DEFAULT_PRIVATE_KEY_DECRYPTION_RETRY_CONFIG =
      RetryConfig.custom()
          .maxAttempts(4)
          .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(400), 2))
          .retryOnException(
              t -> {
                if (t instanceof GeneralSecurityException) {
                  var parsedException = generateKeyFetchExceptionFromGrpcException(t);
                  return !KEY_DECRYPTION_NON_RETRYABLE_FAILURE_REASONS.contains(
                      parsedException.getReason());
                }
                // Do not retry other exceptions
                return false;
              })
          .failAfterMaxAttempts(true)
          .build();

  private Optional<Retry> splitKeyDecryptionRetry = Optional.empty();

  /** Creates a new instance of the {@code MultiPartyHybridEncryptionKeyServiceImpl} class. */
  @Inject
  public MultiPartyHybridEncryptionKeyServiceImpl(
      @CoordinatorAEncryptionKeyFetchingService
          EncryptionKeyFetchingService coordinatorAEncryptionKeyFetchingService,
      @CoordinatorBEncryptionKeyFetchingService
          EncryptionKeyFetchingService coordinatorBEncryptionKeyFetchingService,
      @CoordinatorAAead CloudAeadSelector coordinatorAAeadService,
      @CoordinatorBAead CloudAeadSelector coordinatorBAeadService) {
    this.coordinatorAEncryptionKeyFetchingService = coordinatorAEncryptionKeyFetchingService;
    this.coordinatorBEncryptionKeyFetchingService = coordinatorBEncryptionKeyFetchingService;
    this.coordinatorAAeadService = coordinatorAAeadService;
    this.coordinatorBAeadService = coordinatorBAeadService;
  }

  private MultiPartyHybridEncryptionKeyServiceImpl(
      EncryptionKeyFetchingService coordinatorAEncryptionKeyFetchingService,
      EncryptionKeyFetchingService coordinatorBEncryptionKeyFetchingService,
      CloudAeadSelector coordinatorAAeadService,
      CloudAeadSelector coordinatorBAeadService,
      boolean enablePrivateKeyDecryptionRetries,
      Optional<RetryConfig> privateKeyDecryptionRetryConfig) {

    this(
        coordinatorAEncryptionKeyFetchingService,
        coordinatorBEncryptionKeyFetchingService,
        coordinatorAAeadService,
        coordinatorBAeadService);

    if (enablePrivateKeyDecryptionRetries) {
      if (privateKeyDecryptionRetryConfig.isPresent()) {
        this.splitKeyDecryptionRetry =
            Optional.of(Retry.of("splitKeyDecryptionRetry", privateKeyDecryptionRetryConfig.get()));
      } else {
        this.splitKeyDecryptionRetry =
            Optional.of(
                Retry.of("splitKeyDecryptionRetry", DEFAULT_PRIVATE_KEY_DECRYPTION_RETRY_CONFIG));
      }

      var splitKeyDecryptionRetryEventPublisher =
          this.splitKeyDecryptionRetry.get().getEventPublisher();
      splitKeyDecryptionRetryEventPublisher.onSuccess(
          // This event informs that a call has been retried and a retry was successful. This event
          // is not published when a call was successful without a retry attempt.
          event -> {
            logger.info(String.format(SPLIT_KEY_AEAD_DECRYPT_LOG_TEMPLATE, "RETRY_SUCCESS"));
            logger.info(
                String.format(SPLIT_KEY_AEAD_DECRYPT_LOG_TEMPLATE, "RETRY_SUCCESS_COUNT:")
                    + event.getNumberOfRetryAttempts());
          });
      splitKeyDecryptionRetryEventPublisher.onError(
          // This event informs that a call has been retried, but still failed. That is, the
          // maximum number of attempts has been reached.
          event ->
              logger.error(String.format(SPLIT_KEY_AEAD_DECRYPT_LOG_TEMPLATE, "RETRY_FAILURE")));
      splitKeyDecryptionRetryEventPublisher.onIgnoredError(
          // This event informs that an error occurred and has been ignored - not retried.
          // An error is ignored when the exception is determined to be non-retriable, based on the
          // RetryConfig.
          event -> logger.error(String.format(SPLIT_KEY_AEAD_DECRYPT_LOG_TEMPLATE, "FAILURE")));
    }
  }

  /**
   * Create a new instance of {@link MultiPartyHybridEncryptionKeyServiceImpl} with an object {@link
   * MultiPartyHybridEncryptionKeyServiceParams}
   */
  public static MultiPartyHybridEncryptionKeyServiceImpl newInstance(
      MultiPartyHybridEncryptionKeyServiceParams params) {
    return new MultiPartyHybridEncryptionKeyServiceImpl(
        params.coordAKeyFetchingService(),
        params.coordBKeyFetchingService(),
        params.coordAAeadService(),
        params.coordBAeadService(),
        (params.enablePrivateKeyDecryptionRetries().isPresent()
            && params.enablePrivateKeyDecryptionRetries().get()),
        params.privateKeyDecryptionRetryConfig());
  }

  /** Returns the decrypter for the provided key. */
  @Override
  public HybridDecrypt getDecrypter(String keyId) throws KeyFetchException {
    try {
      return keysetHandleCache.get(keyId).getPrimitive(HybridDecrypt.class);
    } catch (ExecutionException | UncheckedExecutionException | GeneralSecurityException e) {
      ErrorReason reason = ErrorReason.UNKNOWN_ERROR;
      if (e.getCause() instanceof KeyFetchException) {
        reason = ((KeyFetchException) e.getCause()).getReason();
      }
      throw new KeyFetchException("Failed to get key with id: " + keyId, reason, e);
    }
  }

  /** Returns the encrypter for the provided key ID. */
  @Override
  public HybridEncrypt getEncrypter(String keyId) throws KeyFetchException {
    try {
      return keysetHandleCache.get(keyId).getPublicKeysetHandle().getPrimitive(HybridEncrypt.class);
    } catch (ExecutionException | UncheckedExecutionException | GeneralSecurityException e) {
      ErrorReason reason = ErrorReason.UNKNOWN_ERROR;
      if (e.getCause() instanceof KeyFetchException) {
        reason = ((KeyFetchException) e.getCause()).getReason();
      }
      throw new KeyFetchException("Failed to get key with id: " + keyId, reason, e);
    }
  }

  /** Key fetching service for coordinator A. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface CoordinatorAEncryptionKeyFetchingService {}

  /** Key fetching service for coordinator B. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface CoordinatorBEncryptionKeyFetchingService {}

  private KeysetHandle createDecrypter(String keyId) throws KeyFetchException {
    try {
      var primaryEncryptionKey = coordinatorAEncryptionKeyFetchingService.fetchEncryptionKey(keyId);

      switch (primaryEncryptionKey.getEncryptionKeyType()) {
        case SINGLE_PARTY_HYBRID_KEY:
          return createDecrypterSingleKey(primaryEncryptionKey);
        case MULTI_PARTY_HYBRID_EVEN_KEYSPLIT:
          var secondaryEncryptionKey =
              coordinatorBEncryptionKeyFetchingService.fetchEncryptionKey(keyId);
          if (splitKeyDecryptionRetry.isPresent()) {
            logger.info(String.format(SPLIT_KEY_AEAD_DECRYPT_LOG_TEMPLATE, "CALL"));
            return createDecrypterSplitKeyWithRetries(primaryEncryptionKey, secondaryEncryptionKey);
          }
          return createDecrypterSplitKey(primaryEncryptionKey, secondaryEncryptionKey);
        default:
          throw new KeyFetchException(
              "Unsupported encryption key type.", ErrorReason.UNKNOWN_ERROR);
      }

    } catch (EncryptionKeyFetchingServiceException e) {
      throw generateKeyFetchExceptionFromServiceException(e);
    } catch (GeneralSecurityException e) {
      throw generateKeyFetchExceptionFromGrpcException(e);
    } catch (IOException e) {
      throw new KeyFetchException("Failed to fetch key ID: " + keyId, ErrorReason.UNKNOWN_ERROR, e);
    }
  }

  private KeysetHandle createDecrypterSingleKey(EncryptionKey encryptionKey)
      throws GeneralSecurityException, IOException {
    var encryptionKeyData = getOwnerKeyData(encryptionKey);
    var aead = coordinatorAAeadService.getAead(encryptionKeyData.getKeyEncryptionKeyUri());
    var keysetHandle =
        KeysetHandle.read(JsonKeysetReader.withString(encryptionKeyData.getKeyMaterial()), aead);
    return keysetHandle;
  }

  private KeysetHandle createDecrypterSplitKey(
      EncryptionKey encryptionKeyA, EncryptionKey encryptionKeyB)
      throws GeneralSecurityException, IOException {
    // Split A.
    var encryptionKeyAData = getOwnerKeyData(encryptionKeyA);
    var aeadA = coordinatorAAeadService.getAead(encryptionKeyAData.getKeyEncryptionKeyUri());
    var splitA =
        aeadA.decrypt(Base64.getDecoder().decode(encryptionKeyAData.getKeyMaterial()), new byte[0]);

    // Split B.
    var encryptionKeyBData = getOwnerKeyData(encryptionKeyB);
    var aeadB = coordinatorBAeadService.getAead(encryptionKeyBData.getKeyEncryptionKeyUri());
    var splitB =
        aeadB.decrypt(Base64.getDecoder().decode(encryptionKeyBData.getKeyMaterial()), new byte[0]);

    // Reconstruct.
    var keySetHandle =
        KeySplitUtil.reconstructXorKeysetHandle(
            ImmutableList.of(ByteString.copyFrom(splitA), ByteString.copyFrom(splitB)));
    return keySetHandle;
  }

  /**
   * Runs the KMS decryption call with retries. It does not check for the existence of the Retry
   * object, so this method should only be called after validating the retry object exists.
   */
  private KeysetHandle createDecrypterSplitKeyWithRetries(
      EncryptionKey encryptionKeyA, EncryptionKey encryptionKeyB)
      throws GeneralSecurityException, IOException {
    try {
      return Retry.decorateCheckedSupplier(
              splitKeyDecryptionRetry.get(),
              () -> {
                return createDecrypterSplitKey(encryptionKeyA, encryptionKeyB);
              })
          .apply();
    } catch (GeneralSecurityException | IOException e) {
      throw e;
    } catch (Throwable t) {
      logger.error("Unexpected exception while creating split-key decrypter", t);
      throw new IOException(t);
    }
  }

  /** Find {@code KeyData} object owned by the coordinator. */
  private KeyData getOwnerKeyData(EncryptionKey encryptionKey) {
    // Should only be one key data with key material per coordinator.
    return encryptionKey.getKeyDataList().stream()
        .filter(keyData -> !keyData.getKeyMaterial().isEmpty())
        .findFirst()
        .get();
  }

  private static KeyFetchException generateKeyFetchExceptionFromServiceException(
      EncryptionKeyFetchingServiceException e) {
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

  private static KeyFetchException generateKeyFetchExceptionFromGrpcException(Throwable e) {
    if (e == null) {
      return new KeyFetchException(e, ErrorReason.KEY_DECRYPTION_ERROR);
    }
    logger.error("Exception for key decryption: ", e);
    logger.info("End of exception log.");
    if (e instanceof GoogleJsonResponseException) {
      var jsonException = (GoogleJsonResponseException) e;
      if (RETRYABLE_HTTP_STATUS_CODES.contains(jsonException.getStatusCode())) {
        return new KeyFetchException(e, ErrorReason.KEY_SERVICE_UNAVAILABLE);
      }
    }
    if (e instanceof ConnectException) {
      return new KeyFetchException(e, ErrorReason.KEY_SERVICE_UNAVAILABLE);
    }
    if (e instanceof ApiException) {
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
          return new KeyFetchException(e, ErrorReason.KEY_DECRYPTION_ERROR);
      }
    }
    if (e.getCause() == null) {
      return new KeyFetchException(e, ErrorReason.KEY_DECRYPTION_ERROR);
    }
    return generateKeyFetchExceptionFromGrpcException(e.getCause());
  }
}
