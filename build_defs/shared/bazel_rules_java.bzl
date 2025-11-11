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

def bazel_rules_java():
    maybe(
        http_archive,
        name = "rules_java",
        url = "https://github.com/bazelbuild/rules_java/releases/download/8.4.0/rules_java-8.4.0.tar.gz",
        sha256 = "aff2f902f660928ea566e3002f46fc19caa496427c8a264ce578934dd10ef103",
    )
