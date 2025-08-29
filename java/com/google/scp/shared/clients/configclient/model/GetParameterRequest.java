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

package com.google.scp.shared.clients.configclient.model;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * A class for defining parameters used to get a parameter.
 *
 * <p>The parameter storage layer will be invoked in the format:
 * `{paramPrefix}-{environment}-{workgroup}-{param}`, with the inclusion of the parameter prefix, the
 * environment, and the workgroup controlled by the arguments.
 */
@AutoValue
public abstract class GetParameterRequest {

  /** SCP infrastructure parameter prefix. */
  public static String SCP_PARAM_PREFIX = "scp";

  /** Returns a new instance of the {@code GetParameterRequest.Builder} class. */
  public static Builder builder() {
    return new AutoValue_GetParameterRequest.Builder()
        .setIncludeEnvironmentPrefix(false)
        .setIncludeWorkgroupPrefix(false)
        .setLatest(false);
  }

  /** Returns the base name of the parameter. */
  public abstract String getParamName();

  /** Returns the prefix of the parameter. */
  public abstract Optional<String> getParamPrefix();

  /** Returns whether to include the environment in the requested parameter. */
  public abstract boolean getIncludeEnvironmentPrefix();

  /** Returns whether to include the workgroup in the requested parameter. */
  public abstract boolean getIncludeWorkgroupPrefix();

  /** Return whether to fetch the latest parameter value. */
  public abstract boolean getLatest();

  /** Builder class for the {@code GetParameterRequest} class. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Set the base name of the parameter. */
    public abstract Builder setParamName(String value);

    /** Set the prefix of the parameter. */
    public abstract Builder setParamPrefix(String value);

    /** Set whether to include the environment in the requested parameter. */
    public abstract Builder setIncludeEnvironmentPrefix(boolean value);

    /** Set whether to include the workgroup in the requested parameter. */
    public abstract Builder setIncludeWorkgroupPrefix(boolean value);

    /** Set whether to fetch the latest parameter value. */
    public abstract Builder setLatest(boolean value);

    /** Returns a new instance of the {@code GetParameterRequest} class from the builder. */
    public abstract GetParameterRequest build();
  }
}
