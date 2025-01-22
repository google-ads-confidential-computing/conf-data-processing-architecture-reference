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

package com.google.scp.operator.worker.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.time.temporal.ChronoUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.scp.operator.worker.model.Fact;
import com.google.scp.operator.worker.model.Report;
import com.google.scp.privacy.budgeting.model.PrivacyBudgetKey;
import java.time.Instant;
import java.util.stream.IntStream;

/** Generates fake reports for testing purposes. */
public class FakeReportGenerator {
  /** Generates fake facts for testing purposes. */
  public static class FakeFactGenerator {
    /** Generates fake fact with key=id, value=value for testing purposes. */
    public static Fact generate(String id, int value) {
      return Fact.builder().setKey(String.valueOf(id)).setValue(value).build();
    }
  }

  /**
   * Given an id, returns the following Report(PrivacyBudget: PrivacyBudget("dummy"),
   * AttributionDestination: "dummy",* AttributionReportTo: "dummy", OriginalReportTime: "1970-01-01
   * 00:00:01", Facts: facts
   */
  public static Report generate(ImmutableList<Fact> facts) {
    return Report.builder()
        .setPrivacyBudgetKey(
            PrivacyBudgetKey.builder()
                .setKey("dummy")
                .setOriginalReportTime(Instant.EPOCH.plus(1, SECONDS))
                .build())
        .setAttributionDestination("dummy")
        .setAttributionReportTo("dummy")
        .setOriginalReportTime(Instant.EPOCH.plus(1, SECONDS))
        .addAllFact(facts)
        .build();
  }

  /**
   * Given an id, returns the following Report(PrivacyBudgetKey: "1", AttributionDestination: "1",
   * AttributionReportTo: "1", OriginalReportTime: "1970-01-01 00:00:01", Facts: [Fact("1", 1)])
   * containing 'id' Facts.
   */
  public static Report generate(int id) {
    return Report.builder()
        .setPrivacyBudgetKey(
            PrivacyBudgetKey.builder()
                .setKey(String.valueOf(id))
                .setOriginalReportTime(Instant.EPOCH.plus(id, SECONDS))
                .build())
        .setAttributionDestination(String.valueOf(id))
        .setAttributionReportTo(String.valueOf(id))
        .setOriginalReportTime(Instant.EPOCH.plus(id, SECONDS))
        .addAllFact(
            IntStream.range(0, id)
                .mapToObj(i -> Fact.builder().setKey(String.valueOf(id)).setValue(id).build())
                .collect(toImmutableList()))
        .build();
  }

  private FakeReportGenerator() {}
}
