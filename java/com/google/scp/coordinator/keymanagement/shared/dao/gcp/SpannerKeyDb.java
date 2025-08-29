/*
 * Copyright 2024 Google LLC
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

package com.google.scp.coordinator.keymanagement.shared.dao.gcp;

import static com.google.cloud.Timestamp.ofTimeSecondsAndNanos;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.DATASTORE_ERROR;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.MISSING_KEY;
import static com.google.scp.coordinator.keymanagement.shared.model.KeyManagementErrorReason.UNSUPPORTED_OPERATION;
import static com.google.scp.shared.api.model.Code.ALREADY_EXISTS;
import static com.google.scp.shared.api.model.Code.INTERNAL;
import static com.google.scp.shared.api.model.Code.NOT_FOUND;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.ErrorCode;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.TimestampBound;
import com.google.cloud.spanner.Value;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.scp.coordinator.keymanagement.shared.dao.common.Annotations.KeyDbClient;
import com.google.scp.coordinator.keymanagement.shared.dao.common.KeyDb;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.EncryptionKeyProto.EncryptionKey;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitData;
import com.google.scp.coordinator.protos.keymanagement.shared.backend.KeySplitDataProto.KeySplitDataList;
import com.google.scp.shared.api.exception.ServiceException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** KeyDb implementation for GCP Spanner. */
public final class SpannerKeyDb implements KeyDb {

  private static final String KEY_ID_COLUMN = "KeyId";
  private static final String SET_NAME_COLUMN = "SetName";
  private static final String PUBLIC_KEY_COLUMN = "PublicKey";
  private static final String PRIVATE_KEY_COLUMN = "PrivateKey";
  private static final String PUBLIC_KEY_MATERIAL_COLUMN = "PublicKeyMaterial";
  private static final String KEY_SPLIT_DATA_COLUMN = "KeySplitData";
  private static final String KEY_TYPE = "KeyType";
  private static final String KEY_ENCRYPTION_KEY_URI = "KeyEncryptionKeyUri";
  private static final String EXPIRY_TIME_COLUMN = "ExpiryTime";
  private static final String TTL_TIME_COLUMN = "TtlTime";
  private static final String ACTIVATION_TIME_COLUMN = "ActivationTime";
  private static final String CREATED_AT_COLUMN = "CreatedAt";
  private static final String UPDATED_AT_COLUMN = "UpdatedAt";
  private static final String MIGRATION_PRIVATE_KEY_COLUMN = "MigrationPrivateKey";
  private static final String MIGRATION_KEY_SPLIT_DATA_COLUMN = "MigrationKeySplitData";
  private static final String MIGRATION_KEY_ENCRYPTION_KEY_URI_COLUMN =
      "MigrationKeyEncryptionKeyUri";
  private static final String TABLE_NAME = "KeySets";
  private static final String NATURAL_ORDERING =
      EXPIRY_TIME_COLUMN + " DESC, " + ACTIVATION_TIME_COLUMN + " DESC, " + KEY_ID_COLUMN + " DESC";
  private static final JsonFormat.Printer JSON_PRINTER = JsonFormat.printer();
  private static final JsonFormat.Parser JSON_PARSER = JsonFormat.parser();
  private static final Logger LOGGER = LoggerFactory.getLogger(SpannerKeyDb.class);

  private final DatabaseClient dbClient;
  private final TimestampBound stalenessBound;

  @Inject
  public SpannerKeyDb(@KeyDbClient DatabaseClient dbClient, SpannerKeyDbConfig dbConfig) {
    this.dbClient = dbClient;
    this.stalenessBound =
        dbConfig.readStalenessSeconds() > 0
            ? TimestampBound.ofExactStaleness(dbConfig.readStalenessSeconds(), TimeUnit.SECONDS)
            : TimestampBound.strong();
  }

