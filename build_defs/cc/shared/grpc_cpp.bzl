# Copyright 2025 Google LLC
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

def import_grpc_cpp():
    maybe(
        http_archive,
        name = "com_github_grpc_grpc",
        sha256 = "cd256d91781911d46a57506978b3979bfee45d5086a1b6668a3ae19c5e77f8dc",
        strip_prefix = "grpc-1.69.0",
        url = "https://github.com/grpc/grpc/archive/v1.69.0.tar.gz",
        patch_args = ["-p1"],
        patches = [Label("//build_defs/cc/shared:grpc_cpp.patch")],
    )
