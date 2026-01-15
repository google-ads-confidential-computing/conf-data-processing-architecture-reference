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

def com_google_farmhash():
    maybe(
        http_archive,
        name = "com_google_farmhash",
        strip_prefix = "farmhash-0d859a811870d10f53a594927d0d0b97573ad06d",
        sha256 = "18392cf0736e1d62ecbb8d695c31496b6507859e8c75541d7ad0ba092dc52115",
        build_file = Label("//build_defs/cc/shared:farmhash.BUILD"),
        # Commits on May 13, 2019 as the latest stable version. The repo is archived at Jan 10, 2023.
        urls = [
            "https://github.com/google/farmhash/archive/0d859a811870d10f53a594927d0d0b97573ad06d.tar.gz",
        ],
    )
