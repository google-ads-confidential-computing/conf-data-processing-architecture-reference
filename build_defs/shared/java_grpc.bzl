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

def java_grpc():
    maybe(
        http_archive,
        name = "io_grpc_grpc_java",
        sha256 = "970ac87fccbaa6c978dc56b0ba72db53b6401821c01197e1942aecb347e5f218",
        strip_prefix = "grpc-java-1.69.1",
        urls = ["https://github.com/grpc/grpc-java/archive/v1.69.1.tar.gz"],
    )
