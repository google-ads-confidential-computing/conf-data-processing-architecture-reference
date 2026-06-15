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
import static com.google.scp.shared.util.KeyParams.DEFAULT_TINK_TEMPLATE;
import static com.google.scp.shared.util.KeySplitUtil.reconstructXorKeysetHandle;
import static java.lang.Integer.MAX_VALUE;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
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
  protected static final String SET_NAME = "test-set-name";

  @Inject protected CreateSplitKeyTaskBase task;
  @Inject protected InMemoryKeyDb keyDb;

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
    ImmutableList<EncryptionKey> keys = task.keyDb.listAllKeysForSetName("test-set").reverse();
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
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).isEmpty();

    // When
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 5, 7, 365, 14, 0, 0);

    // Then
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(5);
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(10);
  }

  @Test
  public void create_noKeys_validateActiveAndPendingKeysTest() throws Exception {
    var count = 5;
    var validity = 7;
    var validityMillis = Duration.ofDays(validity).toMillis();
    var ttl = 365;

    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).isEmpty();
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 14, 0, 0);

    validateEncryptionKeyTimes(validity, ttl);
    var keys = task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE);
    assertThat(keys).hasSize(count);
    validateTimesMatch(keys);

    var allKeys = task.keyDb.listAllKeysForSetName(SET_NAME);
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
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 1, 7, 365, 14, 0, 0);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(1);
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(2);

    // When
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 5, 7, 365, 14, 0, 0);

    // Then
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(5);
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(10);
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
    task.create(setName2, template, 4, 7, 365, 14, 0, 0);

    // Then
    assertThat(task.keyDb.listAllKeysForSetName(setName1)).hasSize(10);
    assertThat(task.keyDb.listAllKeysForSetName(setName2)).hasSize(8);
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
                    SET_NAME, DEFAULT_TINK_TEMPLATE, keysToCreate, 10, 20, 0, now()));

    assertThat(ex).hasCauseThat().isInstanceOf(KeyStorageServiceException.class);
    ImmutableList<EncryptionKey> keys = keyDb.listAllKeysForSetName(SET_NAME);
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

    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).isEmpty();

    // Creat Batch 1 - key1 and key2 are active
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0);
    // Create Batch 2 - key1, key2 and key3 are active
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0);
    // Created Batch 3 - key1, key2 and key3 are active, key4 is inactive
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0);
    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(3);
    assertThat(keyDb.listAllKeysForSetName(SET_NAME)).hasSize(4);

    // Assert another run does not create additional keys
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0);
    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(3);
    assertThat(keyDb.listAllKeysForSetName(SET_NAME)).hasSize(4);
  }

  @Test
  public void validateOverlap_activeKeysToStableAtNow() throws Exception {
    int validity = 30;
    int overlap = 20;

    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).isEmpty();

    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0);
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 1, validity, 365, 365, overlap, 0);

    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(3);
    assertThat(keyDb.listAllKeysForSetName(SET_NAME)).hasSize(3);
  }

  @Test
  public void validateInitialOverlapTest() throws Exception {
    int count = 3;
    int validity = 30;
    int overlap = 20;
    int ttl = 365;
    var differenceMillis = Duration.ofDays(validity - overlap).toMillis();
    Instant now = Instant.now();

    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0, now);

    validateEncryptionKeyTimes(validity, ttl);
    // Query active keys at an instant when only the first set of keys (activated at now - 20) is
    // active,
    // before the second set of keys (activated at now - 10) becomes active.
    var keys = task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.minus(15, DAYS));
    assertThat(keys).hasSize(count);

    var allKeys = task.keyDb.listAllKeysForSetName(SET_NAME);
    assertThat(allKeys).hasSize(count * 2);

    var currActivation = keys.get(0).getActivationTime();
    var nextActivation = allKeys.get(0).getActivationTime();
    assertThat(nextActivation - currActivation)
        .isIn(Range.closed(differenceMillis - 2000, differenceMillis + 2000));
  }

  @Test
  public void create_newOverlapKeyset_shiftsActivationTimeToPast() throws Exception {
    // Given
    int count = 3;
    int validity = 30;
    int overlap = 20;
    int ttl = 365;
    Instant now = Instant.now();

    // When
    // Setting overlap = 20 > 0 and since it's a new keyset (activeKeys is empty),
    // the activation time of the generated keys should be shifted back by 'overlap' days.
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0, now);

    // Then
    ImmutableList<EncryptionKey> keys =
        task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.minus(15, DAYS));
    assertThat(keys).hasSize(count);

    long expectedActivationTime = now.minus(overlap, DAYS).toEpochMilli();
    for (EncryptionKey key : keys) {
      assertThat(key.getActivationTime()).isEqualTo(expectedActivationTime);
    }
  }

  @Test
  public void create_existingOverlapKeyset_doesNotShiftActivationTimeToPast() throws Exception {
    // Given
    int count = 3;
    int validity = 30;
    int overlap = 20;
    int ttl = 365;
    Instant now = Instant.now();

    // Establish a pre-existing active key in the database so isNewKeyset is false
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 1, validity, ttl, 365, 0, 0, now);
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).isNotEmpty();

    // When
    // With existing keys, isNewKeyset is false; 2 new keys should activate at 'now', not shifted.
    // Database total: 6 keys (3 active at Day 0, 3 active at Day 10, 1 active at Day 30)
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0, now);
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(6);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(3);

    // Day 10 - Database total: 8 keys (3 active at Day 0, 2 active at Day 10, 2 active at Day 20, 1
    // active at Day 30).
    task.create(
        SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0, now.plus(10, DAYS));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(8);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.plus(10, DAYS))).hasSize(5);

    // Day 20 - Database total: 10 keys (3 active at Day 0, 2 active at Day 10, 2 active at Day 20,
    // 2 active at Day 30).
    task.create(
        SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0, now.plus(20, DAYS));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(10);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.plus(20, DAYS))).hasSize(7);

    // Day 30 - Database total: 13 keys (2 active at Day 10, 2 active at Day 20, 3 active at Day 30,
    // 3 active at Day 40).
    task.create(
        SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0, now.plus(30, DAYS));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(13);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.plus(30, DAYS))).hasSize(7);

    // Day 40 - Database total: 16 keys (2 active at Day 20, 3 active at Day 30, 3 active at Day 40,
    // 3 active at Day 50).
    task.create(
        SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0, now.plus(40, DAYS));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(16);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.plus(40, DAYS))).hasSize(8);

    // Day 50 - Database total: 19 keys (3 active at Day 30, 3 active at Day 40, 3 active at Day 50,
    // 3 active at Day 60).
    task.create(
        SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0, now.plus(50, DAYS));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(19);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.plus(50, DAYS))).hasSize(9);
  }

  @Test
  public void create_newKeysetNoOverlap_doesNotShiftActivationTimeToPast() throws Exception {
    // Given
    int count = 3;
    int validity = 30;
    int overlap = 0;
    int ttl = 365;
    Instant now = Instant.now();

    // When
    // overlap = 0, so no shifting should happen even though it is a new keyset.
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0, now);

    // Then
    ImmutableList<EncryptionKey> keys = task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now);
    assertThat(keys).hasSize(count);

    long expectedActivationTime = now.toEpochMilli();
    for (EncryptionKey key : keys) {
      assertThat(key.getActivationTime()).isEqualTo(expectedActivationTime);
    }
  }

  @Test
  public void validateMultipleOverlap_noExtraKeysCreatedTest() throws Exception {
    int count = 3;
    int validity = 30;
    int overlap = 20;
    int ttl = 365;

    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0);
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0);
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0);
    // These runs should not create any additional keys as the keys from the first three runs(key1,
    // key2, key3) are already active and key4 is inactive.
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0);
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 365, overlap, 0);

    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(count * 3);
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count * 4);
  }

  @Test
  public void validateOverlapDifferenceOverTimeTest() throws Exception {
    var count = 1;
    var validity = 30;
    var overlap = 20;
    var ttl = 365;
    var template = DEFAULT_TINK_TEMPLATE;
    var differenceMillis = Duration.ofDays(validity - overlap).toMillis();
    var now = now();

    task.create(SET_NAME, template, count, validity, ttl, 365, overlap, 0);
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count * 2);

    task.create(SET_NAME, template, count, validity, ttl, 365, overlap, 0, now.plus(11, DAYS));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count * 3);

    task.create(SET_NAME, template, count, validity, ttl, 365, overlap, 0, now.plus(21, DAYS));
    var allKeys = task.keyDb.listAllKeysForSetName(SET_NAME);
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
  public void validateOverlapDifferenceOverTime_highBatchTest() throws Exception {
    var count = 3; // Batch size of 3
    var validity = 30;
    var overlap = 20;
    var ttl = 365;
    var template = DEFAULT_TINK_TEMPLATE;
    var now = now();

    // Day 0: Create Batch 1 (3 active keys) + Batch 2 (3 active keys) = 6 active, 6 total
    task.create(SET_NAME, template, count, validity, ttl, 365, overlap, 0, now);
    // Day 0 + 5 minutes: Create Batch 3 (3 active keys) = 9 active, 9 total
    task.create(SET_NAME, template, count, validity, ttl, 365, overlap, 0, now.plus(5, MINUTES));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count * 3);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(9);

    // Day 10: Deactivate Batch 1 + Create Batch 4 = 9 active, 12 total
    task.create(SET_NAME, template, count, validity, ttl, 365, overlap, 0, now.plus(10, DAYS));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count * 4);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.plus(10, DAYS))).hasSize(9);

    // Day 20: Deactivate Batch 2 + Create Batch 5 = 9 active, 15 total
    task.create(SET_NAME, template, count, validity, ttl, 365, overlap, 0, now.plus(20, DAYS));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count * 5);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.plus(20, DAYS))).hasSize(9);

    // Day 30: Deactivate Batch 3 + Create Batch 6 = 9 active, 18 total
    task.create(SET_NAME, template, count, validity, ttl, 365, overlap, 0, now.plus(30, DAYS));
    assertThat(task.keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count * 6);
    assertThat(task.keyDb.getActiveKeys(SET_NAME, MAX_VALUE, now.plus(30, DAYS))).hasSize(9);
  }

  @Test
  public void create_withBackfill_setsBackfillExpirationTime() throws Exception {
    int count = 1;
    int validity = 10;
    int ttl = 20;
    int backfill = 5;
    Instant now = now();

    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, 14, 0, backfill, now);

    ImmutableList<EncryptionKey> keys = task.keyDb.listAllKeysForSetName(SET_NAME);
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
    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).isEmpty();
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, keysToCreate, validity, ttl, 14, 0, 0);

    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(keysToCreate);
    // No next set
    assertThat(keyDb.listAllKeysForSetName(SET_NAME)).hasSize(keysToCreate);
  }

  @Test
  public void partialKeys_maxDaysAheadBlocks_noNextActiveSet_test() throws Exception {
    int count = 5;
    int validity = 14;
    int ttl = 365;
    int maxDaysAhead = 13;

    // Create partial set
    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).isEmpty();
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, 1, validity, ttl, maxDaysAhead, 0, 0);
    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(1);
    // No next set
    assertThat(keyDb.listAllKeysForSetName(SET_NAME)).hasSize(1);

    // Finish complete set
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, maxDaysAhead, 0, 0);
    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(count);
    // No next set
    assertThat(keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count);
  }

  @Test
  public void maxDaysAheadLessThanActivePeriodTest() throws Exception {
    int count = 5;
    int validity = 14;
    int ttl = 365;
    int maxDaysAhead = validity + 5;

    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).isEmpty();
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, maxDaysAhead, 0, 0);
    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).hasSize(count);
    assertThat(keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count * 2);
  }

  private void validateEncryptionKeyTimes(int validity, int ttl) throws Exception {
    var expectedValidityMillis = Duration.ofDays(validity).toMillis();
    var ttlMillis = Duration.ofDays(ttl).toMillis();

    var keys = task.keyDb.listAllKeysForSetName(SET_NAME);
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

    assertThat(keyDb.getActiveKeys(SET_NAME, MAX_VALUE)).isEmpty();
    task.create(SET_NAME, DEFAULT_TINK_TEMPLATE, count, validity, ttl, maxDaysAhead, 0, 0);
    var keys = keyDb.getActiveKeys(SET_NAME, MAX_VALUE);
    assertThat(keys).hasSize(count);
    // Must have null expiration time which is represented as 0
    assertThat(keys.get(0).getExpirationTime()).isEqualTo(0);
    // Must have null expiration time which is represented as 0
    assertThat(keys.get(0).getTtlTime()).isEqualTo(0);

    assertThat(keyDb.listAllKeysForSetName(SET_NAME)).hasSize(count);
  }

  @Test
  public void validate_overlapGreaterThanDaysInAdvance() throws Exception {
    int count = 1;
    int validity = 30;
    int overlap = 20; // Next Activation = Day 10
    int ttl = 365;
    int maxDaysAhead = 5; // overlap > maxDaysAhead
    Instant now = now();

    // Day 0: Next Activation is on Day 10. Cutoff is (now + 5) = Day 5.
    // 10 < 5 is FALSE. Should NOT generate next set of keys.
    task.create(
        SET_NAME + "_G",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now);
    task.create(
        SET_NAME + "_G",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(5, MINUTES));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_G")).hasSize(count * 3);

    // Day 5: Next Activation is on Day 10. Cutoff is (now + 5) = Day 10.
    // 10 < 10 is FALSE. Should NOT generate next set of keys yet (strict <).
    task.create(
        SET_NAME + "_G",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(5, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_G")).hasSize(count * 3);

    // Day 6: Next Activation is on Day 10. Cutoff is (now + 5) = Day 11.
    // 10 < 11 is TRUE. Generates next set of keys.
    task.create(
        SET_NAME + "_G",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(6, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_G")).hasSize(count * 4);
  }

  @Test
  public void validate_overlapLessThanOrEqualToDaysInAdvance() throws Exception {
    int count = 1;
    int validity = 30;
    int overlap = 20; // Next Activation = Day 10
    int ttl = 365;
    int maxDaysAhead = 20; // overlap <= maxDaysAhead
    Instant now = now();

    // Key 1 (Active 0-30) is generated. Key 2 activates on Day 10.
    // Because overlap (20) + maxDaysAhead (20) >= validity (30),
    // the deadline to create Key 2 is effectively in the past on Day 0
    // (Expiration 30 - Overlap 20 - MaxDays 20 = -10 < 0).
    // Both keys are generated together in the first run.
    task.create(
        SET_NAME + "_L",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now);
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_L")).hasSize(count * 2);
  }

  @Test
  public void validate_overlapIsZero_daysInAdvanceIsNonZero() throws Exception {
    int count = 1;
    int validity = 30;
    int ttl = 365;
    int maxDaysAhead = 5;
    int overlap = 0;
    Instant now = now();

    // Day 0: Next Activation is Day 30. Cutoff is Day 5.
    // 30 < 5 is False. No generation.
    task.create(
        SET_NAME + "_Z_A",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now);
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_A")).hasSize(count);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_A", 10, now)).hasSize(1);

    // Day 25: Next Activation is Day 30. Cutoff is Day 30.
    // 30 < 30 is False. No generation yet (strict <).

    task.create(
        SET_NAME + "_Z_A",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(25, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_A")).hasSize(count);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_A", 10, now.plus(25, DAYS))).hasSize(1);

    task.create(
        SET_NAME + "_Z_A",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(26, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_A")).hasSize(count * 2);
    // Key 2 is generated but pending until Day 30, so only 1 is active.
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_A", 10, now.plus(26, DAYS))).hasSize(1);
  }

  @Test
  public void validate_overlapIsNonZero_daysInAdvanceIsZero() throws Exception {
    int count = 1;
    int validity = 30;
    int ttl = 365;
    int maxDaysAhead = 0;
    int overlap = 20;
    Instant now = now();

    // Day 0: Next Activation is Day 10. Cutoff is Day 0.
    // 10 < 0 is False. No generation.
    task.create(
        SET_NAME + "_Z_B",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now);
    task.create(
        SET_NAME + "_Z_B",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(5, MINUTES));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_B")).hasSize(count * 3);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_B", 10, now)).hasSize(3);

    // Day 10: Next Activation is Day 10. Cutoff is Day 10.
    // 10 < 10 is False. No generation yet (strict <).
    // Key 1 expires. Total stays 3. Active drops to 2 (Key 2, Key 3).

    task.create(
        SET_NAME + "_Z_B",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(10, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_B")).hasSize(count * 3);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_B", 10, now.plus(10, DAYS))).hasSize(2);

    task.create(
        SET_NAME + "_Z_B",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(11, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_B")).hasSize(count * 4);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_B", 10, now.plus(11, DAYS))).hasSize(3);

    // Day 20: Next Activation is Day 20. Cutoff is Day 20.
    // 20 < 20 is False. No generation yet (strict <).
    // Key 1, 2 expires. Total stays 4. Active drops to 2 (Key 3, Key 4).

    task.create(
        SET_NAME + "_Z_B",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(20, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_B")).hasSize(count * 4);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_B", 10, now.plus(20, DAYS))).hasSize(2);

    // Day 21: Next Activation is Day 20. Cutoff is Day 21.
    // 20 < 21 is True. Generates Key 5.
    task.create(
        SET_NAME + "_Z_B",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(21, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_B")).hasSize(count * 5);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_B", 10, now.plus(21, DAYS))).hasSize(3);

    // Day 30: Key 1, 2, 3 expires. Total stays 5. Active drops to 2 (Key 4, Key 5).
    // Cutoff is Day 30. Next Activation is 30. 30 < 30 is False.
    task.create(
        SET_NAME + "_Z_B",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(30, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_B")).hasSize(count * 5);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_B", 10, now.plus(30, DAYS))).hasSize(2);

    // Day 31: Next Activation is 30. 30 < 31 is True. Generates Key 4.
    // Total becomes 6. Active jumps back to 3 (Key 4, Key 5, Key 6).
    task.create(
        SET_NAME + "_Z_B",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(31, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_B")).hasSize(count * 6);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_B", 10, now.plus(31, DAYS))).hasSize(3);
  }

  @Test
  public void validate_overlapAndDaysInAdvanceAreZero() throws Exception {
    int count = 1;
    int validity = 30;
    int ttl = 365;
    int maxDaysAhead = 0;
    int overlap = 0;
    Instant now = now();

    // Day 0: Next Activation is Day 30. Cutoff is Day 0.
    // 30 < 0 is False. No generation.
    task.create(
        SET_NAME + "_Z_C",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now);
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_C")).hasSize(count);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_C", 10, now)).hasSize(1);

    // Day 30: Key 1 expired. getActiveKeys returns 0.
    // Generation kicks in to maintain desired count. New key (Key 2) is active.
    task.create(
        SET_NAME + "_Z_C",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(30, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_C")).hasSize(count * 2);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_C", 10, now.plus(30, DAYS))).hasSize(1);

    // Day 31: Already generated. No pending generation needed.
    // Size remains count * 2.
    task.create(
        SET_NAME + "_Z_C",
        DEFAULT_TINK_TEMPLATE,
        count,
        validity,
        ttl,
        maxDaysAhead,
        overlap,
        0,
        now.plus(31, DAYS));
    assertThat(keyDb.listAllKeysForSetName(SET_NAME + "_Z_C")).hasSize(count * 2);
    assertThat(keyDb.getActiveKeys(SET_NAME + "_Z_C", 10, now.plus(31, DAYS))).hasSize(1);
  }

  protected abstract KeyStorageClient getKeyStorageClient();

  protected abstract ImmutableList<byte[]> capturePeerSplits() throws Exception;
}
