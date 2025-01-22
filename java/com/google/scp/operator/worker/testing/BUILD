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

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "testing",
    testonly = True,
    srcs = glob(
        ["*.java"],
        exclude = [
            # TODO: consolidate below targets into glob.
            "NoopJobProcessor.java",
            "FakeRecordReaderFactory.java",
            "FakeRecordDecrypter.java",
            "FakeReportGenerator.java",
            "AvroResultsFileReader.java",
            "LocalSimpleWorkerRunner.java",
        ],
    ),
    deps = [
        "//java/com/google/scp/operator/worker",
        "//java/com/google/scp/operator/worker:worker_runner",
        "//java/external:guava",
        "//java/external:guice",
    ],
)

java_library(
    name = "noop_job_processor",
    testonly = True,
    srcs = ["NoopJobProcessor.java"],
    deps = [
        "//java/com/google/scp/operator/cpio/jobclient:model",
        "//java/com/google/scp/operator/worker:worker_util",
    ],
)

java_library(
    name = "fake_record_reader_factory",
    testonly = True,
    srcs = ["FakeRecordReaderFactory.java"],
    deps = [
        "//java/com/google/scp/operator/cpio/blobstorageclient:model",
        "//java/com/google/scp/operator/worker/model",
        "//java/com/google/scp/operator/worker/reader",
        "//java/external:guava",
    ],
)

java_library(
    name = "fake_record_decrypter",
    testonly = True,
    srcs = ["FakeRecordDecrypter.java"],
    deps = [
        ":fake_report_generator",
        "//java/com/google/scp/operator/worker/decryption",
        "//java/com/google/scp/operator/worker/model",
        "//java/external:guava",
    ],
)

java_library(
    name = "fake_report_generator",
    testonly = True,
    srcs = ["FakeReportGenerator.java"],
    deps = [
        "//java/com/google/scp/operator/worker/model",
        "//java/com/google/scp/privacy/budgeting/model:privacy_budgeting_model",
        "//java/external:guava",
    ],
)

java_library(
    name = "avro_results_file_reader",
    srcs = ["AvroResultsFileReader.java"],
    deps = [
        "//java/com/google/scp/operator/worker/model",
        "//java/com/google/scp/protocol/avro:avro_results_schema_supplier",
        "//java/external:avro",
        "//java/external:guava",
        "//java/external:javax_inject",
    ],
)

java_library(
    name = "local_aggregation_worker_runner",
    srcs = ["LocalSimpleWorkerRunner.java"],
    deps = [
        "//java/com/google/scp/operator/worker",
        "//java/com/google/scp/operator/worker:worker_runner",
        "//java/com/google/scp/operator/worker/logger",
        "//java/com/google/scp/operator/worker/logger/inmemory",
        "//java/com/google/scp/operator/worker/model",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:jcommander",
        "//java/external:tink",
    ],
)
