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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KeySetsConfigTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testMapper_noKeySets_readsExpected() throws Exception {
    // Given
    String json = "{\"key_sets\":[]}";

    // When
    KeySetsConfig configs = mapper.readValue(json, KeySetsConfig.class);

    // Then
    assertThat(configs).isEqualTo(KeySetsConfig.Builder.builder().keySets(List.of()).build());
  }

  @Test
  public void testMapper_hasKeySets_readsExpected() throws Exception {
    // Given
    String json =
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

    // When
    KeySetsConfig configs = mapper.readValue(json, KeySetsConfig.class);

    // Then
    assertThat(configs)
        .isEqualTo(
            KeySetsConfig.Builder.builder()
                .keySets(
                    List.of(
                        KeySetsConfig.KeySet.Builder.builder()
                            .name("set1")
                            .tinkTemplate("test_template1")
                            .count(5)
                            .validityInDays(6)
                            .ttlInDays(7)
                            .createMaxDaysAhead(8)
                            .overlapPeriodDays(9)
                            .backfillDays(10)
                            .build(),
                        KeySetsConfig.KeySet.Builder.builder()
                            .name("set2")
                            .tinkTemplate("test_template2")
                            .count(15)
                            .validityInDays(10)
                            .ttlInDays(100)
                            .build(),
                        KeySetsConfig.KeySet.Builder.builder()
                            .name("set3")
                            .tinkTemplate("test_template3")
                            .count(25)
                            .validityInDays(20)
                            .ttlInDays(0)
                            .overlapPeriodDays(24)
                            .backfillDays(12)
                            .build()))
                .build());
  }
}
