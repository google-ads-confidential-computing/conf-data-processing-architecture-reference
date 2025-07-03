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
    name = "testutils",
    srcs =
        ["MultiCoordinatorTestEnv.java"],
    deps = [
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/app/common",
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/common",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/tasks/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/aws",
        "//java/com/google/scp/operator/cpio/configclient",
        "//java/com/google/scp/operator/cpio/configclient:aws",
        "//java/com/google/scp/operator/cpio/cryptoclient:aws",
        "//java/com/google/scp/shared/api/util",
        "//java/com/google/scp/shared/aws/credsprovider",
        "//java/com/google/scp/shared/clients/configclient:aws",
        "//java/external:acai",
        "//java/external:apache_httpclient",
        "//java/external:aws_dynamodb",
        "//java/external:aws_dynamodb_enhanced",
        "//java/external:aws_kms",
        "//java/external:aws_lambda_v2",
        "//java/external:aws_regions",
        "//java/external:aws_s3",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:javax_inject",
        "//java/external:localstack_containers",
        "//java/external:mockito",
        "//java/external:testcontainers",
        "//java/external:tink",
        "//javatests/com/google/scp/coordinator/keymanagement/keygeneration/app/aws/testing",
        "//javatests/com/google/scp/coordinator/keymanagement/keyhosting/service/aws/testing",
        "//javatests/com/google/scp/coordinator/keymanagement/keystorage/service/aws/testing",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/aws",
        "//javatests/com/google/scp/shared/testutils/aws",
        "//javatests/com/google/scp/shared/testutils/common",
    ],
)

java_library(
    name = "testutils_with_pbs",
    srcs =
        ["MultiCoordinatorWithPbsTestEnv.java"],
    tags = ["manual"],
    deps = [
        ":testutils",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:javax_inject",
        "//java/external:localstack_containers",
        "//java/external:mockito",
        "//java/external:testcontainers",
        "//javatests/com/google/scp/coordinator/privacy/budgeting/utils/aws:pbs_e2e_test_utils",
        "//javatests/com/google/scp/shared/testutils/aws",
    ],
)
