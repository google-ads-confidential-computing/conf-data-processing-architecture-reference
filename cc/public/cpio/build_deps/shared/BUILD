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

load("@rules_pkg//:mappings.bzl", "pkg_files")
load("//cc/public/cpio/build_deps/shared:snapshot_build_time_image.bzl", "snapshot_build_time_image")
load("//cc/public/cpio/build_deps/shared:snapshot_runtime_image.bzl", "snapshot_runtime_image")

package(default_visibility = ["//visibility:public"])

# Collection of helper scripts to support reproducible build.
pkg_files(
    name = "reproducible_build_scripts_pkg",
    srcs = [
        "reproducible_build.sh",
    ],
    prefix = "reproducible_build_utils",
)

snapshot_runtime_image(
    name = "stable_runtime_image",
    version = "v0.3",
)

snapshot_build_time_image(
    name = "stable_build_time_image",
    version = "v0.2",
)
