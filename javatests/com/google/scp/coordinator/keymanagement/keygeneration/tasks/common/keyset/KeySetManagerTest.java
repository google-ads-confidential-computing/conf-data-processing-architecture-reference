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
import static org.junit.Assert.assertThrows;

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

  private static final int TEST_CREATE_MAX_DAYS_AHEAD = new Random().nextInt();
  private static final ConfigCacheDuration TEST_CACHE_DURATION =
      ConfigCacheDuration.of(Duration.ofSeconds(1));

  @Test
  public void testGetConfigs_noConfig_throwsException() {
    KeySetManager manager = createKeySetManager(null);
    assertThrows(IllegalStateException.class, manager::getConfigs);
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
            {
              "name": "set1",
              "tink_template": "test_template1",
              "count": 5,
              "validity_in_days": 6,
              "ttl_in_days": 7,
              "create_max_days_ahead": 8,
              "overlap_period_days": 9,
              "backfill_days": 10
            },
            {
              "name": "set2",
              "tink_template": "test_template2",
              "count": 15,
              "validity_in_days": 10,
              "ttl_in_days": 100
            },
            {
              "name": "set3",
              "tink_template": "test_template3",
              "count": 25,
              "validity_in_days": 20,
              "ttl_in_days": 0,
              "overlap_period_days": 24,
              "backfill_days": 12
            }
          ]
        }
        """;
    KeySetManager manager = createKeySetManager(config);

    // When
    ImmutableList<KeySetConfig> configs = manager.getConfigs();

    // Then
    assertThat(configs)
        .containsExactly(
            new KeySetConfig("set1", "test_template1", 5, 6, 7, 8, 9, 10),
            new KeySetConfig(
                "set2", "test_template2", 15, 10, 100, TEST_CREATE_MAX_DAYS_AHEAD, 0, 0),
            new KeySetConfig(
                "set3", "test_template3", 25, 20, 0, TEST_CREATE_MAX_DAYS_AHEAD, 24, 12));
  }

  @Test
  public void testGetConfigs_configChanged_returnsRefreshed() throws Exception {
    testCache(true);
  }

  @Test
  public void testGetConfigs_configChanged_returnsUnexpiredCache() throws Exception {
    testCache(false);
  }

  private void testCache(boolean shouldRefresh) throws Exception {
    // Given
    String config1 =
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
              "overlap_period_days": 24,
              "backfill_days": 12
            }
          ]
        }
        """;
    String config2 =
        """
        {
          "key_sets": [
            {
              "name": "set2",
              "tink_template": "test_template",
              "count": 15,
              "validity_in_days": 0,
              "ttl_in_days": 0,
              "create_max_days_ahead": 13,
              "overlap_period_days": 24,
              "backfill_days": 12
            }
          ]
        }
        """;
    Iterator<Optional<String>> jsons = Stream.of(config1, config2).map(Optional::of).iterator();
    KeySetManager manager = createKeySetManagerProvider(jsons::next);

    // When
    ImmutableList<KeySetConfig> configs1 = manager.getConfigs();
    var sleepMillis = TEST_CACHE_DURATION.value.toMillis() + (shouldRefresh ? +500 : -500);
    Thread.sleep(sleepMillis);
    ImmutableList<KeySetConfig> configs2 = manager.getConfigs();

    // Then
    if (shouldRefresh) {
      assertThat(configs1).isNotEqualTo(configs2);
    } else {
      assertThat(configs1).isEqualTo(configs2);
    }
  }

  private static KeySetManager createKeySetManager(String config) {
    return new KeySetManager(
        Providers.of(Optional.ofNullable(config)),
        TEST_CREATE_MAX_DAYS_AHEAD,
        new ConfigCacheDuration());
  }

  private static KeySetManager createKeySetManagerProvider(
      Provider<Optional<String>> configProvider) {
    return new KeySetManager(configProvider, TEST_CREATE_MAX_DAYS_AHEAD, TEST_CACHE_DURATION);
  }
}
