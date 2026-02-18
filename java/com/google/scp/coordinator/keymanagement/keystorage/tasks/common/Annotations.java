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

package com.google.scp.coordinator.keymanagement.keystorage.tasks.common;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Annotations for Tasks. */
public final class Annotations {

  private Annotations() {}

  /** Binds instance of Kms Key Aead used to validate encrypted private keys/splits. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface KmsKeyAead {}

  /** Binds instance of Coordinator Key Aead. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface CoordinatorKeyAead {}

  /**
   * Binds the String URI of the Coordinator B cloud KMS key used for encrypting {@link
   * com.google.scp.coordinator.protos.keymanagement.shared.backend.DataKeyProto.DataKey}s delivered
   * to Coordinator A.
   *
   * <p>Format: {@code aws-kms://arn:...}
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface CoordinatorKekUri {}

  /** Binds the populate migration key data environment variable. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface PopulateMigrationKeyData {}

  /** Binds the kms key base uri environment variable. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface KmsKeyEncryptionKeyBaseUri {}

  /** Binds the migration kms key base uri environment variable. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface MigrationKmsKeyEncryptionKeyBaseUri {}

  /** Binds the kms client for creating aeads. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface KmsAeadClient {}

  /** Binds the migration kms client for creating aeads. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface MigrationKmsAeadClient {}
}
