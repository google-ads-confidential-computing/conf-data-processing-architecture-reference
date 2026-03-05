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

"""Terraform binary build defs."""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")

def terraform():
    maybe(
        http_archive,
        name = "terraform",
        build_file_content = """
package(default_visibility = ["//visibility:public"])
exports_files(["terraform"])
""",
        sha256 = "c71fd5d500a7e4d869bf5d12176c72d1dfc00440b862116797694361671f77c8",
        url = "https://releases.hashicorp.com/terraform/1.12.0/terraform_1.12.0_linux_amd64.zip",
    )
