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

package(default_visibility = ["//visibility:public"])

java_library(
    name = "worker_util",
    srcs = [
        "Annotations.java",
        "ErrorSummaryAggregator.java",
        "JobProcessor.java",
        "ReportDecrypter.java",
    ],
    deps = [
        "//java/com/google/scp/operator/cpio/jobclient:model",
        "//java/com/google/scp/operator/shared/model",
        "//java/com/google/scp/operator/worker/decryption",
        "//java/com/google/scp/operator/worker/model",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:javax_inject",
        "//java/external:slf4j",
        "//operator/protos/shared/backend:java_proto",
    ],
)

java_library(
    name = "worker",
    srcs = [
        "SimpleProcessor.java",
        "SimpleWorker.java",
        "WorkerModule.java",
        "WorkerPullWorkService.java",
    ],
    deps = [
        ":worker_util",
        "//java/com/google/scp/operator/cpio/blobstorageclient",
        "//java/com/google/scp/operator/cpio/blobstorageclient:model",
        "//java/com/google/scp/operator/cpio/jobclient",
        "//java/com/google/scp/operator/cpio/jobclient:model",
        "//java/com/google/scp/operator/cpio/metricclient",
        "//java/com/google/scp/operator/cpio/metricclient:model",
        "//java/com/google/scp/operator/shared/model",
        "//java/com/google/scp/operator/worker/logger",
        "//java/com/google/scp/operator/worker/model",
        "//java/com/google/scp/operator/worker/perf",
        "//java/com/google/scp/operator/worker/reader",
        "//java/com/google/scp/shared/clients/configclient",
        "//java/com/google/scp/shared/clients/configclient:model",
        "//java/com/google/scp/shared/enums",
        "//java/com/google/scp/shared/proto",
        "//java/external:autovalue",
        "//java/external:autovalue_annotations",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_annotations",
        "//java/external:jackson_databind",
        "//java/external:javax_inject",
        "//java/external:slf4j",
        "//operator/protos/shared/backend:java_proto",
    ],
)

java_library(
    name = "worker_runner",
    srcs = [
        "SimpleWorkerArgs.java",
        "SimpleWorkerModule.java",
        "SimpleWorkerRunner.java",
    ],
    deps = [
        ":worker",
        ":worker_util",
        "//java/com/google/scp/operator/cpio/blobstorageclient",
        "//java/com/google/scp/operator/cpio/blobstorageclient:gcp",
        "//java/com/google/scp/operator/cpio/configclient:gcp",
        "//java/com/google/scp/operator/cpio/configclient:local",
        "//java/com/google/scp/operator/cpio/cryptoclient",
        "//java/com/google/scp/operator/cpio/cryptoclient:gcp",
        "//java/com/google/scp/operator/cpio/jobclient",
        "//java/com/google/scp/operator/cpio/jobclient:gcp",
        "//java/com/google/scp/operator/cpio/lifecycleclient:local",
        "//java/com/google/scp/operator/cpio/metricclient",
        "//java/com/google/scp/operator/cpio/metricclient:gcp",
        "//java/com/google/scp/operator/worker/decryption",
        "//java/com/google/scp/operator/worker/decryption/hybrid",
        "//java/com/google/scp/operator/worker/logger/localtocloud",
        "//java/com/google/scp/operator/worker/model/serdes",
        "//java/com/google/scp/operator/worker/model/serdes/proto:report_serdes",
        "//java/com/google/scp/operator/worker/perf",
        "//java/com/google/scp/operator/worker/perf/exporter:cloud_stopwatch_exporter",
        "//java/com/google/scp/operator/worker/perf/exporter:no_op_exporter",
        "//java/com/google/scp/operator/worker/reader",
        "//java/com/google/scp/operator/worker/reader/avro",
        "//java/com/google/scp/operator/worker/selector",
        "//java/com/google/scp/shared/clients/configclient",
        "//java/com/google/scp/shared/clients/configclient:gcp",
        "//java/com/google/scp/shared/mapper",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jackson_databind",
        "//java/external:javax_inject",
        "//java/external:jcommander",
    ],
)

java_binary(
    name = "WorkerRunner",
    classpath_resources = ["//java/com/google/scp:slf4jconfig_prod"],
    main_class = "com.google.scp.operator.worker.SimpleWorkerRunner",
    runtime_deps = [
        ":worker_runner",
        "//java/external:commons_logging",
        "//java/external:slf4j_simple",
    ],
)

# The uber-JAR for the aggregation worker.
alias(
    name = "WorkerRunnerDeploy",
    actual = ":WorkerRunner_deploy.jar",
)
