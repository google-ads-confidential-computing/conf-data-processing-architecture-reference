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
    name = "util",
    srcs = [
        "CloudFunctionHandler.java",
        "CloudFunctionRequestHandler.java",
        "CloudFunctionRequestHandlerBase.java",
        "CloudFunctionServiceBase.java",
        "CloudFunctionUtil.java",
        "GcpHttpInterceptorUtil.java",
    ],
    deps = [
        "//java/com/google/scp/shared/api/exception",
        "//java/com/google/scp/shared/api/model",
        "//java/com/google/scp/shared/api/util",
        "//java/com/google/scp/shared/mapper",
        "//java/external:apache_httpclient",
        "//java/external:apache_httpcore",
        "//java/external:apache_httpcore5",
        "//java/external:gcp_cloud_function",
        "//java/external:google_auth_library_oauth2_http",
        "//java/external:guava",
        "//java/external:jackson_core",
        "//java/external:jackson_databind",
        "//java/external:protobuf_java",
        "//java/external:protobuf_java_util",
        "//java/external:slf4j",
        "//shared/protos/api/v1:java_proto",
    ],
)

java_library(
    name = "cloud_event_util",
    srcs = [
        "CloudEventFunctionHandlerBase.java",
    ],
    deps = [
        "//java/external:gcp_cloud_function",
        "//java/external:io_cloudevents_api",
        "//java/external:protobuf_java",
        "//java/external:slf4j",
        "//java/external:slf4j_simple",
    ],
)

java_library(
    name = "json_util",
    srcs = ["JsonHelper.java"],
    deps = [
        "//java/external:gcp_cloudevent_types",
        "//java/external:io_cloudevents_api",
        "//java/external:jackson_databind",
        "//java/external:protobuf_java",
        "//java/external:protobuf_java_util",
    ],
)
