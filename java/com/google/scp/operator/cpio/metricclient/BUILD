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
    name = "metricclient",
    srcs = glob([
        "*.java",
        "local/*.java",
    ]),
    deps = [
        ":model",
        "//java/com/google/scp/shared/clients/configclient",
        "//java/com/google/scp/shared/clients/configclient:model",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:javax_inject",
        "//java/external:opentelemetry_exporter_otlp",
        "//java/external:opentelemetry_sdk_metrics",
    ],
)

java_library(
    name = "model",
    srcs = glob(["model/*.java"]),
    deps = [
        "//java/external:autovalue",
        "//java/external:autovalue_annotations",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_annotations",
    ],
)

java_library(
    name = "gcp",
    srcs = glob([
        "*.java",
        "gcp/*.java",
        "local/*.java",
    ]),
    deps = [
        ":model",
        "//java/com/google/scp/operator/cpio/configclient",
        "//java/com/google/scp/operator/cpio/metricclient",
        "//java/com/google/scp/shared/clients/configclient",
        "//java/com/google/scp/shared/clients/configclient:gcp",
        "//java/com/google/scp/shared/clients/configclient:model",
        "//java/external:autovalue",
        "//java/external:autovalue_annotations",
        "//java/external:gcp_api_gax",
        "//java/external:gcp_cloud_monitoring",
        "//java/external:gcp_monitoring_v3",
        "//java/external:google_auth_library_oauth2_http",
        "//java/external:google_cloud_opentelemetry_detector_resources",
        "//java/external:google_common_proto",
        "//java/external:grpc_api",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:javax_inject",
        "//java/external:opentelemetry_api",
        "//java/external:opentelemetry_exporter_otlp",
        "//java/external:opentelemetry_sdk",
        "//java/external:opentelemetry_sdk_common",
        "//java/external:opentelemetry_sdk_extension_autoconfigure",
        "//java/external:opentelemetry_sdk_metrics",
        "//java/external:protobuf_java",
        "//java/external:protobuf_java_util",
    ],
)
