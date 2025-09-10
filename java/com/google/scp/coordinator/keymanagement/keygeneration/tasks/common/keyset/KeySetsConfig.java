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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.Optional;

/**
 * This class is the key set configuration model defining the schema for the JSON deserialization.
 */
@AutoValue
@JsonDeserialize(builder = KeySetsConfig.Builder.class)
abstract class KeySetsConfig {

  @JsonProperty("key_sets")
  abstract List<KeySet> keySets();

  @AutoValue.Builder
  abstract static class Builder {

    @JsonProperty("key_sets")
    abstract KeySetsConfig.Builder keySets(List<KeySet> keySets);

    abstract KeySetsConfig build();

    @JsonCreator
    static KeySetsConfig.Builder builder() {
      return new AutoValue_KeySetsConfig.Builder();
    }
  }

  @AutoValue
  @JsonDeserialize(builder = KeySet.Builder.class)
  abstract static class KeySet {

    @JsonProperty("name")
    abstract String name();

    abstract Optional<Integer> count();

    abstract Optional<Integer> validityInDays();

    abstract Optional<Integer> ttlInDays();

    abstract Optional<String> tinkTemplate();

    abstract Optional<Integer> createMaxDaysAhead();

    abstract Optional<Integer> overlapPeriodDays();

    abstract Optional<Boolean> noRefreshWindow();

    @JsonIgnoreProperties(ignoreUnknown = true)
    @AutoValue.Builder
    abstract static class Builder {

      @JsonProperty("name")
      abstract Builder name(String name);

      @JsonProperty("count")
      Builder count(Integer count) {
        return count(Optional.ofNullable(count));
      }

      abstract Builder count(Optional<Integer> count);

      @JsonProperty("validity_in_days")
      Builder validityInDays(Integer validityInDays) {
        return validityInDays(Optional.ofNullable(validityInDays));
      }

      abstract Builder validityInDays(Optional<Integer> validityInDays);

      @JsonProperty("ttl_in_days")
      Builder ttlInDays(Integer ttlInDays) {
        return ttlInDays(Optional.ofNullable(ttlInDays));
      }

      abstract Builder ttlInDays(Optional<Integer> ttlInDays);

      @JsonProperty("create_max_days_ahead")
      Builder createMaxDaysAhead(Integer createMaxDaysAhead) {
        return createMaxDaysAhead(Optional.ofNullable(createMaxDaysAhead));
      }

      abstract Builder createMaxDaysAhead(Optional<Integer> createMaxDaysAhead);

      @JsonProperty("overlap_period_days")
      Builder overlapPeriodDays(Integer overlapPeriodDays) {
        return overlapPeriodDays(Optional.ofNullable(overlapPeriodDays));
      }

      abstract Builder overlapPeriodDays(Optional<Integer> overlapPeriodDays);

      @JsonProperty("no_refresh_window")
      Builder noRefreshWindow(Boolean noRefreshWindow) {
        return noRefreshWindow(Optional.ofNullable(noRefreshWindow));
      }

      abstract Builder noRefreshWindow(Optional<Boolean> overlapPeriodDays);

      @JsonProperty("tink_template")
      Builder tinkTemplate(String tinkTemplate) {
        return tinkTemplate(Optional.ofNullable(tinkTemplate));
      }

      abstract Builder tinkTemplate(Optional<String> tinkTemplate);

      abstract KeySet build();

      @JsonCreator
      static KeySet.Builder builder() {
        return new AutoValue_KeySetsConfig_KeySet.Builder();
      }
    }
  }
}
