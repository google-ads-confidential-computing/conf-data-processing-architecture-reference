# Copyright 2026 Google LLC
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

"""Repository rules for rules_distroless."""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")

def bazel_rules_distroless():
    maybe(
        http_archive,
        name = "rules_distroless",
        strip_prefix = "rules_distroless-0.5.1",
        urls = ["https://github.com/GoogleContainerTools/rules_distroless/archive/refs/tags/v0.5.1.tar.gz"],
        sha256 = "352b297e608f95a179e93c62c4688d5713125f7aa9e2f71e2fbcc67ac6490540",
    )
