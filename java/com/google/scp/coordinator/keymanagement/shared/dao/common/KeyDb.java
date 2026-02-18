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

package com.google.scp.coordinator.keymanagement.shared.dao.common;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

/** Interface for Key database properties */
public interface KeyDb {

  // TODO(b/484326902): Cleanup/remove uses of this variable
  String DEFAULT_SET_NAME = "";

  /**
   * Returns active keys in descending expiration time order.
   *
   * @param setName the key set name.
   * @param keyLimit the maximum number of keys to retrieve.
   */
  default ImmutableList<EncryptionKey> getActiveKeys(String setName, int keyLimit)
      throws ServiceException {
    return getActiveKeys(setName, keyLimit, Instant.now());
  }

  /**
   * Returns active keys that have a public key in descending expiration time order.
   *
   * @param setName the key set name.
   * @param keyLimit the maximum number of keys to retrieve.
   */
  default ImmutableList<EncryptionKey> getActiveKeysWithPublicKey(String setName, int keyLimit)
      throws ServiceException {
    return getActiveKeys(setName, keyLimit, Instant.now()).stream()
        .filter(key -> !Strings.isNullOrEmpty(key.getPublicKey()))
        .collect(ImmutableList.toImmutableList());
  }

  /**
   * Returns keys active at a specific time in descending expiration time order.
   *
   * @param setName the key set name.
   * @param keyLimit the maximum number of keys to retrieve.
   * @param instant the instant where the keys are active.
   */
  ImmutableList<EncryptionKey> getActiveKeys(String setName, int keyLimit, Instant instant)
      throws ServiceException;

  /**
   * Returns keys active at within a specific time range in descending expiration time order.
   *
   * @param setName the key set name.
   * @param keyLimit the maximum number of keys to retrieve.
   * @param start keys returned must have expiration time after (exclusive)
   * @param end keys returned must have activation time before (inclusive)
   */
  ImmutableList<EncryptionKey> getActiveKeys(
      String setName, int keyLimit, Instant start, Instant end) throws ServiceException;

  /** Returns all keys in the database without explicit ordering */
  ImmutableList<EncryptionKey> getAllKeys() throws ServiceException;

  /** Returns all keys for a given setName in the database */
  ImmutableList<EncryptionKey> listAllKeysForSetName(String setName) throws ServiceException;

  /**
   * Returns all the keys of a specified maximum age based on their creation timestamp.
   *
   * @param setName the key set name.
   * @param maxAge the maximum age of returned keys.
   */
  Stream<EncryptionKey> listRecentKeys(String setName, Duration maxAge) throws ServiceException;

  /**
   * Performs a lookup of a single key, throwing a ServiceException if the key is not found.
   *
   * @param keyId the unique ID of the key (e.g. 'abcd123', not 'privateKeys/abcd123')
   */
  EncryptionKey getKey(String keyId) throws ServiceException;

  /** Create given key. */
  default void createKey(EncryptionKey key) throws ServiceException {
    createKey(key, true);
  }

  /** Create given keys. */
  void createKeys(ImmutableList<EncryptionKey> keys) throws ServiceException;

  /** Create key with overwrite option */
  void createKey(EncryptionKey key, boolean overwrite) throws ServiceException;

  // TODO(b/439619571): Look into refactoring to live under keymigration/ since methods shouldn't be
  // used elsewhere.
  /** Used to update key data for an existing key */
  void updateKeyMaterial(ImmutableList<EncryptionKey> keys) throws ServiceException;

  // TODO(b/439619571): Look into refactoring to live under keymigration/ since methods shouldn't be
  // used elsewhere.
  /** Used to update migration data for an existing key */
  void updateMigrationKeyMaterial(ImmutableList<EncryptionKey> keys) throws ServiceException;

  /**
   * Thrown when the KeyDB record contains a Status field that does not match a value of
   * EncryptionKeyStatus enum.
   */
  class InvalidEncryptionKeyStatusException extends RuntimeException {

    public InvalidEncryptionKeyStatusException(String message) {
      super(message);
    }
  }
}
