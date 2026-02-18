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

load("@rules_java//java:java_binary.bzl", "java_binary")
load("@rules_java//java:java_library.bzl", "java_library")
load("@rules_oci//oci:defs.bzl", "oci_image", "oci_load", "oci_push")
load("@rules_pkg//:pkg.bzl", "pkg_tar", "pkg_zip")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "gcp",
    srcs = glob([
        "*.java",
    ]),
    deps = [
        "//cc/public/cpio/proto/job_service/v1:job_service_java_proto",
        "//java/com/google/scp/operator/frontend/service",
        "//java/com/google/scp/operator/frontend/service/converter",
        "//java/com/google/scp/operator/frontend/service/model",
        "//java/com/google/scp/operator/frontend/tasks",
        "//java/com/google/scp/operator/frontend/tasks/gcp",
        "//java/com/google/scp/operator/shared/dao/jobqueue/gcp",
        "//java/com/google/scp/operator/shared/dao/metadatadb/gcp",
        "//java/com/google/scp/operator/shared/model",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/com/google/scp/shared/api/util",
        "//java/com/google/scp/shared/clients/configclient",
        "//java/com/google/scp/shared/clients/configclient:gcp",
        "//java/com/google/scp/shared/gcp/util",
        "//java/com/google/scp/shared/mapper",
        "//java/external:gcp_cloud_function",
        "//java/external:google_api_client",
        "//java/external:google_http_client_gson",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_core",
        "//java/external:jackson_databind",
        "//java/external:protobuf_java",
        "//java/external:protobuf_java_util",
        "//java/external:slf4j",
        "//java/external:slf4j_simple",
        "//operator/protos/frontend/api/v1:java_proto",
        "//operator/protos/shared/backend:java_proto",
        "//operator/protos/shared/backend/metadatadb:java_proto",
    ],
)

java_binary(
    name = "FrontendServiceHttpCloudFunction",
    runtime_deps = [
        ":gcp",
    ],
)

alias(
    name = "FrontendServiceHttpCloudFunctionDeploy",
    actual = ":FrontendServiceHttpCloudFunction_deploy.jar",
)

pkg_zip(
    name = "FrontendServiceHttpCloudFunctionDeploy_zip",
    srcs = [
        ":FrontendServiceHttpCloudFunction_deploy.jar",
    ],
)

pkg_tar(
    name = "function_invoker_tar",
    srcs = ["//java/external:gcp_function_invoker"],
)

pkg_tar(
    name = "frontend_cloud_function_tar",
    srcs = [":FrontendServiceHttpCloudFunctionDeploy"],
)

oci_image(
    name = "FrontendCloudFunctionRuntimeImage",
    base = "@java_base_21_oci",
    cmd = [
        "./processed_java-function-invoker-1.3.1.jar",
        "--classpath",
        "./FrontendServiceHttpCloudFunction_deploy.jar",
        "--target",
        "com.google.scp.operator.frontend.service.gcp.FrontendServiceHttpFunction",
    ],
    tags = ["manual"],
    tars = [
        ":function_invoker_tar",
        ":frontend_cloud_function_tar",
    ],
)

oci_load(
    name = "FrontendCloudFunctionRuntimeContainer",
    image = ":FrontendCloudFunctionRuntimeImage",
    repo_tags = [
        "bazel/java/com/google/scp/operator/frontend/service/gcp:FrontendCloudFunctionRuntimeContainer",
    ],
    tags = ["manual"],
)

# To build and push this image to a repository in one command:
#   bazel run //java/com/google/scp/operator/frontend/service/gcp:FrontendCloudFunctionRuntimeContainer_push -- \
#     --repository "us-docker.pkg.dev/<project>/<repo>/operator-fe-service" \
#     --tag "some-tag"
oci_push(
    name = "FrontendCloudFunctionRuntimeContainer_push",
    image = ":FrontendCloudFunctionRuntimeImage",
    repository = "replace-me-with-a-real-repo",
    tags = ["manual"],
)