  @Override
  public ImmutableList<EncryptionKey> getActiveKeys(
      String setName, int keyLimit, Instant start, Instant end) throws ServiceException {
    Statement statement =
        Statement.newBuilder(
                "SELECT * FROM "
                    + TABLE_NAME
                    + " WHERE ("
                    + EXPIRY_TIME_COLUMN
                    + " IS NULL OR "
                    + EXPIRY_TIME_COLUMN
                    + " > @startParam) AND ("
                    + ACTIVATION_TIME_COLUMN
                    + " is NULL OR "
                    + ACTIVATION_TIME_COLUMN
                    + " <= @endParam)"
                    // Filter keys with matching set name, if it's the default set, includes keys
                    // with null set name.
                    + " AND (SetName = @setName"
                    + " OR (@setName = @defaultSetName AND SetName IS NULL))"
                    // Ordering implementation should follow {@link
                    // KeyDbUtil.getActiveKeysComparator}
                    + " ORDER BY "
                    + NATURAL_ORDERING
                    + (keyLimit == 0 ? "" : " LIMIT @keyLimitParam"))
            .bind("startParam")
            .to(ofTimeSecondsAndNanos(start.getEpochSecond(), start.getNano()))
            .bind("endParam")
            .to(ofTimeSecondsAndNanos(end.getEpochSecond(), end.getNano()))
            .bind("keyLimitParam")
            .to(keyLimit)
            .bind("defaultSetName")
            .to(KeyDb.DEFAULT_SET_NAME)
            .bind("setName")
            .to(setName)
            .build();
    return retrieveKeys(statement);
  }

