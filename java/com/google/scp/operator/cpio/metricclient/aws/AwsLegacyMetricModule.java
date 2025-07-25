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

package com.google.scp.operator.cpio.metricclient.aws;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.scp.operator.cpio.metricclient.MetricClient;
import com.google.scp.operator.cpio.metricclient.local.LocalMetricClient;
import com.google.scp.operator.cpio.metricclient.model.Annotations.LegacyMetricClient;

/**
 * This is a temporary module provides legacy metric client. Since AWS is not part of the
 * OpenTelemetry migration, this client does not send metrics to the cloud. The legacy metric client
 * is to support dual write during OpenTelemetry migration. Once the migration is done, this module
 * will be removed.
 */
@Singleton
public class AwsLegacyMetricModule extends AbstractModule {

  @Provides
  @LegacyMetricClient
  public MetricClient provideLegacyMetricClient() {
    return new LocalMetricClient();
  }
}
