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

load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("@rules_oci//oci:defs.bzl", "oci_image", "oci_load")
load("@rules_pkg//pkg:tar.bzl", "pkg_tar")

# TODO(b/386426300): the load statement below is needed due to a cherry-pick that
# happens during the ks release process. Once that cherry-pick is removed, the pkg_files load can be removed.

load("@rules_pkg//:mappings.bzl", "pkg_files")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "key_generation",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/scp/coordinator/clients/configclient/gcp",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/app/common",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/app/gcp/listener",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/tasks/common",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/tasks/common/keyid",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/tasks/common/keyset",
        "//java/com/google/scp/coordinator/keymanagement/keygeneration/tasks/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/com/google/scp/coordinator/keymanagement/shared/util",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/clients",
        "//java/com/google/scp/shared/clients/configclient",
        "//java/com/google/scp/shared/clients/configclient:gcp",
        "//java/com/google/scp/shared/gcp/util",
        "//java/external:apache_httpclient",
        "//java/external:apache_httpcore",
        "//java/external:gcp_core",
        "//java/external:gcp_kms",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jcommander",
        "//java/external:slf4j",
        "//java/external:slf4j_simple",
    ],
)

java_binary(
    name = "KeyGenerationApp",
    classpath_resources = ["//java/com/google/scp:slf4jconfig_prod"],
    main_class = "com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.KeyGenerationApplication",
    runtime_deps = [
        ":key_generation",
        "//java/external:gcp-http-client-jackson2",
        "//java/external:slf4j_simple",
    ],
)

alias(
    name = "KeyGenerationAppDeploy",
    actual = ":KeyGenerationApp_deploy.jar",
)

pkg_tar(
    name = "key_generation_app_tar",
    srcs = [":KeyGenerationAppDeploy"],
    include_runfiles = True,
    tags = ["manual"],
)

oci_image(
    name = "KeyGenerationServiceRuntimeImage",
    base = "@java_base_21_oci",
    cmd = [
        "KeyGenerationApp_deploy.jar",
        "--multiparty",
    ],
    labels = {
        "tee.launch_policy.allow_cmd_override": "false",
        "tee.launch_policy.log_redirect": "always",
    },
    tags = ["manual"],
    tars = [
        ":key_generation_app_tar",
    ],
)

oci_load(
    name = "KeyGenerationServiceRuntimeContainer",
    image = ":KeyGenerationServiceRuntimeImage",
    repo_tags = [
        "bazel/java/com/google/scp/coordinator/keymanagement/keygeneration/app/gcp:KeyGenerationServiceRuntimeContainer",
    ],
    tags = ["manual"],
)
