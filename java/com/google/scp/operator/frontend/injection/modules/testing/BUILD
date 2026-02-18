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
    name = "fake_frontend_module",
    srcs = ["FakeFrontendModule.java"],
    deps = [
        "//java/com/google/scp/operator/frontend/injection/modules",
        "//java/com/google/scp/operator/frontend/serialization",
        "//java/com/google/scp/operator/frontend/service",
        "//java/com/google/scp/operator/frontend/service/converter",
        "//java/com/google/scp/operator/frontend/service/model",
        "//java/com/google/scp/operator/frontend/tasks",
        "//java/com/google/scp/operator/frontend/tasks/testing",
        "//java/com/google/scp/operator/frontend/tasks/validation",
        "//java/com/google/scp/operator/frontend/testing:fake_request_info_validator",
        "//java/com/google/scp/operator/shared/dao/jobqueue/common",
        "//java/com/google/scp/operator/shared/dao/jobqueue/testing",
        "//java/com/google/scp/operator/shared/injection/modules",
        "//java/com/google/scp/shared/mapper",
        "//java/external:autoservice",
        "//java/external:autoservice_annotations",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_core",
        "//java/external:jackson_databind",
        "//operator/protos/frontend/api/v1:java_proto",
        "//operator/protos/shared/backend:java_proto",
        "//operator/protos/shared/backend/metadatadb:java_proto",
    ],
)
