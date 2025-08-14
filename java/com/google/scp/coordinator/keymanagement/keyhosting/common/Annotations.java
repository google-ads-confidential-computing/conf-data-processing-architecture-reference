/*
 * Copyright 2025 Google LLC
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

package com.google.scp.coordinator.keymanagement.keyhosting.common;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Annotations for Keyhosting. */
public final class Annotations {

  private Annotations() {}

  /** Binds cache control maximum for active public keys */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface CacheControlMaximum {}

  /** Binds disable activation time */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface DisableActivationTime {}

  /** Binds enable cache value to control server-side caching. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface EnableCache {}

  /** Binds cache refresh time in minutes. */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface CacheRefreshInMinutes {}

  /** Binds limit value that determines count of active keys returned from Key database */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface KeyLimit {}

  /**
   * Binds allowed migrators from a Key Sets Vending Config to control server-side key migration
   * vending.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface KeySetsVendingConfigAllowedMigrators {}
}
