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

load("@rules_java//java:defs.bzl", "java_library", "java_test")

package(default_visibility = ["//visibility:public"])

java_test(
    name = "SpannerKeyDbTest",
    srcs = ["SpannerKeyDbTest.java"],
    deps = [
        ":gcp",
        "//coordinator/protos/keymanagement/shared/api/v1:proto",
        "//coordinator/protos/keymanagement/shared/backend:java_proto",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/com/google/scp/coordinator/keymanagement/shared/model",
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/com/google/scp/shared/mapper",
        "//java/external:acai",
        "//java/external:awaitility",
        "//java/external:gcp_core",
        "//java/external:gcp_spanner",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_core",
        "//java/external:jackson_databind",
        "//java/external:slf4j",
        "//java/external:slf4j_simple",
        "//java/external:testcontainers",
        "//javatests/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils",
        "//javatests/com/google/scp/coordinator/keymanagement/testutils/gcp:spanner_key_db_test_util",
    ],
)

java_library(
    name = "gcp",
    testonly = True,
    srcs = ["SpannerKeyDbTestModule.java"],
    deps = [
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/common",
        "//java/com/google/scp/coordinator/keymanagement/shared/dao/gcp",
        "//java/external:acai",
        "//java/external:awaitility",
        "//java/external:gcp_core",
        "//java/external:gcp_spanner",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:slf4j",
        "//java/external:slf4j_simple",
        "//java/external:testcontainers",
        "//javatests/com/google/scp/shared/gcp:constants",
        "//javatests/com/google/scp/shared/testutils/gcp",
    ],
)
