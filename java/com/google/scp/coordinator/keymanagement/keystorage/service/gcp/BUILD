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
    name = "GcpKeyStorageServiceModule",
    srcs = [
        "GcpKeyStorageServiceModule.java",
    ],
    deps = [
        "//java/com/google/scp/coordinator/keymanagement/keystorage/tasks/common",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/tasks/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/external:guice",
        "//java/external:tink",
        "//java/external:tink-gcpkms",
    ],
)

java_library(
    name = "KeyStorageServiceHttpFunction",
    srcs = [
        "CreateKeyRequestHandler.java",
        "KeyStorageServiceHttpFunction.java",
        "KeyStorageServiceHttpFunctionBase.java",
    ],
    runtime_deps = [
        "//java/external:gcp-http-client-jackson2",
        "//java/external:slf4j",
        "//java/external:slf4j_simple",
    ],
    deps = [
        ":GcpKeyStorageServiceModule",
        "//coordinator/protos/keymanagement/keystorage/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/api/v1:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/keystorage/service/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/com/google/scp/shared/api/util",
        "//java/com/google/scp/shared/gcp/util",
        "//java/com/google/scp/shared/mapper",
        "//java/external:gcp_cloud_function",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_core",
        "//java/external:jackson_databind",
        "//java/external:protobuf_java_util",
    ],
)

java_binary(
    name = "KeyStorageServiceHttpCloudFunction",
    runtime_deps = [
        ":KeyStorageServiceHttpFunction",
    ],
)

alias(
    name = "KeyStorageServiceHttpCloudFunctionDeploy",
    actual = ":KeyStorageServiceHttpCloudFunction_deploy.jar",
)

pkg_zip(
    name = "KeyStorageServiceHttpCloudFunctionDeploy_zip",
    srcs = [
        ":KeyStorageServiceHttpCloudFunction_deploy.jar",
    ],
)

pkg_tar(
    name = "function_invoker_tar",
    srcs = ["//java/external:gcp_function_invoker"],
)

pkg_tar(
    name = "key_storage_service_cloud_function_tar",
    srcs = [":KeyStorageServiceHttpCloudFunctionDeploy"],
)

container_image(
    name = "KeyStorageServiceRuntimeContainer",
    base = "@java_base_21//image",
    cmd = [
        "./java-function-invoker-1.3.1.jar",
        "--classpath",
        "./KeyStorageServiceHttpCloudFunction_deploy.jar",
        "--target",
        "com.google.scp.coordinator.keymanagement.keystorage.service.gcp.KeyStorageServiceHttpFunction",
    ],
    files = [],
    tags = ["manual"],
    tars = [
        ":function_invoker_tar",
        ":key_storage_service_cloud_function_tar",
    ],
)
