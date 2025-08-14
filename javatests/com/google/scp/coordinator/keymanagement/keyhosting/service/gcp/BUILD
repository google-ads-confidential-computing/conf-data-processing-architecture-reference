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

load("@rules_java//java:defs.bzl", "java_test")

package(default_visibility = ["//visibility:public"])

java_test(
    name = "PublicKeyHostingIntegrationTest",
    srcs = [
        "PublicKeyHostingIntegrationTest.java",
    ],
    deps = [
        "//coordinator/protos/keymanagement/keyhosting/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/com/google/scp/shared/mapper",
        "//java/external:acai",
        "//java/external:gcp_cloud_function",
        "//java/external:gcp_spanner",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_core",
        "//java/external:jackson_databind",
        "//java/external:protobuf_java_util",
        "//java/external:resilience4j_core",
        "//java/external:resilience4j_retry",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp:annotations",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp:spanner_key_db_test_util",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp/cloudfunction",
        "//javatests/com/google/scp/coordinator/testutils/gcp:gcp_multi_coordinator_test_env_module",
        "//javatests/com/google/scp/shared/testutils/common",
        "//javatests/com/google/scp/shared/testutils/gcp",
        "//shared/protos/api/v1:java_proto",
    ],
)

java_test(
    name = "PrivateKeyServiceIntegrationTest",
    srcs = [
        "PrivateKeyServiceIntegrationTest.java",
    ],
    deps = [
        "//coordinator/protos/keymanagement/keyhosting/api/v1:java_proto",
        "//coordinator/protos/keymanagement/keyhosting/api/v1:proto",
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/com/google/scp/shared/api/util",
        "//java/com/google/scp/shared/mapper",
        "//java/external:acai",
        "//java/external:gcp_cloud_function",
        "//java/external:gcp_spanner",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_core",
        "//java/external:jackson_databind",
        "//java/external:protobuf_java_util",
        "//java/external:resilience4j_core",
        "//java/external:resilience4j_retry",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp:annotations",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp:spanner_key_db_test_util",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp/cloudfunction",
        "//javatests/com/google/scp/coordinator/testutils/gcp:gcp_multi_coordinator_test_env_module",
        "//javatests/com/google/scp/shared/testutils/common",
        "//javatests/com/google/scp/shared/testutils/gcp",
        "//shared/protos/api/v1:java_proto",
    ],
)

java_test(
    name = "PublicKeyServiceTest",
    srcs = [
        "PublicKeyServiceTest.java",
    ],
    runtime_deps = [
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp:PublicKeyService_deploy.jar",
    ],
    deps = [
        "//coordinator/protos/keymanagement/keyhosting/api/v1:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/external:acai",
        "//java/external:apache_httpclient",
        "//java/external:apache_httpcore",
        "//java/external:gcp_function_invoker",
        "//java/external:gcp_spanner",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:protobuf_java_util",
        "//java/external:slf4j",
        "//java/external:testcontainers",
        "//javatests/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp:annotations",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp:spanner_key_db_test_util",
        "//javatests/com/google/scp/shared/testutils/gcp",
    ],
)

java_test(
    name = "PrivateKeyServiceTest",
    srcs = [
        "PrivateKeyServiceTest.java",
    ],
    runtime_deps = [
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/service/gcp:PrivateKeyService_deploy.jar",
    ],
    deps = [
        "//coordinator/protos/keymanagement/keyhosting/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/shared/api/model",
        "//java/external:acai",
        "//java/external:apache_httpclient",
        "//java/external:apache_httpcore",
        "//java/external:gcp_function_invoker",
        "//java/external:gcp_spanner",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:protobuf_java_util",
        "//java/external:slf4j",
        "//java/external:testcontainers",
        "//javatests/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/shared/testutils/gcp",
    ],
)
