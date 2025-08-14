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
import com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;
import com.google.scp.shared.api.model.Code;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

/** Interface for Key database properties */
public interface KeyDb {

  String DEFAULT_SET_NAME = "";

  /**
   * Returns keys that are active at a specific {@code instant} up to given {@code keyLimit}. Keys
   * are sorted by expiration time in descending order.
   *
   * @deprecated Use {@link #getActiveKeys(String, int, Instant)} and specify set name.
   */
  @Deprecated
  default ImmutableList<EncryptionKey> getActiveKeys(int keyLimit, Instant instant)
      throws ServiceException {
    return getActiveKeys(DEFAULT_SET_NAME, keyLimit, instant);
  }

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
   * @param instant the instant where the keys are active.
   * @param keyLimit the maximum number of keys to retrieve.
   */
  ImmutableList<EncryptionKey> getActiveKeys(String setName, int keyLimit, Instant instant)
      throws ServiceException;

  /** Returns all keys in the database without explicit ordering */
  ImmutableList<EncryptionKey> getAllKeys() throws ServiceException;

  /** Returns all keys for a given setName in the database */
  ImmutableList<EncryptionKey> listAllKeysForSetName(String setName) throws ServiceException;

  /**
   * Returns all the keys of a specified maximum age based on their creation timestamp.
   *
   * @deprecated Use {@link #listRecentKeys(String, Duration)} and specify set name.
   * @param maxAge the maximum age of returned keys.
   */
  @Deprecated
  default Stream<EncryptionKey> listRecentKeys(Duration maxAge) throws ServiceException {
    return listRecentKeys(DEFAULT_SET_NAME, maxAge);
  }

  /**
   * all recently created {@link EncryptionKey}s up to a specific age.
   *
   * @param maxAgeSeconds Maximum age of {@link EncryptionKey} that should be returned.
   */
  @Deprecated
  default Stream<EncryptionKey> listRecentKeys(int maxAgeSeconds) throws ServiceException {
    if (maxAgeSeconds < 0) {
      throw new ServiceException(
          Code.INVALID_ARGUMENT,
          KeyManagementErrorReason.INVALID_ARGUMENT.name(),
          String.format("%s should be positive.", maxAgeSeconds));
    }
    return listRecentKeys(Duration.ofSeconds(maxAgeSeconds));
  }

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
