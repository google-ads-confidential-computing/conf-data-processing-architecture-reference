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

# Note: Go build rules are an indirect dependency of "io_bazel_rules_docker" and
# a direct dependency of rpmpack. These rules are not used for deploying go code
# at the time of writing.

def go_deps():
    maybe(
        http_archive,
        name = "io_bazel_rules_go",
        sha256 = "90fe8fb402dee957a375f3eb8511455bd738c7ed562695f4dd117ac7d2d833b1",
        urls = [
            "https://github.com/bazel-contrib/rules_go/releases/download/v0.52.0/rules_go-v0.52.0.zip",
        ],
    )
    maybe(
        http_archive,
        name = "bazel_gazelle",
        sha256 = "e467b801046b6598c657309b45d2426dc03513777bd1092af2c62eebf990aca5",
        urls = [
            "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.45.0/bazel-gazelle-v0.45.0.tar.gz",
        ],
    )
