# Copyright 2023 Google LLC
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

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "gcp_multi_coordinator_test_env_module",
    testonly = True,
    srcs = glob(
        ["*.java"],
    ),
    deps = [
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/app/gcp/listener",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/tasks/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/operator/cpio/blobstorageclient",
        "//java/com/google/scp/operator/cpio/blobstorageclient:gcp",
        "//java/com/google/scp/operator/cpio/cryptoclient",
        "//java/com/google/scp/operator/shared/dao/jobqueue/common",
        "//java/com/google/scp/operator/shared/dao/jobqueue/gcp",
        "//java/com/google/scp/operator/shared/dao/metadatadb/common",
        "//java/com/google/scp/operator/shared/dao/metadatadb/gcp",
        "//java/com/google/scp/operator/worker:worker_runner",
        "//java/com/google/scp/operator/worker/model",
        "//java/com/google/scp/operator/worker/testing",
        "//java/com/google/scp/operator/worker/testing:avro_results_file_reader",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/clients/configclient:gcp",
        "//java/com/google/scp/shared/util",
        "//java/external:acai",
        "//java/external:awaitility",
        "//java/external:gcp_api_gax",
        "//java/external:gcp_core",
        "//java/external:gcp_gax_grpc",
        "//java/external:gcp_pubsub",
        "//java/external:gcp_pubsub_v1",
        "//java/external:gcp_spanner",
        "//java/external:gcp_storage",
        "//java/external:google_truth",
        "//java/external:grpc_api",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:javax_inject",
        "//java/external:jcommander",
        "//java/external:protobuf_java",
        "//java/external:protobuf_java_util",
        "//java/external:tink",
        "//javatests/com/google/kms:LocalGcpKmsClient",
        "//javatests/com/google/kms:LocalGcpKmsServer",
        "//javatests/com/google/kms:LocalKmsServerContainer",
        "//javatests/com/google/scp/coordinator/keymanagement/keygeneration/app/gcp/listener:gcp",
        "//javatests/com/google/scp/coordinator/keymanagement/keygeneration/app/gcp/testing",
        "//javatests/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp/testing:KeyHostingHttpFunctionModule",
        "//javatests/com/google/scp/coordinator/keymanagement/keystorage/service/gcp/testing:KeyStorageHttpFunctionModule",
        "//javatests/com/google/scp/coordinator/keymanagement/keystorage/service/gcp/testing:LocalKeyStorageServiceHttpFunction",
        "//javatests/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp:annotations",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp/cloudfunction",
        "//javatests/com/google/scp/e2e/shared:testutils",
        "//javatests/com/google/scp/operator/frontend/service/gcp/testing/v1:FrontendServiceV1CloudFunctionEmulatorContainer",
        "//javatests/com/google/scp/operator/shared/dao/jobqueue/gcp",
        "//javatests/com/google/scp/operator/shared/dao/metadatadb/gcp",
        "//javatests/com/google/scp/shared/gcp:constants",
        "//javatests/com/google/scp/shared/testutils/gcp",
        "//operator/protos/shared/backend:java_proto",
        "//operator/protos/shared/backend/metadatadb:java_proto",
    ],
)
