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

load("@rules_java//java:defs.bzl", "java_test")

package(default_visibility = ["//visibility:public"])

java_test(
    name = "CreateKeyApiGatewayHandlerTest",
    srcs = [
        "CreateKeyApiGatewayHandlerTest.java",
    ],
    deps = [
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/crypto/tink/integration/awskmsv2",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/service/aws:CreateKeyApiGatewayHandler",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/service/common",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/tasks/aws",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/tasks/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/testing",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/com/google/scp/shared/crypto/tink",
        "//java/external:acai",
        "//java/external:aws_core",
        "//java/external:aws_kms",
        "//java/external:aws_lambda_java_core",
        "//java/external:aws_lambda_java_events",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:mockito",
        "//java/external:tink",
        "//java/external:tink-gcpkms",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
    ],
)

java_test(
    name = "CreateKeyApiGatewayHandlerIntegrationTest",
    size = "large",
    srcs = [
        "CreateKeyApiGatewayHandlerIntegrationTest.java",
    ],
    flaky = True,
    deps = [
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/crypto/tink/integration/awskmsv2",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/app/common",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/tasks/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/aws",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/com/google/scp/coordinator/keymanagement/shared/util",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/com/google/scp/shared/crypto/tink",
        "//java/external:acai",
        "//java/external:apache_httpclient",
        "//java/external:aws_core",
        "//java/external:aws_kms",
        "//java/external:aws_lambda_java_core",
        "//java/external:aws_lambda_java_events",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:localstack_containers",
        "//java/external:mockito",
        "//java/external:protobuf_java",
        "//java/external:tink",
        "//java/external:tink-gcpkms",
        "//javatests/com/google/scp/coordinator/keymanagement/keystorage/service/aws/testing",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/aws:annotations",
        "//javatests/com/google/scp/shared/testutils/aws",
    ],
)
