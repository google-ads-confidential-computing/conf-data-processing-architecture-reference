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

# Note: these rules add a dependency on the golang toolchain and must be ordered
# after any `go_register_toolchains` calls in this file (or else the toolchain
# defined in io_bazel_rules_docker are used for future go toolchains)

def bazel_rules_oci():
    maybe(
        http_archive,
        name = "rules_oci",
        sha256 = "5994ec0e8df92c319ef5da5e1f9b514628ceb8fc5824b4234f2fe635abb8cc2e",
        strip_prefix = "rules_oci-2.2.6",
        url = "https://github.com/bazel-contrib/rules_oci/releases/download/v2.2.6/rules_oci-v2.2.6.tar.gz",
    )
