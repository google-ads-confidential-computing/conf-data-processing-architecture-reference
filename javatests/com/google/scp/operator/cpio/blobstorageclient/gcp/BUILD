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

load("@rules_java//java:defs.bzl", "java_test")

java_test(
    name = "GcsBlobStorageClientIntegrationTest",
    srcs = ["GcsBlobStorageClientIntegrationTest.java"],
    deps = [
        "//java/com/google/scp/operator/cpio/blobstorageclient",
        "//java/com/google/scp/operator/cpio/blobstorageclient:gcp",
        "//java/com/google/scp/operator/cpio/blobstorageclient:model",
        "//java/external:acai",
        "//java/external:gcp_storage",
        "//java/external:google_truth",
        "//java/external:guava",
        "//java/external:guice",
        "//javatests/com/google/scp/shared/testutils/gcp",
    ],
)