  @Override
  public ImmutableList<EncryptionKey> getActiveKeys(String setName, int keyLimit, Instant instant)
      throws ServiceException {
    Statement statement =
        Statement.newBuilder(
                "SELECT * FROM "
                    + TABLE_NAME
                    + " WHERE ("
                    + EXPIRY_TIME_COLUMN
                    + " IS NULL OR "
                    + EXPIRY_TIME_COLUMN
                    + " > @nowParam) AND ("
                    + ACTIVATION_TIME_COLUMN
                    + " is NULL OR "
                    + ACTIVATION_TIME_COLUMN
                    + " <= @nowParam)"
                    // Filter keys with matching set name, if it's the default set, includes keys
                    // with null set name.
                    + " AND (SetName = @setName"
                    + " OR (@setName = @defaultSetName AND SetName IS NULL))"
                    // Ordering implementation should follow {@link
                    // KeyDbUtil.getActiveKeysComparator}
                    + " ORDER BY "
                    + NATURAL_ORDERING
                    + (keyLimit == 0 ? "" : " LIMIT @keyLimitParam"))
            .bind("nowParam")
            .to(ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()))
            .bind("keyLimitParam")
            .to(keyLimit)
            .bind("defaultSetName")
            .to(KeyDb.DEFAULT_SET_NAME)
            .bind("setName")
            .to(setName)
            .build();
    return retrieveKeys(statement);
  }

  private ImmutableList<EncryptionKey> retrieveKeys(Statement statement) throws ServiceException {
    ImmutableList.Builder<EncryptionKey> keysBuilder = ImmutableList.builder();
    try (var readContext = dbClient.singleUse(stalenessBound)) {
      var resultSet = readContext.executeQuery(statement);
      while (resultSet.next()) {
        keysBuilder.add(buildEncryptionKey(resultSet));
      }
    }
    return keysBuilder.build();
  }

  @Override
  public ImmutableList<EncryptionKey> getAllKeys() throws ServiceException {
    throw new ServiceException(
        NOT_FOUND, UNSUPPORTED_OPERATION.name(), "Unsupported operation in Spanner");
  }

  @Override
  public ImmutableList<EncryptionKey> listAllKeysForSetName(String setName)
      throws ServiceException {
    Statement statement =
        Statement.newBuilder(
                "SELECT * FROM "
                    + TABLE_NAME
                    + " WHERE "
                    // Filter keys with matching set name, if it's the default set, includes keys
                    // with null set name.
                    + " SetName = @setName OR (@setName = @defaultSetName AND SetName IS NULL)"
                    + " ORDER BY "
                    + NATURAL_ORDERING)
            .bind("defaultSetName")
            .to(DEFAULT_SET_NAME)
            .bind("setName")
            .to(setName)
            .build();
    return retrieveKeys(statement);
  }

  @Override
  public Stream<EncryptionKey> listRecentKeys(String setName, Duration maxAge)
      throws ServiceException {
    Instant maxCreation = Instant.now().minus(maxAge);
    Statement statement =
        Statement.newBuilder(
                "SELECT * FROM "
                    + TABLE_NAME
                    + " WHERE "
                    + CREATED_AT_COLUMN
                    + " >= @nowParam "
                    // Filter keys with matching set name, if it's the default set, includes keys
                    // with null set name.
                    + " AND (SetName = @setName"
                    + " OR (@setName = @defaultSetName AND SetName IS NULL))"
                    // Add expiry time to improve usage of KeySetsByNameExpiryActivationDesc.
                    // Expiry time can be null for no rotation keys.
                    + " AND ("
                    + EXPIRY_TIME_COLUMN
                    + " >= @nowParam OR "
                    + EXPIRY_TIME_COLUMN
                    + " IS NULL) "
                    + " ORDER BY "
                    + NATURAL_ORDERING)
            .bind("nowParam")
            .to(
                ofTimeSecondsAndNanos(
                    maxCreation.getEpochSecond(), maxCreation.getNano()))
            .bind("defaultSetName")
            .to(KeyDb.DEFAULT_SET_NAME)
            .bind("setName")
            .to(setName)
            .build();
    Stream.Builder<EncryptionKey> keysBuilder = Stream.builder();
    try (var readContext = dbClient.singleUse(stalenessBound)) {
      var resultSet = readContext.executeQuery(statement);
      while (resultSet.next()) {
        keysBuilder.add(buildEncryptionKey(resultSet));
      }
    }
    return keysBuilder.build();
  }

  @Override
  public EncryptionKey getKey(String keyId) throws ServiceException {
    Statement statement =
        Statement.newBuilder("SELECT * FROM " + TABLE_NAME + " WHERE KeyId = @KeyIdParam")
            .bind("KeyIdParam")
            .to(keyId)
            .build();
    ImmutableList.Builder<EncryptionKey> keysBuilder = ImmutableList.builder();
    try (var readContext = dbClient.singleUse(stalenessBound)) {
      var resultSet = readContext.executeQuery(statement);
      while (resultSet.next()) {
        keysBuilder.add(buildEncryptionKey(resultSet));
      }
    }
    ImmutableList<EncryptionKey> keys = keysBuilder.build();
    if (keys.isEmpty()) {
      throw new ServiceException(
          NOT_FOUND, MISSING_KEY.name(), "Unable to find item with keyId " + keyId);
    } else if (keys.size() > 1) {
      throw new ServiceException(
          INTERNAL, DATASTORE_ERROR.name(), "Multiple keys found with keyId " + keyId);
    }
    return keys.getFirst();
  }

  @Override
  public void createKey(EncryptionKey key, boolean overwrite) throws ServiceException {
    List<Mutation> mutations;
    mutations =
        ImmutableList.of(key).stream()
            .map(a -> toMutation(a, overwrite))
            .collect(toImmutableList());
    writeTransaction(mutations);
  }

  @Override
  public void createKeys(ImmutableList<EncryptionKey> keys) throws ServiceException {
    List<Mutation> mutations =
        keys.stream().map(a -> toMutation(a, true)).collect(toImmutableList());
    writeTransaction(mutations);
  }

  /**
   * Updates the stored <u>key material</u> fields for each KeyId to the associated EncryptionKey
   * fields provided. UpdatedAt will be set to the transaction timestamp. No other fields will be
   * modified.
   *
   * <p><b>Warning:</b> <u>Will overwrite any existing stored key material!</u>
   *
   * <p>Use only for the following migration phase:
   *
   * <ul>
   *   <li>Phase 2 (Migrate): Overwrite original key material with migration material.
   * </ul>
   *
   * @param keys The list of encryption keys to update key material for.
   */
  @Override
  public void updateKeyMaterial(ImmutableList<EncryptionKey> keys) throws ServiceException {
    List<Mutation> mutations =
        keys.stream().map(SpannerKeyDb::toKeyMaterialMutation).collect(toImmutableList());
    writeTransaction(mutations);
  }

  /**
   * Updates the stored <u>migration key material</u> fields for each KeyId to the associated
   * EncryptionKey fields provided. UpdatedAt will be set to the transaction timestamp. No other
   * fields will be modified.
   *
   * <p><b>Warning:</b> <u>Will overwrite any existing stored migration key material!</u>
   *
   * <p>Use only for the following migration phases:
   *
   * <ul>
   *   <li>Phase 1 (Generate): Generate and store migration material.
   *   <li>Phase 3 (Cleanup): Clear migration columns.
   * </ul>
   *
   * @param keys The list of encryption keys to update migration material for.
   */
  @Override
  public void updateMigrationKeyMaterial(ImmutableList<EncryptionKey> keys)
      throws ServiceException {
    List<Mutation> mutations =
        keys.stream().map(SpannerKeyDb::toMigrationKeyMaterialMutation).collect(toImmutableList());
    writeTransaction(mutations);
  }

  private void writeTransaction(List<Mutation> mutations) throws ServiceException {
    try {
      dbClient
          .readWriteTransaction()
          .run(
              transaction -> {
                transaction.buffer(mutations);
                return null;
              });
    } catch (SpannerException ex) {
      if (ex.getErrorCode().equals(ErrorCode.ALREADY_EXISTS)) {
        String message = "KeyId already exists in database";
        LOGGER.warn(message);
        throw new ServiceException(ALREADY_EXISTS, DATASTORE_ERROR.name(), message, ex);
      }
      if (ex.getErrorCode().equals(ErrorCode.NOT_FOUND)) {
        String message = "KeyId not found in database";
        LOGGER.warn(message);
        throw new ServiceException(NOT_FOUND, DATASTORE_ERROR.name(), message, ex);
      }
      String message = "Spanner encountered error creating or updating keys";
      LOGGER.error(message, ex);
      throw new ServiceException(INTERNAL, DATASTORE_ERROR.name(), message, ex);
    }
  }

  private static Mutation toMutation(EncryptionKey key, boolean overwrite) {
    Timestamp expireTime =
        key.hasExpirationTime()
            ? Timestamp.ofTimeMicroseconds(
                TimeUnit.MICROSECONDS.convert(key.getExpirationTime(), TimeUnit.MILLISECONDS))
            : null;
    // TTL is saved in seconds, however Spanner requires a timestamp
    Timestamp ttlTime =
        key.hasTtlTime()
            ? ofTimeSecondsAndNanos(
                TimeUnit.SECONDS.convert(key.getTtlTime(), TimeUnit.SECONDS), 0)
            : null;
    Timestamp activationTime =
        Timestamp.ofTimeMicroseconds(
            TimeUnit.MICROSECONDS.convert(key.getActivationTime(), TimeUnit.MILLISECONDS));

    // Wrap keySplitData in a Value object for Spanner
    Value keySplitJsonValue = toValueFromKeySplitData(key.getKeySplitDataList());
    Value migrationKeySplitJsonValue = toValueFromKeySplitData(key.getMigrationKeySplitDataList());

    Mutation.WriteBuilder builder = Mutation.newInsertBuilder(SpannerKeyDb.TABLE_NAME);
    if (overwrite) {
      builder = Mutation.newInsertOrUpdateBuilder(SpannerKeyDb.TABLE_NAME);
    }

    return builder
        .set(KEY_ID_COLUMN)
        .to(key.getKeyId())
        .set(SET_NAME_COLUMN)
        .to(key.getSetName())
        .set(PUBLIC_KEY_COLUMN)
        .to(key.getPublicKey())
        .set(PUBLIC_KEY_MATERIAL_COLUMN)
        .to(key.getPublicKeyMaterial())
        .set(PRIVATE_KEY_COLUMN)
        .to(key.getJsonEncodedKeyset())
        .set(MIGRATION_PRIVATE_KEY_COLUMN)
        .to(key.getMigrationJsonEncodedKeyset())
        .set(KEY_SPLIT_DATA_COLUMN)
        .to(keySplitJsonValue)
        .set(MIGRATION_KEY_SPLIT_DATA_COLUMN)
        .to(migrationKeySplitJsonValue)
        .set(KEY_TYPE)
        .to(key.getKeyType())
        .set(KEY_ENCRYPTION_KEY_URI)
        .to(key.getKeyEncryptionKeyUri())
        .set(MIGRATION_KEY_ENCRYPTION_KEY_URI_COLUMN)
        .to(key.getMigrationKeyEncryptionKeyUri())
        .set(EXPIRY_TIME_COLUMN)
        .to(expireTime)
        .set(TTL_TIME_COLUMN)
        .to(ttlTime)
        .set(ACTIVATION_TIME_COLUMN)
        .to(activationTime)
        .set(CREATED_AT_COLUMN)
        .to(Value.COMMIT_TIMESTAMP)
        .set(UPDATED_AT_COLUMN)
        .to(Value.COMMIT_TIMESTAMP)
        .build();
  }

  /** Creates a mutation that will only update the key material fields of an existing key. */
  private static Mutation toKeyMaterialMutation(EncryptionKey key) {
    Value keySplitJsonValue = toValueFromKeySplitData(key.getKeySplitDataList());
    return Mutation.newUpdateBuilder(SpannerKeyDb.TABLE_NAME)
        .set(KEY_ID_COLUMN)
        .to(key.getKeyId())
        .set(PRIVATE_KEY_COLUMN)
        .to(key.getJsonEncodedKeyset())
        .set(KEY_SPLIT_DATA_COLUMN)
        .to(keySplitJsonValue)
        .set(KEY_ENCRYPTION_KEY_URI)
        .to(key.getKeyEncryptionKeyUri())
        .set(UPDATED_AT_COLUMN)
        .to(Value.COMMIT_TIMESTAMP)
        .build();
  }

  /**
   * Creates a mutation that will only update the migration key material fields of an existing key.
   */
  private static Mutation toMigrationKeyMaterialMutation(EncryptionKey key) {
    Value migrationKeySplitJsonValue = toValueFromKeySplitData(key.getMigrationKeySplitDataList());
    return Mutation.newUpdateBuilder(SpannerKeyDb.TABLE_NAME)
        .set(KEY_ID_COLUMN)
        .to(key.getKeyId())
        .set(MIGRATION_PRIVATE_KEY_COLUMN)
        .to(key.getMigrationJsonEncodedKeyset())
        .set(MIGRATION_KEY_SPLIT_DATA_COLUMN)
        .to(migrationKeySplitJsonValue)
        .set(MIGRATION_KEY_ENCRYPTION_KEY_URI_COLUMN)
        .to(key.getMigrationKeyEncryptionKeyUri())
        .set(UPDATED_AT_COLUMN)
        .to(Value.COMMIT_TIMESTAMP)
        .build();
  }

  /** Serialize a list of {@link KeySplitData} to JSON and wrap it in Value object for Spanner */
  private static Value toValueFromKeySplitData(List<KeySplitData> keySplitData)
      throws RuntimeException {
    try {
      KeySplitDataList proto =
          KeySplitDataList.newBuilder().addAllKeySplitData(keySplitData).build();
      String json = JSON_PRINTER.print(proto);
      return Value.json(json);
    } catch (InvalidProtocolBufferException ex) {
      // TODO: remove after proto migration. Currently cannot throw checked exception in java
      // stream.
      String message = "Spanner encountered keySplitData serialization error";
      LOGGER.error(message, ex);
      throw new RuntimeException(message, ex);
    }
  }

  private static EncryptionKey buildEncryptionKey(ResultSet resultSet) throws ServiceException {
    ImmutableList<KeySplitData> keySplitData = getKeySplitData(KEY_SPLIT_DATA_COLUMN, resultSet);
    ImmutableList<KeySplitData> migrationKeySplitData =
        !resultSet.isNull(MIGRATION_KEY_SPLIT_DATA_COLUMN)
            ? getKeySplitData(MIGRATION_KEY_SPLIT_DATA_COLUMN, resultSet)
            : ImmutableList.of();
    // For backward compatibility with existing keys without set names.
    String setName =
        resultSet.isNull(SET_NAME_COLUMN) ? DEFAULT_SET_NAME : resultSet.getString(SET_NAME_COLUMN);
    EncryptionKey.Builder keyBuilder =
        EncryptionKey.newBuilder()
            .setKeyId(resultSet.getString(KEY_ID_COLUMN))
            .setSetName(setName)
            .setPublicKey(resultSet.getString(PUBLIC_KEY_COLUMN))
            .setPublicKeyMaterial(resultSet.getString(PUBLIC_KEY_MATERIAL_COLUMN))
            .setJsonEncodedKeyset(resultSet.getString(PRIVATE_KEY_COLUMN))
            .addAllKeySplitData(keySplitData)
            .setKeyType(resultSet.getString(KEY_TYPE))
            .setKeyEncryptionKeyUri(resultSet.getString(KEY_ENCRYPTION_KEY_URI))
            .setCreationTime(toEpochMilliSeconds(resultSet.getTimestamp(CREATED_AT_COLUMN)))
            .setActivationTime(toEpochMilliSeconds(resultSet.getTimestamp(ACTIVATION_TIME_COLUMN)));
    if (!resultSet.isNull(TTL_TIME_COLUMN)) {
      keyBuilder.setTtlTime(resultSet.getTimestamp(TTL_TIME_COLUMN).getSeconds());
    }
    if (!resultSet.isNull(EXPIRY_TIME_COLUMN)) {
      keyBuilder.setExpirationTime(toEpochMilliSeconds(resultSet.getTimestamp(EXPIRY_TIME_COLUMN)));
    }
    if (!resultSet.isNull(MIGRATION_PRIVATE_KEY_COLUMN)) {
      keyBuilder.setMigrationJsonEncodedKeyset(resultSet.getString(MIGRATION_PRIVATE_KEY_COLUMN));
    }
    if (!resultSet.isNull(MIGRATION_KEY_SPLIT_DATA_COLUMN)) {
      keyBuilder.addAllMigrationKeySplitData(migrationKeySplitData);
    }
    if (!resultSet.isNull(MIGRATION_KEY_ENCRYPTION_KEY_URI_COLUMN)) {
      keyBuilder.setMigrationKeyEncryptionKeyUri(
          resultSet.getString(MIGRATION_KEY_ENCRYPTION_KEY_URI_COLUMN));
    }
    return keyBuilder.build();
  }

  private static ImmutableList<KeySplitData> getKeySplitData(String columnName, ResultSet resultSet)
      throws ServiceException {
    try {
      String json = resultSet.getJson(columnName);
      KeySplitDataList.Builder builder = KeySplitDataList.newBuilder();
      JSON_PARSER.merge(json, builder);
      return builder.build().getKeySplitDataList().stream().collect(toImmutableList());
    } catch (InvalidProtocolBufferException ex) {
      String message = "Spanner encountered deserialization error with KeySplitData";
      LOGGER.error(message, ex);
      throw new ServiceException(INTERNAL, DATASTORE_ERROR.name(), message, ex);
    }
  }

  private static long toEpochMilliSeconds(Timestamp timestamp) {
    Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    return instant.toEpochMilli();
  }
}
