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

import com.google.common.collect.ImmutableList;
import com.google.scp.operator.worker.model.Fact;
import com.google.scp.protocol.avro.AvroResultsSchemaSupplier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;

/** Simple utility to read an Avro results file, used for testing. */
public final class AvroResultsFileReader {

  AvroResultsSchemaSupplier avroResultsSchemaSupplier;

  @Inject
  AvroResultsFileReader(AvroResultsSchemaSupplier avroResultsSchemaSupplier) {
    this.avroResultsSchemaSupplier = avroResultsSchemaSupplier;
  }

  /** Reads the Avro results file at the path given to a list. */
  public ImmutableList<Fact> readAvroResultsFile(Path path) throws IOException {
    DatumReader<GenericRecord> datumReader =
        new GenericDatumReader<>(avroResultsSchemaSupplier.get());
    DataFileStream<GenericRecord> streamReader =
        new DataFileStream<>(Files.newInputStream(path), datumReader);

    return Stream.generate(() -> readRecordToFact(streamReader))
        .takeWhile(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableList());
  }

  private static Optional<Fact> readRecordToFact(DataFileStream<GenericRecord> streamReader) {
    if (streamReader.hasNext()) {
      GenericRecord genericRecord = streamReader.next();
      String key = String.valueOf(genericRecord.get("key"));
      Long value = (Long) genericRecord.get("value");
      return Optional.of(Fact.create(key, value));
    }

    return Optional.empty();
  }
}
