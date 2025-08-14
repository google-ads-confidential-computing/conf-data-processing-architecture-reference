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

package com.google.scp.coordinator.keymanagement.keyhosting.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableSet;
import com.google.scp.coordinator.keymanagement.shared.serverless.common.RequestContext;
import com.google.scp.coordinator.keymanagement.shared.util.LogMetricHelper;
import com.google.scp.coordinator.keymanagement.testutils.FakeEncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import java.util.Base64;
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
public class KeyMigrationVendingUtilTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final String APPROVED_CALLER = "approved-caller@google.com";
  private static final String SET_NAME = "TestSet";
  private static final EncryptionKey TEST_KEY_WITH_MIGRATION_DATA =
      FakeEncryptionKey.createWithMigration().toBuilder().setSetName(SET_NAME).build();
  private static final EncryptionKey TEST_KEY_WITHOUT_MIGRATION_DATA =
      FakeEncryptionKey.create().toBuilder().setSetName(SET_NAME).build();

  @Mock private RequestContext request;
  private LogMetricHelper logMetricHelper;

  @Before
  public void setUp() {
    logMetricHelper = new LogMetricHelper("test");
  }

  @Test
  public void vendAccordingToConfig_approvedSetName_vendsMigrationKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of(SET_NAME);

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITH_MIGRATION_DATA, request, allowedMigrators, logMetricHelper);

    // Then
    verifyUsesMigrationEncryptionData(vendedKey);
    verify(request).getFirstHeader("Authorization");
  }

  @Test
  public void vendAccordingToConfig_approvedCaller_vendsMigrationKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of(APPROVED_CALLER);
    String fakeJwt = createFakeJwt(APPROVED_CALLER);
    doReturn(Optional.of("Bearer: " + fakeJwt)).when(request).getFirstHeader("Authorization");

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITH_MIGRATION_DATA, request, allowedMigrators, logMetricHelper);

    // Then
    verifyUsesMigrationEncryptionData(vendedKey);
    verify(request).getFirstHeader("Authorization");
  }

  @Test
  public void vendAccordingToConfig_noApprovedSetNames_vendsStandardKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of("some-other-set");

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITH_MIGRATION_DATA, request, allowedMigrators, logMetricHelper);

    // Then
    verifyUsesBaseEncryptionData(TEST_KEY_WITH_MIGRATION_DATA, vendedKey);
    verify(request).getFirstHeader("Authorization");
  }

  @Test
  public void vendAccordingToConfig_noApprovedCallers_vendsStandardKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of(APPROVED_CALLER);
    String fakeJwt = createFakeJwt("unapproved-caller@google.com");
    doReturn(Optional.of("Bearer: " + fakeJwt)).when(request).getFirstHeader("Authorization");

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITH_MIGRATION_DATA, request, allowedMigrators, logMetricHelper);

    // Then
    verifyUsesBaseEncryptionData(TEST_KEY_WITH_MIGRATION_DATA, vendedKey);
    verify(request).getFirstHeader("Authorization");
  }

  @Test
  public void vendAccordingToConfig_noAuthHeader_vendsStandardKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of(APPROVED_CALLER);
    doReturn(Optional.empty()).when(request).getFirstHeader("Authorization");

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITH_MIGRATION_DATA, request, allowedMigrators, logMetricHelper);

    // Then
    verifyUsesBaseEncryptionData(TEST_KEY_WITH_MIGRATION_DATA, vendedKey);
    verify(request).getFirstHeader("Authorization");
  }

  @Test
  public void vendAccordingToConfig_malformedAuthHeader_vendsStandardKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of(APPROVED_CALLER);
    doReturn(Optional.of("not a bearer token")).when(request).getFirstHeader("Authorization");

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITH_MIGRATION_DATA, request, allowedMigrators, logMetricHelper);

    // Then
    verifyUsesBaseEncryptionData(TEST_KEY_WITH_MIGRATION_DATA, vendedKey);
    verify(request).getFirstHeader("Authorization");
  }

  @Test
  public void vendAccordingToConfig_malformedJwt_vendsStandardKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of(APPROVED_CALLER);
    doReturn(Optional.of("Bearer: not.a.jwt")).when(request).getFirstHeader("Authorization");

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITH_MIGRATION_DATA, request, allowedMigrators, logMetricHelper);

    // Then
    verifyUsesBaseEncryptionData(TEST_KEY_WITH_MIGRATION_DATA, vendedKey);
    verify(request).getFirstHeader("Authorization");
  }

  @Test
  public void vendAccordingToConfig_noMigrationData_vendsStandardKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of(SET_NAME);

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITHOUT_MIGRATION_DATA, request, allowedMigrators, logMetricHelper);

    // Then
    verifyUsesBaseEncryptionData(TEST_KEY_WITHOUT_MIGRATION_DATA, vendedKey);
    verify(request).getFirstHeader("Authorization");
  }

  @Test
  public void vendAccordingToConfig_emptyAllowedMigrators_vendsStandardKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of();

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITH_MIGRATION_DATA, request, allowedMigrators, logMetricHelper);

    // Then
    verifyUsesBaseEncryptionData(TEST_KEY_WITH_MIGRATION_DATA, vendedKey);
    verify(request, never()).getFirstHeader("Authorization");
  }

  @Test
  public void vendAccordingToConfig_missingOnlySomeMigrationData_vendsStandardKey() {
    // Given
    ImmutableSet<String> allowedMigrators = ImmutableSet.of(SET_NAME);

    // When
    EncryptionKey vendedKey =
        KeyMigrationVendingUtil.vendAccordingToConfig(
            TEST_KEY_WITH_MIGRATION_DATA.toBuilder().clearMigrationKeySplitData().build(),
            request,
            allowedMigrators,
            logMetricHelper);

    // Then
    verifyUsesBaseEncryptionData(TEST_KEY_WITH_MIGRATION_DATA, vendedKey);
    verify(request).getFirstHeader("Authorization");
  }

  /** Creates a fake JWT string with a given email claim for testing. */
  private String createFakeJwt(String email) {
    String payload = String.format("{\"email\":\"%s\"}", email);
    String encodedPayload =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
    // A dummy header and signature are needed to pass the split check in the task.
    return "header." + encodedPayload + ".signature";
  }

  private void verifyUsesBaseEncryptionData(EncryptionKey inputKey, EncryptionKey vendedKey) {
    // Then
    assertThat(vendedKey.getJsonEncodedKeyset()).isEqualTo(inputKey.getJsonEncodedKeyset());
    assertThat(vendedKey.getKeyEncryptionKeyUri()).isEqualTo(inputKey.getKeyEncryptionKeyUri());
    assertThat(vendedKey.getKeySplitDataList()).isEqualTo(inputKey.getKeySplitDataList());
    assertTrue(vendedKey.getMigrationJsonEncodedKeyset().isEmpty());
    assertTrue(vendedKey.getMigrationKeyEncryptionKeyUri().isEmpty());
    assertTrue(vendedKey.getMigrationKeySplitDataList().isEmpty());
  }

  private void verifyUsesMigrationEncryptionData(EncryptionKey vendedKey) {
    // Then
    assertThat(vendedKey.getJsonEncodedKeyset())
        .isEqualTo(
            KeyMigrationVendingUtilTest.TEST_KEY_WITH_MIGRATION_DATA
                .getMigrationJsonEncodedKeyset());
    assertThat(vendedKey.getKeyEncryptionKeyUri())
        .isEqualTo(
            KeyMigrationVendingUtilTest.TEST_KEY_WITH_MIGRATION_DATA
                .getMigrationKeyEncryptionKeyUri());
    assertThat(vendedKey.getKeySplitDataList())
        .isEqualTo(
            KeyMigrationVendingUtilTest.TEST_KEY_WITH_MIGRATION_DATA
                .getMigrationKeySplitDataList());
    assertTrue(vendedKey.getMigrationJsonEncodedKeyset().isEmpty());
    assertTrue(vendedKey.getMigrationKeyEncryptionKeyUri().isEmpty());
    assertTrue(vendedKey.getMigrationKeySplitDataList().isEmpty());
  }
}
