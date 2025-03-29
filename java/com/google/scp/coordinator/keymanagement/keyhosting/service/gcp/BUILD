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
load("@io_bazel_rules_docker//container:container.bzl", "container_image")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("@rules_pkg//:pkg.bzl", "pkg_tar", "pkg_zip")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "GcpKeyServiceModule",
    srcs = [
        "GcpKeyServiceModule.java",
    ],
    deps = [
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/service/common",
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/tasks",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/external:guice",
    ],
)

pkg_tar(
    name = "function_invoker_tar",
    srcs = ["//java/external:gcp_function_invoker"],
)

# Public Key Service
java_library(
    name = "public_key_service",
    srcs = [
        "PublicKeyService.java",
    ],
    deps = [
        ":GcpKeyServiceModule",
        "//coordinator/protos/keymanagement/keyhosting/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/api/v1:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/service/common:public_key_service",
        "//java/com/google/scp/coordinator/keymanagement/shared/serverless/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/util",
        "//java/external:aws_lambda_java_core",
        "//java/external:aws_lambda_java_events",
        "//java/external:gcp_cloud_function",
        "//java/external:guice",
        "//java/external:protobuf_java_util",
    ],
)

java_binary(
    name = "PublicKeyServiceHttpCloudFunction",
    runtime_deps = [
        ":public_key_service",
    ],
)

alias(
    name = "PublicKeyServiceHttpCloudFunctionDeploy",
    actual = ":PublicKeyServiceHttpCloudFunction_deploy.jar",
)

pkg_zip(
    name = "PublicKeyServiceHttpCloudFunctionDeploy_zip",
    srcs = [
        ":PublicKeyServiceHttpCloudFunction_deploy.jar",
    ],
)

java_binary(
    name = "PublicKeyService",
    main_class = "com.google.cloud.functions.invoker.runner.Invoker",
    runtime_deps = [
        ":public_key_service",
        "//java/external:gcp_function_invoker",
    ],
)

## PKS Cloud Run
pkg_tar(
    name = "public_key_service_cloud_function_tar",
    srcs = [":PublicKeyServiceHttpCloudFunctionDeploy"],
)

container_image(
    name = "PublicKeyServiceRuntimeContainer",
    base = "@java_base_21//image",
    cmd = [
        "./java-function-invoker-1.3.1.jar",
        "--classpath",
        "./PublicKeyServiceHttpCloudFunction_deploy.jar",
        "--target",
        "com.google.scp.coordinator.keymanagement.keyhosting.service.gcp.PublicKeyService",
    ],
    files = [],
    tags = ["manual"],
    tars = [
        ":function_invoker_tar",
        ":public_key_service_cloud_function_tar",
    ],
)

# Encryption Key Service
java_library(
    name = "encryption_key_service",
    srcs = [
        "EncryptionKeyService.java",
    ],
    deps = [
        ":GcpKeyServiceModule",
        "//coordinator/protos/keymanagement/keyhosting/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/api/v1:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/service/common:encryption_key_service",
        "//java/com/google/scp/coordinator/keymanagement/shared/serverless/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/util",
        "//java/external:aws_lambda_java_core",
        "//java/external:aws_lambda_java_events",
        "//java/external:gcp_cloud_function",
        "//java/external:guice",
        "//java/external:protobuf_java_util",
    ],
)

java_binary(
    name = "EncryptionKeyServiceHttpCloudFunction",
    runtime_deps = [
        ":encryption_key_service",
    ],
)

alias(
    name = "EncryptionKeyServiceHttpCloudFunctionDeploy",
    actual = ":EncryptionKeyServiceHttpCloudFunction_deploy.jar",
)

pkg_zip(
    name = "EncryptionKeyServiceHttpCloudFunctionDeploy_zip",
    srcs = [
        ":EncryptionKeyServiceHttpCloudFunction_deploy.jar",
    ],
)

java_binary(
    name = "EncryptionKeyService",
    main_class = "com.google.cloud.functions.invoker.runner.Invoker",
    runtime_deps = [
        ":encryption_key_service",
        "//java/external:gcp_function_invoker",
    ],
)

## EKS Cloud Run
pkg_tar(
    name = "encryption_key_service_cloud_function_tar",
    srcs = [":EncryptionKeyServiceHttpCloudFunctionDeploy"],
)

container_image(
    name = "EncryptionKeyServiceRuntimeContainer",
    base = "@java_base_21//image",
    cmd = [
        "./java-function-invoker-1.3.1.jar",
        "--classpath",
        "./EncryptionKeyServiceHttpCloudFunction_deploy.jar",
        "--target",
        "com.google.scp.coordinator.keymanagement.keyhosting.service.gcp.EncryptionKeyService",
    ],
    files = [],
    tags = ["manual"],
    tars = [
        ":function_invoker_tar",
        ":encryption_key_service_cloud_function_tar",
    ],
)
