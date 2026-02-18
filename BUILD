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

load("@com_github_bazelbuild_buildtools//buildifier:def.bzl", "buildifier")

package(default_visibility = ["//visibility:public"])

buildifier(
    name = "buildifier_check",
    lint_mode = "warn",
    # TODO(b/478054650): cleanup these lint warnings then remove each from this list.
    lint_warnings = [
        "-attr-licenses",
        "-constant-glob",
        "-function-docstring",
        "-function-docstring-args",
        "-function-docstring-header",
        "-function-docstring-return",
        "-module-docstring",
        "-no-effect",
        "-print",
        "-unused-variable",
    ],
    mode = "check",
)

buildifier(
    name = "buildifier_fix",
    lint_mode = "fix",
    mode = "fix",
)

exports_files([
    "original_source_code.tar",
])

genrule(
    name = "copy_source_code_tar",
    srcs = [
        "//:original_source_code.tar",
    ],
    outs = ["source_code.tar"],
    cmd = "cp $(location //:original_source_code.tar) $@",
)

package_group(
    name = "scp_internal_pkg",
    packages = [
        "//cc/...",
        "//java/...",
        "//javatests/...",
    ],
)
