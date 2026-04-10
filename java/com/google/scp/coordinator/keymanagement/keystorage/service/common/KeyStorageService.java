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

package com.google.scp.coordinator.keymanagement.keystorage.service.common;

import static com.google.scp.shared.api.model.Code.INVALID_ARGUMENT;

import com.google.inject.Inject;
import com.google.scp.coordinator.keymanagement.keystorage.converters.EncryptionKeyConverter;
import com.google.scp.coordinator.keymanagement.keystorage.tasks.common.CreateKeyTask;
import com.google.scp.coordinator.protos.keymanagement.keystorage.api.v1.CreateKeyRequestProto.CreateKeyRequest;
import com.google.scp.coordinator.protos.keymanagement.shared.api.v1.EncryptionKeyProto.EncryptionKey;
import com.google.scp.shared.api.exception.ServiceException;

/** A service with a collection of tasks for saving keys. */
public final class KeyStorageService {

  private final CreateKeyTask createKeyTask;

  @Inject
  public KeyStorageService(CreateKeyTask createKeyTask) {
    this.createKeyTask = createKeyTask;
  }

  /**
   * Handles a request to save a key, returning an {@link EncryptionKey} after populating the
   * signature field.
   */
  public EncryptionKey createKey(CreateKeyRequest request) throws ServiceException {
    try {
      var receivedKey =
          EncryptionKeyConverter.toStorageEncryptionKey(request.getKeyId(), request.getKey());
      var storedKey =
          createKeyTask.createKey(
              receivedKey, request.getEncryptedKeySplit(), request.getMigrationEncryptedKeySplit());
      return EncryptionKeyConverter.toApiEncryptionKey(storedKey);
    } catch (IllegalArgumentException ex) {
      throw new ServiceException(INVALID_ARGUMENT, INVALID_ARGUMENT.name(), ex.getMessage(), ex);
    }
  }
}
