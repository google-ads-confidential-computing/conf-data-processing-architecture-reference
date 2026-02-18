/*
 * Copyright 2023 Google LLC
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

package com.google.scp.operator.worker.selector;

import com.google.inject.Module;
import com.google.scp.operator.cpio.notificationclient.gcp.GcpNotificationClientModule;
import com.google.scp.operator.cpio.notificationclient.local.LocalNotificationClientModule;

/** CLI enum to select the notification implementation */
public enum NotificationClientSelector {
  GCP(new GcpNotificationClientModule()),
  LOCAL(new LocalNotificationClientModule());

  private final Module notificationClientGuiceModule;

  NotificationClientSelector(Module module) {
    this.notificationClientGuiceModule = module;
  }

  /**
   * Gets the guice module for the notificationClient
   *
   * @return specific notification module
   */
  public Module getNotificationModule() {
    return notificationClientGuiceModule;
  }
}
