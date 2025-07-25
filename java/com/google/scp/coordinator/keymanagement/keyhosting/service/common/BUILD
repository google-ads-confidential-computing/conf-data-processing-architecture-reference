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

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "common",
    srcs = glob(
        ["*.java"],
        exclude = [
            "EncryptionKeyServiceModule.java",
            "PublicKeyServiceModule.java",
        ],
    ),
    deps = [
        "//coordinator/protos/keymanagement/keyhosting/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/shared/converter",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/com/google/scp/coordinator/keymanagement/shared/util",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/com/google/scp/shared/api/util",
        "//java/external:autovalue",
        "//java/external:autovalue_annotations",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:slf4j",
    ],
)

java_library(
    name = "public_key_service",
    srcs = ["PublicKeyServiceModule.java"],
    deps = [
        "//coordinator/protos/keymanagement/keyhosting/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/tasks",
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/tasks/v1",
        "//java/com/google/scp/coordinator/keymanagement/shared/serverless/common",
        "//java/external:autovalue",
        "//java/external:autovalue_annotations",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:slf4j",
    ],
)

java_library(
    name = "encryption_key_service",
    srcs = ["EncryptionKeyServiceModule.java"],
    deps = [
        "//coordinator/protos/keymanagement/keyhosting/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/api/v1:java_proto",
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/tasks",
        "//java/com/google/scp/coordinator/keymanagement/keyhosting/tasks/v1",
        "//java/com/google/scp/coordinator/keymanagement/shared/serverless/common",
        "//java/external:autovalue",
        "//java/external:autovalue_annotations",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:slf4j",
    ],
)
