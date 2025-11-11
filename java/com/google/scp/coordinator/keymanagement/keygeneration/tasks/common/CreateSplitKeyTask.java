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

package com.google.scp.coordinator.keymanagement.keygeneration.tasks.common;

import com.google.scp.shared.api.exception.ServiceException;
import java.time.Instant;

/** Interface for task classes that generate split keys */
public interface CreateSplitKeyTask {

  /**
   * Amount of days a key must be valid for to not be refreshed. Keys that expire before (now +
   * keyRefreshWindow) should be replaced with a new key.
   */
  int KEY_REFRESH_WINDOW_DAYS = 1;

  /**
   * The actual key generation process. Performs the necessary key exchange key fetching (if
   * applicable), encryption key generation and splitting, key storage request, and database
   * persistence with signatures.
   *
   * @param setName the name of the key set the keys belong to.
   * @param tinkTemplate the Tink template used for key generation.
   * @param count the number of keys is ensured to be active.
   * @param validityInDays the number of days each key should be active/valid for before expiring.
   * @param ttlInDays the number of days each key should be stored in the database.
   * @param activation the instant when the key should be active for encryption.
   */
  void createSplitKey(
      String setName,
      String tinkTemplate,
      int count,
      int validityInDays,
      int ttlInDays,
      Instant activation)
      throws ServiceException;

  /**
   * Ensures {@code numDesiredKeys} active keys are currently available by creating new immediately
   * active keys to meet that number.
   *
   * <p>The created immediately active keys expire in {@code validityInDays} days and will be in the
   * key database for {@code ttlInDays} days. The subsequent replacement keys will be active when
   * the currently active key expires, the replacement key will also expire in {@code
   * validityInDays} and in the key database for {@code ttlInDays} days.
   *
   * @param setName the name of the key set the keys belong to.
   * @param tinkTemplate the Tink template used for key generation.
   * @param numDesiredKeys the number of keys is ensured to be active.
   * @param validityInDays the number of days each key should be active/valid for before expiring.
   * @param ttlInDays the number of days each key should be stored in the database.
   * @param createMaxDaysAhead the number of days ahead that a key can be created
   * @param overlapPeriodDays the number of days each consecutive active set should overlap
   */
  void create(
      String setName,
      String tinkTemplate,
      int numDesiredKeys,
      int validityInDays,
      int ttlInDays,
      int createMaxDaysAhead,
      int overlapPeriodDays)
      throws ServiceException;
}
