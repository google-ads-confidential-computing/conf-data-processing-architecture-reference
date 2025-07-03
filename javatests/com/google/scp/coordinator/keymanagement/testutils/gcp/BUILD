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

# Separate target for injection tokens to prevent cyclical dependencies.
java_library(
    name = "annotations",
    srcs = [
        "Annotations.java",
    ],
    deps = [
        "//java/external:guice",
    ],
)

# Separate target for injection tokens to prevent cyclical dependencies.
java_library(
    name = "spanner_key_db_test_util",
    srcs = [
        "SpannerKeyDbTestUtil.java",
    ],
    deps = [
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/shared/api/exception",
        "//java/external:guava",
        "//javatests/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
    ],
)

java_library(
    name = "GcpKeyManagementIntegrationTestEnv",
    srcs = glob(["GcpKeyManagementIntegrationTestEnv.java"]),
    deps = [
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/service/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/external:acai",
        "//java/external:gcp_core",
        "//java/external:gcp_spanner",
        "//java/external:guava",
        "//java/external:guice",
        "//javatests/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp/cloudfunction",
    ],
)

java_library(
    name = "GcpKeyGenerationTestEnv",
    srcs = glob(["GcpKeyGenerationTestEnv.java"]),
    deps = [
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/app/common",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/app/gcp:key_generation",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/tasks/common",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/tasks/gcp",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/tasks/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/shared/util",
        "//java/external:gcp_api_gax",
        "//java/external:gcp_core",
        "//java/external:gcp_gax_grpc",
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
        "//javatests/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp:annotations",
        "//javatests/com/google/scp/e2e/shared:testutils",
        "//javatests/com/google/scp/shared/gcp:constants",
        "//javatests/com/google/scp/shared/testutils/gcp",
        "//operator/protos/shared/backend:java_proto",
    ],
)
