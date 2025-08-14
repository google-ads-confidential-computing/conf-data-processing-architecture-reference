/**
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

moved {
  from = module.worker.module.base_worker.google_service_account.worker_service_account[0]
  to   = module.worker_service_account.google_service_account.generated_worker_service_account[0]
}

moved {
  from = module.worker.module.base_worker.google_spanner_database_iam_member.worker_jobmetadatadb_iam
  to   = module.worker_service_account.google_spanner_database_iam_member.worker_jobmetadatadb_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_storage_iam
  to   = module.worker_service_account.google_project_iam_member.worker_storage_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_secretmanager_iam
  to   = module.worker_service_account.google_project_iam_member.worker_secretmanager_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_logging_iam
  to   = module.worker_service_account.google_project_iam_member.worker_logging_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_monitoring_iam
  to   = module.worker_service_account.google_project_iam_member.worker_monitoring_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_instance_group_iam
  to   = module.worker_service_account.google_project_iam_member.worker_instance_group_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_workload_user_iam
  to   = module.worker_service_account.google_project_iam_member.worker_workload_user_iam
}

moved {
  from = module.worker.module.base_worker.google_project_iam_member.worker_pubsub_publisher_iam
  to   = module.worker_service_account.google_project_iam_member.worker_pubsub_publisher_iam
}
