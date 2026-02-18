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

load("@rules_java//java:java_library.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "configclient",
    srcs = glob([
        "*.java",
        "local/*.java",
    ]),
    deps = [
        ":model",
        "//java/external:autovalue",
        "//java/external:autovalue_annotations",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:javax_inject",
    ],
)

java_library(
    name = "model",
    srcs = glob(["model/*.java"]),
    deps = [
        "//java/external:autovalue",
        "//java/external:autovalue_annotations",
    ],
)

java_library(
    name = "gcp",
    srcs = glob([
        "*.java",
        "gcp/*.java",
        "local/*.java",
        "model/*.java",
    ]),
    deps = [
        ":model",
        "//java/com/google/scp/shared/clients/configclient",
        "//java/com/google/scp/shared/mapper",
        "//java/external:apache_httpclient",
        "//java/external:apache_httpclient5",
        "//java/external:apache_httpcore",
        "//java/external:apache_httpcore5",
        "//java/external:autovalue",
        "//java/external:autovalue_annotations",
        "//java/external:findbugs",
        "//java/external:gcp_api_gax",
        "//java/external:gcp_secretmanager",
        "//java/external:gcp_secretmanager_proto",
        "//java/external:google_api_client",
        "//java/external:google_auth_library_oauth2_http",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_annotations",
        "//java/external:jackson_core",
        "//java/external:jackson_databind",
        "//java/external:javax_inject",
        "//java/external:resilience4j_core",
        "//java/external:resilience4j_retry",
        "//java/external:slf4j",
    ],
)
