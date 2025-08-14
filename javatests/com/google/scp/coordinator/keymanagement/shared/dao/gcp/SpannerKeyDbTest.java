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

package com.google.scp.coordinator.keymanagement.shared.dao.gcp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb.DEFAULT_SET_NAME;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.UNSUPPORTED_OPERATION;
import static com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey.withAllTimesSet;
import static com.google.scp.coordinator.keymanagement.testutils.gcp.SpannerKeyDbTestUtil.SPANNER_KEY_TABLE_NAME;
import static com.google.scp.coordinator.keymanagement.testutils.gcp.SpannerKeyDbTestUtil.putItem;
import static com.google.scp.coordinator.keymanagement.testutils.gcp.SpannerKeyDbTestUtil.putKeyWithActivationAndExpirationTimes;
import static com.google.scp.coordinator.keymanagement.testutils.gcp.SpannerKeyDbTestUtil.putKeyWithExpiration;
import static com.google.scp.coordinator.keymanagement.testutils.gcp.SpannerKeyDbTestUtil.putNItemsRandomValues;
import static com.google.scp.shared.api.model.Code.ALREADY_EXISTS;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertThrows;

import com.google.acai.Acai;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.shared.dao.common.Annotations.KeyDbClient;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDbBaseTest;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDbUtil;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyStatusProto.EncryptionKeyStatus;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SpannerKeyDbTest extends KeyDbBaseTest {

  private static final int KEY_ITEM_COUNT = 5;

  @Rule public final Acai acai = new Acai(SpannerKeyDbTestModule.class);
  @Inject @KeyDbClient private DatabaseClient dbClient;
  @Inject private SpannerKeyDb keyDb;

  @After
  public void deleteTable() {
    dbClient.write(ImmutableList.of(Mutation.delete(SPANNER_KEY_TABLE_NAME, KeySet.all())));
  }

  @Test
  public void createKey_successAdd() throws ServiceException {
    String keyId = "test-key-id";
    EncryptionKey expectedKey =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setPublicKey("12345")
            .setPublicKeyMaterial("12345")
            .setJsonEncodedKeyset("67890")
            .setKeyEncryptionKeyUri("URI")
            .setCreationTime(0L)
            .setExpirationTime(0L)
            .build();

    keyDb.createKey(expectedKey);
    EncryptionKey receivedKey = keyDb.getKey(keyId);

    assertThat(receivedKey.getKeyId()).isEqualTo(keyId);
    assertThat(receivedKey.getPublicKey()).isEqualTo(expectedKey.getPublicKey());
    assertThat(receivedKey.getJsonEncodedKeyset()).isEqualTo(expectedKey.getJsonEncodedKeyset());
    assertThat(receivedKey.getMigrationKeyEncryptionKeyUri()).isEqualTo("");
    assertThat(receivedKey.getMigrationJsonEncodedKeyset()).isEqualTo("");
    assertThat(receivedKey.getMigrationKeySplitDataList()).hasSize(0);
  }

  @Test
  public void createKey_alreadyExists() throws ServiceException {
    String keyId = "test-key-id";
    EncryptionKey key1 =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setPublicKey("012")
            .setPublicKeyMaterial("012")
            .setJsonEncodedKeyset("345")
            .setKeyEncryptionKeyUri("URI")
            .setCreationTime(0L)
            .setExpirationTime(0L)
            .build();
    EncryptionKey key2 =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setPublicKey("678")
            .setPublicKeyMaterial("678")
            .setJsonEncodedKeyset("9ab")
            .setKeyEncryptionKeyUri("URI")
            .setCreationTime(0L)
            .setExpirationTime(0L)
            .build();

    keyDb.createKey(key1);
    try {
      keyDb.createKey(key2, false);
    } catch (ServiceException e) {
      assertThat(e.getErrorCode()).isEqualTo(ALREADY_EXISTS);
    }
  }

  @Test
  public void createKeys_success() throws ServiceException {
    String keyId = "1";
    EncryptionKey key =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setPublicKey("publicKey1")
            .setPublicKeyMaterial("material1")
            .setJsonEncodedKeyset("PrivateKey1")
            .setKeyEncryptionKeyUri("URI")
            .setExpirationTime(Instant.now().plus(Duration.ofSeconds(2000)).toEpochMilli())
            .setTtlTime(Instant.now().plus(Duration.ofDays(365)).getEpochSecond())
            .setActivationTime(Instant.now().plus(Duration.ofSeconds(1000)).toEpochMilli())
            .build();

    keyDb.createKeys(ImmutableList.of(key));
    EncryptionKey dbKey = keyDb.getKey(keyId);

    assertThat(dbKey.getKeyId()).isEqualTo(keyId);
    assertThat(dbKey.getPublicKey()).isEqualTo(key.getPublicKey());
    assertThat(dbKey.getJsonEncodedKeyset()).isEqualTo(key.getJsonEncodedKeyset());
  }

  @Test
  public void getKey_success() throws ServiceException {
    EncryptionKey expectedKey =
        FakeEncryptionKey.createBuilderWithDefaults(false)
            // SpannerKeyDb returns times based off seconds
            .setExpirationTime(Instant.now().plus(7, DAYS).toEpochMilli())
            .setActivationTime(Instant.now().toEpochMilli())
            .build();
    putItem(keyDb, expectedKey);

    EncryptionKey receivedKey = keyDb.getKey(expectedKey.getKeyId());

    // Manually set creationTime equal since it is automatically set by spanner.
    assertThat(receivedKey)
        .isEqualTo(expectedKey.toBuilder().setCreationTime(receivedKey.getCreationTime()).build());
  }

  @Test
  public void getKey_returnsNotFound() {
    ServiceException exception =
        assertThrows(ServiceException.class, () -> keyDb.getKey("notpresent"));

    assertThat(exception.getErrorCode()).isEqualTo(Code.NOT_FOUND);
  }

  @Test
  public void getActiveKeys_atLimitCount() throws ServiceException {
    putNItemsRandomValues(keyDb, 10);

    awaitAndAssertActiveKeyCount(keyDb, KEY_ITEM_COUNT);
  }

  @Test
  public void getActiveKeys_whenGreaterThanLimitCount() throws ServiceException {
    putNItemsRandomValues(keyDb, 13);

    awaitAndAssertActiveKeyCount(keyDb, KEY_ITEM_COUNT);
  }

  @Test
  public void getActiveKeys_whenLessThanLimitCount() throws ServiceException {
    putNItemsRandomValues(keyDb, 3);

    awaitAndAssertActiveKeyCount(keyDb, 3);
  }

  @Test
  public void getActiveKeys_whenEmpty() {
    awaitAndAssertActiveKeyCount(keyDb, 0);
  }

  @Test
  public void getActiveKeys_expiredKeysAreFiltered() throws ServiceException {
    putKeyWithExpiration(keyDb, Instant.now().minusSeconds(2000));

    awaitAndAssertActiveKeyCount(keyDb, 0);
    // Returns all even expired
  }

  @Test
  public void getActiveKeys_inactiveKeysAreFiltered() throws ServiceException {
    putKeyWithActivationAndExpirationTimes(
        keyDb, Instant.now().plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
    awaitAndAssertActiveKeyCount(keyDb, 0);

    putKeyWithActivationAndExpirationTimes(
        keyDb, Instant.now(), Instant.now().plus(Duration.ofHours(1)));
    awaitAndAssertActiveKeyCount(keyDb, 1);
  }

  @Test
  public void getActiveKeys_keysAreSorted() throws ServiceException {
    // Insert items out of expiration time order.
    for (var i : ImmutableList.of(2, 4, 3, 6, 1)) {
      putKeyWithExpiration(keyDb, Instant.now().plus(Duration.ofHours(i)));
    }

    awaitAndAssertActiveKeyCount(keyDb, 5);

    // SpannerKeyDb doesn't use getActiveKeysComparator but other implementations do, ensure the
    // sort order of SpannerKeyDb matches other implementations.
    assertThat(keyDb.getActiveKeys(DEFAULT_SET_NAME, 5))
        .isInOrder(KeyDbUtil.getActiveKeysComparator());

    assertThat(keyDb.listAllKeysForSetName(DEFAULT_SET_NAME))
        .isInOrder(KeyDbUtil.getActiveKeysComparator());
  }

  @Test
  public void getAllKeys_throwsServiceError() {
    ServiceException exception = assertThrows(ServiceException.class, () -> keyDb.getAllKeys());

    assertThat(exception.getErrorCode()).isEqualTo(Code.NOT_FOUND);
    assertThat(exception.getErrorReason()).isEqualTo(UNSUPPORTED_OPERATION.name());
  }

  @Test
  public void getAllKeys_returnKeysWithNullExpirationAndTtl() throws ServiceException {
    // Insert items
    EncryptionKey key =
        EncryptionKey.newBuilder()
            .setKeyId("test")
            .setSetName(DEFAULT_SET_NAME)
            .setStatus(EncryptionKeyStatus.ACTIVE)
            .setCreationTime(Instant.now().toEpochMilli())
            .setActivationTime(Instant.now().toEpochMilli())
            .setKeyType("testtype")
            .build();

    keyDb.createKey(key);
    EncryptionKey receivedKey = keyDb.getKey("test");
    assertThat(receivedKey.getKeyType()).isEqualTo("testtype");
    assertThat(receivedKey.hasExpirationTime()).isFalse();
    assertThat(receivedKey.hasTtlTime()).isFalse();
    assertThat(receivedKey.getExpirationTime()).isEqualTo(0);
    assertThat(receivedKey.getTtlTime()).isEqualTo(0);
    awaitAndAssertActiveKeyCount(keyDb, 1);
  }

  @Test
  public void getActiveKeys_withZeroKeyLimit() throws ServiceException {
    putNItemsRandomValues(keyDb, 10);

    ImmutableList<EncryptionKey> keys =
        keyDb.getActiveKeys(DEFAULT_SET_NAME, 0, Instant.now());
    assertThat(keys.size()).isEqualTo(10);
  }

  @Test
  public void listAllKeysForSetName_returnsAll_test() throws ServiceException {
    var now = Instant.now();

    putItem(keyDb, withAllTimesSet(now));
    putItem(keyDb, withAllTimesSet(now.plus(10, DAYS)));
    putItem(keyDb, withAllTimesSet(now.plus(100, DAYS)));
    putItem(keyDb, withAllTimesSet(now.minus(10, DAYS)));
    putItem(keyDb, withAllTimesSet(now.minus(500, DAYS)));

    awaitAndAssertAllKeyCount(keyDb, 5);
    assertThat(keyDb.listAllKeysForSetName(DEFAULT_SET_NAME))
        .isInOrder(KeyDbUtil.getActiveKeysComparator());
  }

  @Test
  public void listAllKeysForSetName_returnsAll_withNonDefaultSetName() throws ServiceException {
    var now = Instant.now();
    String setName = "test-set-name";

    putItem(keyDb, withAllTimesSet(now).toBuilder().setSetName(setName).build());
    putItem(keyDb, withAllTimesSet(now.plus(10, DAYS)).toBuilder().setSetName(setName).build());
    putItem(keyDb, withAllTimesSet(now.plus(100, DAYS)).toBuilder().setSetName(setName).build());
    putItem(keyDb, withAllTimesSet(now.minus(10, DAYS)).toBuilder().setSetName(setName).build());
    putItem(keyDb, withAllTimesSet(now.minus(500, DAYS)).toBuilder().setSetName(setName).build());

    awaitAndAssertAllKeyCount(keyDb, setName, 5);
    assertThat(keyDb.listAllKeysForSetName(setName))
        .isInOrder(KeyDbUtil.getActiveKeysComparator());
  }

  @Test
  public void listAllKeysForSetName_returnsOnlyKeysForRequestedSetName() throws ServiceException {
    var now = Instant.now();
    String setName1 = "test-set-name-1";
    String setName2 = "test-set-name-2";

    putItem(keyDb, withAllTimesSet(now).toBuilder().setSetName(setName1).build());
    putItem(keyDb, withAllTimesSet(now.plus(10, DAYS)).toBuilder().setSetName(setName1).build());
    putItem(keyDb, withAllTimesSet(now.minus(10, DAYS)).toBuilder().setSetName(setName1).build());

    putItem(keyDb, withAllTimesSet(now).toBuilder().setSetName(setName2).build());
    putItem(keyDb, withAllTimesSet(now.minus(10, DAYS)).toBuilder().setSetName(setName2).build());
    putItem(keyDb, withAllTimesSet(now.plus(100, DAYS)).toBuilder().setSetName(setName2).build());

    awaitAndAssertAllKeyCount(keyDb, setName1, 3);
    awaitAndAssertAllKeyCount(keyDb, setName2, 3);
    assertThat(keyDb.listAllKeysForSetName(setName1))
        .isInOrder(KeyDbUtil.getActiveKeysComparator());
    assertThat(keyDb.listAllKeysForSetName(setName2))
        .isInOrder(KeyDbUtil.getActiveKeysComparator());
  }

  /**
   * Calls keyDb.getActiveKeys() continuously for at most 5 seconds until the assertions succeed and
   * the expectedSize of keys are returned.
   */
  private static void awaitAndAssertActiveKeyCount(SpannerKeyDb keyDb, int expectedSize) {
    // Wait at least 1 second when asserting the expected size is 0 to avoid false positives.
    long minTimeSec = expectedSize == 0 ? 1 : 0;
    await()
        .pollDelay(minTimeSec, SECONDS)
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> {
              ImmutableList<EncryptionKey> keys =
                  keyDb.getActiveKeys(
                      DEFAULT_SET_NAME, KEY_ITEM_COUNT, Instant.now());

              assertThat(keys).isNotNull();
              assertThat(keys).hasSize(expectedSize);
            });
  }

  @Test
  public void createKey_successAddWithMigration() throws ServiceException {
    String keyId = "test-key-id";
    List<KeySplitData> keySplitData = new ArrayList<>();
    List<KeySplitData> migrationKeySplitData = new ArrayList<>();
    keySplitData.add(KeySplitData.newBuilder().setKeySplitKeyEncryptionKeyUri("URI").build());
    migrationKeySplitData.add(
        KeySplitData.newBuilder().setKeySplitKeyEncryptionKeyUri("MIG_URI").build());

    EncryptionKey expectedKey =
        EncryptionKey.newBuilder()
            .setKeyId(keyId)
            .setPublicKey("12345")
            .setPublicKeyMaterial("12345")
            .setJsonEncodedKeyset("67890")
            .setKeyEncryptionKeyUri("URI")
            .addAllKeySplitData(keySplitData)
            .setCreationTime(0L)
            .setExpirationTime(0L)
            .setMigrationJsonEncodedKeyset("09876")
            .setMigrationKeyEncryptionKeyUri("MIG_URI")
            .addAllMigrationKeySplitData(migrationKeySplitData)
            .build();

    keyDb.createKey(expectedKey);
    EncryptionKey receivedKey = keyDb.getKey(keyId);

    assertThat(receivedKey.getKeyId()).isEqualTo(keyId);
    assertThat(receivedKey.getPublicKey()).isEqualTo(expectedKey.getPublicKey());
    assertThat(receivedKey.getJsonEncodedKeyset()).isEqualTo(expectedKey.getJsonEncodedKeyset());
    assertThat(receivedKey.getMigrationKeyEncryptionKeyUri())
        .isEqualTo((expectedKey.getMigrationKeyEncryptionKeyUri()));
    assertThat(receivedKey.getMigrationJsonEncodedKeyset())
        .isEqualTo((expectedKey.getMigrationJsonEncodedKeyset()));
    assertThat(receivedKey.getMigrationKeySplitData(0))
        .isEqualTo((expectedKey.getMigrationKeySplitData(0)));
  }

  private static void awaitAndAssertAllKeyCount(SpannerKeyDb keyDb, int expectedSize) {
    awaitAndAssertAllKeyCount(keyDb, DEFAULT_SET_NAME, expectedSize);
  }

  private static void awaitAndAssertAllKeyCount(
      SpannerKeyDb keyDb, String setName, int expectedSize) {
    // Wait at least 1 second when asserting the expected size is 0 to avoid false positives.
    long minTimeSec = expectedSize == 0 ? 1 : 0;
    await()
        .pollDelay(minTimeSec, SECONDS)
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> {
              ImmutableList<EncryptionKey> keys = keyDb.listAllKeysForSetName(setName);
              assertThat(keys).isNotNull();
              assertThat(keys).hasSize(expectedSize);
              for (var key : keys) {
                assertThat(key.getSetName()).isEqualTo(setName);
              }
            });
  }
}
