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

/**
 * Create an instance.
 *
 * @param name the name of the key set.
 * @param tinkTemplate the Tink template to be used for key generation.
 * @param count the number of keys created for each rotation.
 * @param validityInDays the validity of the keys in number of days.
 * @param ttlInDays the TTL of the keys in number of days.
 * @param createMaxDaysAhead number of days ahead allowed to create next active set.
 * @param overlapPeriodDays number of days active keys can overlap.
 * @param backfillDays number of days allowed for a key to be used past its expiration date for a
 *     backfill job.
 */
public record KeySetConfig(
    String name,
    String tinkTemplate,
    int count,
    int validityInDays,
    int ttlInDays,
    int createMaxDaysAhead,
    int overlapPeriodDays,
    int backfillDays) {}
