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

def bazel_rules_oci():
    maybe(
        http_archive,
        name = "rules_oci",
        sha256 = "b8db7ab889d501db33313620b2c8040dbb07e95c26a0fefe06004b35baf80e08",
        strip_prefix = "rules_oci-2.2.7",
        url = "https://github.com/bazel-contrib/rules_oci/releases/download/v2.2.7/rules_oci-v2.2.7.tar.gz",
    )
