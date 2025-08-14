/*
 * Copyright 2025 Google LLC
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

package com.google.scp.operator.cpio.jobclient.model;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import com.google.scp.operator.protos.shared.backend.ResultInfoProto.ResultInfo;

/** A class for defining the workgroup allocation function response. */
@AutoValue
public abstract class WorkgroupAllocationFuncResponse {

  /** Returns a new instance of the {@code WorkgroupAllocationFuncResponse.Builder} class. */
  public static Builder builder() {
    return new AutoValue_WorkgroupAllocationFuncResponse.Builder();
  }

  /** Returns the workgroup ID. Return empty Optional to process the job in current workgroup. */
  public abstract Optional<String> workgroupId();

  /** Returns the result info for workgroup allocation failures. */
  public abstract Optional<ResultInfo> resultInfo();

  /** Builder class for the {@code WorkgroupAllocationFuncResponse} class. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Set the workgroup ID. */
    public abstract Builder setWorkgroupId(String workgroupId);

    /**
     * Set the result info upon failure of the workgroup allocation function.
     */
    public abstract Builder setResultInfo(ResultInfo resultInfo);

    /**
     * Returns a new instance of the {@code WorkgroupAllocationFuncResponse} class from the builder.
     */
    public abstract WorkgroupAllocationFuncResponse build();
  }
}
