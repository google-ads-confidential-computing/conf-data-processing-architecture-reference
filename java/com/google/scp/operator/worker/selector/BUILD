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

load("@rules_java//java:java_library.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "selector",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/scp/operator/cpio/blobstorageclient",
        "//java/com/google/scp/operator/cpio/blobstorageclient:gcp",
        "//java/com/google/scp/operator/cpio/configclient:gcp",
        "//java/com/google/scp/operator/cpio/cryptoclient",
        "//java/com/google/scp/operator/cpio/cryptoclient:gcp",
        "//java/com/google/scp/operator/cpio/jobclient",
        "//java/com/google/scp/operator/cpio/jobclient:gcp",
        "//java/com/google/scp/operator/cpio/lifecycleclient:gcp",
        "//java/com/google/scp/operator/cpio/lifecycleclient:local",
        "//java/com/google/scp/operator/cpio/metricclient",
        "//java/com/google/scp/operator/cpio/metricclient:gcp",
        "//java/com/google/scp/operator/cpio/notificationclient:gcp",
        "//java/com/google/scp/operator/cpio/notificationclient:local",
        "//java/com/google/scp/operator/worker/logger",
        "//java/com/google/scp/operator/worker/logger/inmemory",
        "//java/com/google/scp/operator/worker/logger/localtocloud",
        "//java/com/google/scp/operator/worker/perf",
        "//java/com/google/scp/operator/worker/perf/exporter:cloud_stopwatch_exporter",
        "//java/com/google/scp/operator/worker/perf/exporter:no_op_exporter",
        "//java/com/google/scp/shared/clients/configclient",
        "//java/com/google/scp/shared/clients/configclient:gcp",
        "//java/com/google/scp/shared/clients/configclient:model",
        "//java/external:apache_httpcore5",
        "//java/external:guice",
        "//java/external:tink",
    ],
)
