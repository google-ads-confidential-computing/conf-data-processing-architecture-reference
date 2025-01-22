package com.google.scp.coordinator.testutils.gcp;

import com.google.cloud.NoCredentials;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import java.util.concurrent.ExecutionException;

/** Utility for creating a database in a given Spanner emulator. */
public class SpannerDbCreationHelper {

  public final String endpoint;
  public final String project;
  public final String instanceName;
  public final String dbName;
  private final Iterable<String> createTableStatements;

  public SpannerDbCreationHelper(
      String endpoint,
      String project,
      String instanceName,
      String dbName,
      Iterable<String> createTableStatements) {
    this.endpoint = endpoint;
    this.project = project;
    this.instanceName = instanceName;
    this.dbName = dbName;
    this.createTableStatements = createTableStatements;
  }

  public void create() {
    try {
      SpannerOptions options =
          SpannerOptions.newBuilder()
              .setEmulatorHost(endpoint)
              .setCredentials(NoCredentials.getInstance())
              .setProjectId(project)
              .build();
      Spanner spanner = options.getService();
      spanner
          .getDatabaseAdminClient()
          .createDatabase(instanceName, dbName, createTableStatements)
          .get();
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
