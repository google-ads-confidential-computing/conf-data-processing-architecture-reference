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

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")

def import_google_cloud_cpp():
    # Load the googleapis dependency for gcloud.
    maybe(
        http_archive,
        name = "com_github_googleapis_google_cloud_cpp",
        sha256 = "2072880320cbd5a4690d6bbaa6e4c4b1dd32be22b2ff42b0cbe30db4b2642df4",
        strip_prefix = "google-cloud-cpp-2.41.0",
        url = "https://github.com/googleapis/google-cloud-cpp/archive/v2.41.0.tar.gz",
    )
