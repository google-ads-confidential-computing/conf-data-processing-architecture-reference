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

package com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyset;

import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb.DEFAULT_SET_NAME;
import static com.google.scp.shared.util.KeyParams.DEFAULT_TINK_TEMPLATE;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import com.google.scp.coordinator.keymanagement.keygeneration.tasks.common.keyset.KeySetManager.ConfigCacheDuration;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KeySetManagerTest {

  private static final int TEST_COUNT = new Random().nextInt();
  private static final int TEST_VALIDITY_IN_DAYS = new Random().nextInt();
  private static final int TEST_TTL_IN_DAYS = new Random().nextInt();
  private static final int TEST_CREATE_MAX_DAYS_AHEAD = new Random().nextInt();
  private static final ConfigCacheDuration TEST_CACHE_DURATION =
      ConfigCacheDuration.of(Duration.ofSeconds(1));

  @Test
  public void testGetConfigs_noConfig_returnsOnlyDefaultConfig() {
    // Given
    KeySetManager manager = createKeySetManager(null);

    // When
    ImmutableList<KeySetConfig> configs = manager.getConfigs();

    // Then
    assertThat(configs).containsExactly(createKeySetConfig(DEFAULT_SET_NAME));
  }

  @Test
  public void testGetConfigs_nullConfigJson_returnsOnlyDefaultConfig() {
    // Given
    String config = "null";
    KeySetManager manager = createKeySetManager(config);

    // When
    ImmutableList<KeySetConfig> configs = manager.getConfigs();

    // Then
    assertThat(configs).containsExactly(createKeySetConfig(DEFAULT_SET_NAME));
  }

  @Test
  public void testGetConfigs_explicitNoKeysets_emptyAndNoDefaultConfig() {
    // Given
    String config = "{\"key_sets\":[]}";
    KeySetManager manager = createKeySetManager(config);

    // When
    ImmutableList<KeySetConfig> configs = manager.getConfigs();

    // Then
    assertThat(configs).isEmpty();
  }

  @Test
  public void testGetConfigs_multipleKeySetsInConfig_returnsExpected() {
    // Given
    String config =
        """
        {
          "key_sets": [
            { "name": "set1" },
            { "name": "set2" },
            { "name": "set3" }
          ]
        }
        """;
    KeySetManager manager = createKeySetManager(config);

    // When
    ImmutableList<KeySetConfig> configs = manager.getConfigs();

    // Then
    assertThat(configs)
        .containsExactly(
            createKeySetConfig("set1"), createKeySetConfig("set2"), createKeySetConfig("set3"));
  }

  @Test
  public void testGetConfigs_configChanged_returnsRefreshed() throws Exception {
    // Given
    String config1 =
        """
        { "key_sets": [{ "name": "v1" }] }
        """;
    String config2 =
        """
        { "key_sets": [{ "name": "v2" }] }
        """;
    Iterator<Optional<String>> jsons = Stream.of(config1, config2).map(Optional::of).iterator();
    KeySetManager manager = createKeySetManagerProvider(jsons::next);

    // When
    ImmutableList<KeySetConfig> configs1 = manager.getConfigs();
    Thread.sleep(TEST_CACHE_DURATION.value.toMillis() + 500);
    ImmutableList<KeySetConfig> configs2 = manager.getConfigs();

    // Then
    assertThat(configs1).isNotEqualTo(configs2);
  }

  @Test
  public void testGetConfigs_configChanged_returnsUnexpiredCache() throws Exception {
    // Given
    String config1 =
        """
        { "key_sets": [{ "name": "v1" }] }
        """;
    String config2 =
        """
        { "key_sets": [{ "name": "v2" }] }
        """;
    Iterator<Optional<String>> jsons = Stream.of(config1, config2).map(Optional::of).iterator();
    KeySetManager manager = createKeySetManagerProvider(jsons::next);

    // When
    ImmutableList<KeySetConfig> configs1 = manager.getConfigs();
    Thread.sleep(TEST_CACHE_DURATION.value.toMillis() - 500);
    ImmutableList<KeySetConfig> configs2 = manager.getConfigs();

    // Then
    assertThat(configs1).isEqualTo(configs2);
  }

  @Test
  public void testGetConfigs_configWithNulls_returnsExpected() {
    // Given
    String config =
        """
        {
          "key_sets": [
            {
              "name": "test_set",
              "tink_template": null,
              "count": null,
              "validity_in_days": null,
              "ttl_in_days": null,
              "create_max_days_ahead": null,
              "overlap_period_days": null
            }
          ]
        }
        """;
    KeySetManager manager = createKeySetManager(config);

    // When
    ImmutableList<KeySetConfig> configs = manager.getConfigs();

    // Then
    assertThat(configs).containsExactly(createKeySetConfig("test_set"));
  }

  @Test
  public void testGetConfigs_allValuesSet_returnsExpected() {
    // Given
    String config =
        """
        {
          "key_sets": [
            {
              "name": "set1",
              "tink_template": "test_template",
              "count": 5,
              "validity_in_days": 0,
              "ttl_in_days": 0,
              "create_max_days_ahead": 13,
              "overlap_period_days": 24
            }
          ]
        }
        """;
    KeySetManager manager = createKeySetManager(config);

    // When
    ImmutableList<KeySetConfig> configs = manager.getConfigs();

    // Then
    assertThat(configs)
        .containsExactly(KeySetConfig.create("set1", "test_template", 5, 0, 0, 13, 24));
  }

  private static KeySetManager createKeySetManager(String config) {
    return new KeySetManager(
        Providers.of(Optional.ofNullable(config)),
        TEST_COUNT,
        TEST_VALIDITY_IN_DAYS,
        TEST_TTL_IN_DAYS,
        TEST_CREATE_MAX_DAYS_AHEAD,
        new ConfigCacheDuration());
  }

  private static KeySetManager createKeySetManagerProvider(
      Provider<Optional<String>> configProvider) {
    return new KeySetManager(
        configProvider,
        TEST_COUNT,
        TEST_VALIDITY_IN_DAYS,
        TEST_TTL_IN_DAYS,
        TEST_CREATE_MAX_DAYS_AHEAD,
        TEST_CACHE_DURATION);
  }

  private static KeySetConfig createKeySetConfig(String setName) {
    return KeySetConfig.create(
        setName,
        DEFAULT_TINK_TEMPLATE,
        TEST_COUNT,
        TEST_VALIDITY_IN_DAYS,
        TEST_TTL_IN_DAYS,
        TEST_CREATE_MAX_DAYS_AHEAD,
        0);
  }
}
