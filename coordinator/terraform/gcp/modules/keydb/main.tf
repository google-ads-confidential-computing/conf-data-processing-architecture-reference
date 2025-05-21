# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google-beta"
      version = ">= 6.4.0"
    }
  }
}

resource "google_spanner_instance" "keydb_instance" {
  name             = "${var.environment}-keydbinstance"
  display_name     = "${var.environment}-keydbinstance"
  config           = var.spanner_instance_config
  processing_units = var.spanner_processing_units
}

resource "google_spanner_database" "keydb" {
  project                  = var.project_id
  instance                 = google_spanner_instance.keydb_instance.name
  name                     = "${var.environment}-keydb"
  version_retention_period = "7d"
  ddl = [
    <<-EOT
    CREATE TABLE KeySets (
      KeyId STRING(50) NOT NULL,
      PublicKey STRING(1000) NOT NULL,
      PrivateKey STRING(1000) NOT NULL,
      PublicKeyMaterial STRING(500) NOT NULL,
      KeySplitData JSON,
      KeyType STRING(500) NOT NULL,
      KeyEncryptionKeyUri STRING(1000) NOT NULL,
      ExpiryTime TIMESTAMP NOT NULL,
      TtlTime TIMESTAMP NOT NULL,
      CreatedAt TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
      UpdatedAt TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true))
    PRIMARY KEY (KeyId),
    ROW DELETION POLICY (OLDER_THAN(TtlTime, INTERVAL 0 DAY))
  EOT
    , "CREATE INDEX KeySetsByExpiryTime ON KeySets(ExpiryTime)"
    , "ALTER TABLE KeySets ADD COLUMN ActivationTime TIMESTAMP"
    , "CREATE INDEX KeySetsByExpiryActivationDesc ON KeySets(ExpiryTime DESC, ActivationTime DESC)"
    , "DROP INDEX KeySetsByExpiryTime"
    , "ALTER TABLE KeySets ADD COLUMN SetName String(50)"
    , "CREATE INDEX KeySetsByNameExpiryActivationDesc ON KeySets(SetName, ExpiryTime DESC, ActivationTime DESC)"
    , "DROP INDEX KeySetsByExpiryActivationDesc"
    , "DROP INDEX KeySetsByNameExpiryActivationDesc"
    , "ALTER TABLE KeySets ALTER COLUMN ExpiryTime TIMESTAMP"
    , "ALTER TABLE KeySets ALTER COLUMN TtlTime TIMESTAMP"
    , "CREATE INDEX KeySetsByNameExpiryActivationDesc ON KeySets(SetName, ExpiryTime DESC, ActivationTime DESC)"
  ]

  deletion_protection = true
}

resource "google_spanner_instance_config" "example" {
  count = var.custom_configuration_name == null ? 0 : 1

  name         = var.custom_configuration_name
  display_name = var.custom_configuration_display_name
  base_config  = var.custom_configuration_base_config
  replicas {
    location                = var.custom_configuration_read_replica_location
    type                    = "READ_ONLY"
    default_leader_location = false
  }
}

resource "google_spanner_backup_schedule" "keydb_backup_schedule_full_backup" {
  instance = google_spanner_instance.keydb_instance.name

  database = google_spanner_database.keydb.name

  name = "${var.environment}-keydb-backup-schedule-full-backup"

  retention_duration = "7776000s" // 90 days

  spec {
    cron_spec {
      text = "0 0 * * *" // once a day at 12:00 midnight in UTC.
    }
  }
  // The schedule creates only full backups.
  full_backup_spec {}
}
