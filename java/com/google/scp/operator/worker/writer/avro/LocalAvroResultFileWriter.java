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

package com.google.scp.operator.worker.writer.avro;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import com.google.scp.operator.worker.model.Fact;
import com.google.scp.operator.worker.writer.LocalResultFileWriter;
import com.google.scp.protocol.avro.AvroResultsSchemaSupplier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;

/** Writes a local results file using the Avro format. */
public final class LocalAvroResultFileWriter implements LocalResultFileWriter {

  private AvroResultsSchemaSupplier schemaSupplier;

  @Inject
  LocalAvroResultFileWriter(AvroResultsSchemaSupplier schemaSupplier) {
    this.schemaSupplier = schemaSupplier;
  }

  /**
   * Write the results to an Avro file at the {@code Path} given.
   *
   * <p>The {@code Path} does not need to point to an existing file, this method will create a new
   * file. If a file already exists at the {@code Path} given then it will be overwritten. This is
   * the behavior of the {@code Files.newOutputStream} method that this relies on.
   *
   * <p>If exceptions occur mid-way during writing this function will leave a partially written
   * file.
   */
  @Override
  public void writeLocalFile(Stream<Fact> results, Path resultFilePath) throws FileWriteException {
    Schema schema = schemaSupplier.get();

    DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
    DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
    try {
      dataFileWriter.create(schema, Files.newOutputStream(resultFilePath, CREATE, APPEND));

      // Write all results to an Avro file. .append() call can throw IOExceptions so using an
      // Iterator is cleaner for exception handling.
      Iterator<Fact> resultsIterator = results.iterator();
      while (resultsIterator.hasNext()) {
        Fact Fact = resultsIterator.next();
        GenericRecord FactRecord = factToGenericRecord(Fact);
        dataFileWriter.append(FactRecord);
      }

      dataFileWriter.close();
    } catch (IOException e) {
      throw new FileWriteException("Failed to write local Avro file", e);
    }
  }

  @Override
  public String getFileExtension() {
    return ".avro";
  }

  private GenericRecord factToGenericRecord(Fact Fact) {
    GenericRecord genericRecord = new GenericData.Record(schemaSupplier.get());
    genericRecord.put("key", Fact.key());
    genericRecord.put("value", Fact.value());
    return genericRecord;
  }
}
