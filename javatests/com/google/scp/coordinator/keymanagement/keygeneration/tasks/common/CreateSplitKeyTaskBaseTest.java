/*
 * Copyright 2023 Google LLC
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
package com.google.scp.coordinator.keymanagement.keygeneration.tasks.common;

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb.DEFAULT_SET_NAME;
import static com.google.scp.shared.util.KeyParams.DEFAULT_TINK_TEMPLATE;
import static com.google.scp.shared.util.KeySplitUtil.reconstructXorKeysetHandle;
import static java.lang.Integer.MAX_VALUE;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.Mac;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient;
import com.google.scp.coordinator.keymanagement.keygeneration.app.common.KeyStorageClient.KeyStorageServiceException;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.Annotations.KmsKeyAead;
import com.google.scp.coordinator.keymanagement.shared.dao.testing.InMemoryKeyDb;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map.Entry;
import org.junit.Test;

public abstract class CreateSplitKeyTaskBaseTest {

  @Inject protected CreateSplitKeyTaskBase task;
  @Inject protected InMemoryKeyDb keyDb;
  @Inject @KmsKeyAead protected Aead keyEncryptionKeyAead;

  protected void create_differentTinkTemplates_successfullyReconstructExpectedPrimitives(Aead aead)
      throws Exception {
    // Given
    ImmutableList<Entry<String, Class<?>>> expectedPrimitives =
        new ImmutableMap.Builder<String, Class<?>>()
            .put("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_CHACHA20_POLY1305_RAW", HybridDecrypt.class)
            .put("HMAC_SHA512_256BITTAG_RAW", Mac.class)
            .build()
            .entrySet()
            .asList();

    // When
    for (Entry<String, Class<?>> primitive : expectedPrimitives) {
      String template = primitive.getKey();
      task.createSplitKey("test-set", template, 1, 1, 1, 0, now());
    }

    // Then
    ImmutableList<EncryptionKey> keys = task.keyDb.getAllKeys().reverse();
    ImmutableList<byte[]> peerSplits = capturePeerSplits();
    for (int i = 0; i < expectedPrimitives.size(); i++) {
      String template = expectedPrimitives.get(i).getKey();
      Class<?> expectedPrimitive = expectedPrimitives.get(i).getValue();

      byte[] localSplit =
          aead.decrypt(Base64.getDecoder().decode(keys.get(i).getJsonEncodedKeyset()), new byte[0]);
      byte[] peerSplit = peerSplits.get(i);

      KeysetHandle reconstructed =
          reconstructXorKeysetHandle(
              ImmutableList.of(ByteString.copyFrom(localSplit), ByteString.copyFrom(peerSplit)));

      assertThat(reconstructed.getKeysetInfo().getKeyInfo(0).getTypeUrl())
          .isEqualTo(KeyTemplates.get(template).getTypeUrl());
      assertThat(reconstructed.getPrimitive(expectedPrimitive)).isInstanceOf(expectedPrimitive);
    }
  }

  @Test
  public void create_noKeys_createsActiveKeysAndPendingActiveForEach() throws Exception {
    // Given
    assertThat(task.keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).isEmpty();

    // When
    task.create(DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, 5, 7, 365, 14, 0, 0);

    // Then
    assertThat(task.keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).hasSize(5);
    assertThat(task.keyDb.getAllKeys()).hasSize(10);
  }

  @Test
  public void create_noKeys_validateActiveAndPendingKeysTest() throws Exception {
    var count = 5;
    var validity = 7;
    var validityMillis = Duration.ofDays(validity).toMillis();
    var ttl = 365;

    assertThat(task.keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).isEmpty();
    task.create(DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 14, 0, 0);

    validateEncryptionKeyTimes(validity, ttl);
    var keys = task.keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE);
    assertThat(keys).hasSize(count);
    validateTimesMatch(keys);

    var allKeys = task.keyDb.getAllKeys();
    assertThat(allKeys).hasSize(count * 2);
    validateTimesMatch(allKeys.subList(0, count));
    validateTimesMatch(allKeys.subList(count, count * 2));

    var currActivation = keys.get(0).getActivationTime();
    var nextActivation = allKeys.get(0).getActivationTime();
    assertThat(nextActivation - currActivation)
        .isIn(Range.closed(validityMillis - 2000, validityMillis + 2000));
  }

  private static void validateTimesMatch(ImmutableList<EncryptionKey> keys) {
    var key = keys.get(0);
    for (int i = 1; i < keys.size(); i++) {
      assertThat(keys.get(i).getActivationTime()).isEqualTo(key.getActivationTime());
      assertThat(keys.get(i).getExpirationTime()).isEqualTo(key.getExpirationTime());
      assertThat(keys.get(i).getTtlTime()).isEqualTo(key.getTtlTime());
    }
  }

  @Test
  public void create_onlyOneButNotEnough_createsOnlyMissingKey() throws Exception {
    // Given
    task.create(DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, 1, 7, 365, 14, 0, 0);
    assertThat(task.keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).hasSize(1);
    assertThat(task.keyDb.getAllKeys()).hasSize(2);

    // When
    task.create(DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, 5, 7, 365, 14, 0, 0);

    // Then
    assertThat(task.keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).hasSize(5);
    assertThat(task.keyDb.getAllKeys()).hasSize(10);
  }

  @Test
  public void create_differentSetNames_createsDifferentSets() throws Exception {
    // Given
    String setName1 = "set-name-1";
    String setName2 = "set-name-2";
    var template = "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_CHACHA20_POLY1305_RAW";

    assertThat(task.keyDb.getActiveKeys(setName1, MAX_VALUE)).isEmpty();
    assertThat(task.keyDb.getActiveKeys(setName2, MAX_VALUE)).isEmpty();

    // When
    task.create(setName1, template, 5, 7, 365, 14, 0, 0);
    task.create(setName2, template, 5, 7, 365, 14, 0, 0);

    // Then
    assertThat(
            task.keyDb.getAllKeys().stream()
                .map(EncryptionKey::getSetName)
                .filter(setName1::equals))
        .hasSize(10);
    assertThat(
            task.keyDb.getAllKeys().stream()
                .map(EncryptionKey::getSetName)
                .filter(setName2::equals))
        .hasSize(10);
  }

  /** Ensure that even if we fail after two attempted generations, we store two keys */
  @Test
  public void createSplitKey_keyGenerationInterrupted() throws Exception {
    int keysToCreate = 5;

    when(getKeyStorageClient().createKey(any(), any(), any()))
        .thenCallRealMethod()
        .thenCallRealMethod()
        .thenThrow(new KeyStorageServiceException("Failure", new GeneralSecurityException()));

    ServiceException ex =
        assertThrows(
            ServiceException.class,
            () ->
                task.createSplitKey(
                    DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, keysToCreate, 10, 20, 0, now()));

    assertThat(ex).hasCauseThat().isInstanceOf(KeyStorageServiceException.class);
    ImmutableList<EncryptionKey> keys = keyDb.getAllKeys();
    assertThat(keys).hasSize(3);
  }

  @Test
  public void overlapLessThanZero_throwsExceptionTest() {
    int validity = 30;
    int overlap = -1;
    var ex =
        assertThrows(
            ServiceException.class,
            () -> task.create("setName", DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0));
    assertThat(ex.getMessage()).contains("must be greater than or equal to 0");
  }

  @Test
  public void backfillLessThanZero_throwsExceptionTest() {
    int validity = 30;
    int overlap = 0;
    int backfill = -1;
    var ex =
        assertThrows(
            ServiceException.class,
            () ->
                task.create(
                    "setName", DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, backfill));
    assertThat(ex.getMessage()).contains("must be greater than or equal to 0");
  }

  @Test
  public void validityNotGreaterThanOverlap_throwsExceptionTest() {
    int validity = 30;
    int overlap = 30;
    var ex =
        assertThrows(
            ServiceException.class,
            () -> task.create("setName", DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0));
    assertThat(ex.getMessage()).contains("must be greater than overlap");
  }

  @Test
  public void validityNotMultipleOfDifference_throwsExceptionTest() {
    int validity = 30;
    int overlap = 21;
    var ex =
        assertThrows(
            ServiceException.class,
            () -> task.create("setName", DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0));
    assertThat(ex.getMessage()).contains("multiple of the difference");
  }

  @Test
  public void validateOverlap_doesNotCreateAdditionalKeysTest() throws Exception {
    int validity = 30;
    int overlap = 20;
    String setName = "setName";

    assertThat(keyDb.getActiveKeys(setName, MAX_VALUE)).isEmpty();

    task.create(setName, DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0);
    assertThat(keyDb.getActiveKeys(setName, MAX_VALUE)).hasSize(1);
    assertThat(keyDb.getAllKeys()).hasSize(2);

    // Assert another run does not create additional keys
    task.create(setName, DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0);
    assertThat(keyDb.getActiveKeys(setName, MAX_VALUE)).hasSize(1);
    assertThat(keyDb.getAllKeys()).hasSize(2);
  }

  @Test
  public void validateInitialOverlapTest() throws Exception {
    int count = 3;
    int validity = 30;
    int overlap = 20;
    int ttl = 365;
    String setName = "setName";
    var differenceMillis = Duration.ofDays(validity - overlap).toMillis();

    task.create(setName, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0);

    validateEncryptionKeyTimes(validity, ttl);
    var keys = task.keyDb.getActiveKeys(setName, MAX_VALUE);
    assertThat(keys).hasSize(count);

    var allKeys = task.keyDb.getAllKeys();
    assertThat(allKeys).hasSize(count * 2);

    var currActivation = keys.get(0).getActivationTime();
    var nextActivation = allKeys.get(0).getActivationTime();
    assertThat(nextActivation - currActivation)
        .isIn(Range.closed(differenceMillis - 2000, differenceMillis + 2000));
  }

  @Test
  public void validateMultipleOverlap_noExtraKeysCreatedTest() throws Exception {
    int count = 3;
    int validity = 30;
    int overlap = 20;
    int ttl = 365;
    String setName = "setName";

    task.create(setName, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0);
    task.create(setName, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0);
    task.create(setName, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0);

    assertThat(task.keyDb.getActiveKeys(setName, MAX_VALUE)).hasSize(count);
    assertThat(task.keyDb.getAllKeys()).hasSize(count * 2);
  }

  @Test
  public void validateOverlapDifferenceOverTimeTest() throws Exception {
    var count = 1;
    var validity = 30;
    var overlap = 20;
    var ttl = 365;
    var setName = "setName";
    var template = DEFAULT_TINK_TEMPLATE;
    var differenceMillis = Duration.ofDays(validity - overlap).toMillis();
    var now = now();

    task.create(setName, template, count, validity, ttl, 365, overlap, 0);
    assertThat(task.keyDb.getAllKeys()).hasSize(count * 2);

    task.create(setName, template, count, validity, ttl, 365, overlap, 0, now.plus(11, DAYS));
    assertThat(task.keyDb.getAllKeys()).hasSize(count * 3);

    task.create(setName, template, count, validity, ttl, 365, overlap, 0, now.plus(21, DAYS));
    var allKeys = task.keyDb.getAllKeys();
    assertThat(allKeys).hasSize(count * 4);

    // Validate activation times are difference ahead between keys
    for (int i = 2; i >= 0; i--) {
      var prevActivation = allKeys.get(i + 1).getActivationTime();
      var activation = allKeys.get(i).getActivationTime();
      assertThat(activation - prevActivation)
          .isIn(Range.closed(differenceMillis - 2000, differenceMillis + 2000));
    }
  }

  @Test
  public void create_withBackfill_setsBackfillExpirationTime() throws Exception {
    int count = 1;
    int validity = 10;
    int ttl = 20;
    int backfill = 5;
    Instant now = now();

    task.create(
        DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 14, 0, backfill, now);

    ImmutableList<EncryptionKey> keys = task.keyDb.getAllKeys();
    // 1 active key + 1 pending key = 2 keys.
    assertThat(keys).hasSize(2);

    EncryptionKey activeKey =
        keys.stream()
            .filter(k -> k.getActivationTime() == now.toEpochMilli())
            .findFirst()
            .orElseThrow();

    assertThat(activeKey.hasKeyMetadata()).isTrue();
    assertThat(activeKey.getKeyMetadata().getBackfillExpirationTime())
        .isEqualTo(now.plus(validity + backfill, DAYS).toEpochMilli());

    EncryptionKey pendingKey =
        keys.stream()
            .filter(k -> k.getActivationTime() > now.toEpochMilli())
            .findFirst()
            .orElseThrow();

    Instant pendingActivation = Instant.ofEpochMilli(pendingKey.getActivationTime());
    assertThat(pendingKey.hasKeyMetadata()).isTrue();
    assertThat(pendingKey.getKeyMetadata().getBackfillExpirationTime())
        .isEqualTo(pendingActivation.plus(validity + backfill, DAYS).toEpochMilli());
  }

  @Test
  public void noKeys_createsActiveKeys_noNextActiveSet_test() throws Exception {
    validateMaxDaysAheadBlocksNextActiveSetCreation(5, 14, 365);
  }

  public void validateMaxDaysAheadBlocksNextActiveSetCreation(
      int keysToCreate, int validity, int ttl) throws Exception {
    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).isEmpty();
    task.create(DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, keysToCreate, validity, ttl, 14, 0, 0);

    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).hasSize(keysToCreate);
    // No next set
    assertThat(keyDb.getAllKeys()).hasSize(keysToCreate);
  }

  @Test
  public void partialKeys_maxDaysAheadBlocks_noNextActiveSet_test() throws Exception {
    int count = 5;
    int validity = 14;
    int ttl = 365;
    int maxDaysAhead = 13;

    // Create partial set
    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).isEmpty();
    task.create(DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, 1, validity, ttl, maxDaysAhead, 0, 0);
    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).hasSize(1);
    // No next set
    assertThat(keyDb.getAllKeys()).hasSize(1);

    // Finish complete set
    task.create(DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, maxDaysAhead, 0, 0);
    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).hasSize(count);
    // No next set
    assertThat(keyDb.getAllKeys()).hasSize(count);
  }

  @Test
  public void maxDaysAheadLessThanActivePeriodTest() throws Exception {
    int count = 5;
    int validity = 14;
    int ttl = 365;
    int maxDaysAhead = validity + 5;

    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).isEmpty();
    task.create(DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, maxDaysAhead, 0, 0);
    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).hasSize(count);
    assertThat(keyDb.getAllKeys()).hasSize(count * 2);
  }

  private void validateEncryptionKeyTimes(int validity, int ttl) throws Exception {
    var expectedValidityMillis = Duration.ofDays(validity).toMillis();
    var ttlMillis = Duration.ofDays(ttl).toMillis();

    var keys = task.keyDb.getAllKeys();
    for (var key : keys) {
      var currActivation = key.getActivationTime();
      var currExpiration = key.getExpirationTime();
      assertThat(currExpiration - currActivation)
          .isIn(Range.closed(expectedValidityMillis - 2000, expectedValidityMillis + 2000));

      var currTtl = key.getTtlTime() * 1000; // ttl is in seconds
      assertThat(currTtl - currActivation).isIn(Range.closed(ttlMillis - 2000, ttlMillis + 2000));
    }
  }

  @Test
  public void createNeverRotateKeyTest() throws Exception {
    int count = 1;
    int validity = 0;
    int ttl = 0;
    int maxDaysAhead = 365;

    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE)).isEmpty();
    task.create(DEFAULT_SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, maxDaysAhead, 0, 0);
    var keys = keyDb.getActiveKeys(DEFAULT_SET_NAME, MAX_VALUE);
    assertThat(keys).hasSize(count);
    // Must have null expiration time which is represented as 0
    assertThat(keys.get(0).getExpirationTime()).isEqualTo(0);
    // Must have null expiration time which is represented as 0
    assertThat(keys.get(0).getTtlTime()).isEqualTo(0);

    assertThat(keyDb.getAllKeys()).hasSize(count);
  }

  protected abstract KeyStorageClient getKeyStorageClient();

  protected abstract ImmutableList<byte[]> capturePeerSplits() throws Exception;
}
