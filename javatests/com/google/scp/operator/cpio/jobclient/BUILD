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

load("@rules_java//java:defs.bzl", "java_test")

package(default_visibility = ["//visibility:public"])

java_test(
    name = "JobClientImplTest",
    srcs = ["JobClientImplTest.java"],
    deps = [
        "//java/com/google/scp/operator/cpio/jobclient:aws",
        "//java/com/google/scp/operator/cpio/jobclient:model",
        "//java/com/google/scp/operator/cpio/lifecycleclient:local",
        "//java/com/google/scp/operator/cpio/metricclient",
        "//java/com/google/scp/operator/cpio/metricclient:model",
        "//java/com/google/scp/operator/cpio/notificationclient",
        "//java/com/google/scp/operator/cpio/notificationclient:model",
        "//java/com/google/scp/operator/shared/dao/jobqueue/common",
        "//java/com/google/scp/operator/shared/dao/jobqueue/testing",
        "//java/com/google/scp/operator/shared/dao/metadatadb/common",
        "//java/com/google/scp/operator/shared/dao/metadatadb/testing:fake_metadata_db",
        "//java/com/google/scp/shared/clients/configclient:aws",
        "//java/com/google/scp/shared/proto",
        "//java/external:acai",
        "//java/external:google_truth",
        "//java/external:google_truth8",
        "//java/external:guava",
        "//java/external:guice",
        "//java/external:guice-testlib",
        "//java/external:mockito",
        "//java/external:protobuf_java_util",
        "//operator/protos/shared/backend:java_proto",
        "//operator/protos/shared/backend/jobqueue:java_proto",
        "//operator/protos/shared/backend/metadatadb:java_proto",
    ],
)

java_test(
    name = "JobValidatorCheckRetryLimitTest",
    srcs = ["JobValidatorCheckRetryLimitTest.java"],
    deps = [
        "//java/com/google/scp/operator/cpio/jobclient:aws",
        "//java/com/google/scp/operator/cpio/jobclient:model",
        "//java/external:acai",
        "//java/external:google_truth",
        "//java/external:guice",
        "//operator/protos/shared/backend:java_proto",
    ],
)
