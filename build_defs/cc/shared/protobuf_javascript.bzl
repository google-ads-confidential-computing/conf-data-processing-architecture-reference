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

def protobuf_javascript(scp_repo_name = ""):
    maybe(
        http_archive,
        name = "com_google_protobuf_javascript",
        sha256 = "8cef92b4c803429af0c11c4090a76b6a931f82d21e0830760a17f9c6cb358150",
        strip_prefix = "protobuf-javascript-3.21.4",
        urls = ["https://github.com/protocolbuffers/protobuf-javascript/archive/refs/tags/v3.21.4.tar.gz"],
    )
