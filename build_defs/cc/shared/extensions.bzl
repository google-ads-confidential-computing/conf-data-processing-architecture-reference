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

"""Common C++ external dependencies for Bzlmod."""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# buildifier: disable=unused-variable
def _cc_deps_extension_impl(ctx):
    http_archive(
        name = "com_github_nghttp2_nghttp2",
        build_file = Label("//build_defs/cc/shared/build_targets:nghttp2.BUILD"),
        patch_args = ["-p1"],
        patches = [Label("//build_defs/cc/shared/build_targets:nghttp2.patch")],
        sha256 = "62f50f0e9fc479e48b34e1526df8dd2e94136de4c426b7680048181606832b7c",
        strip_prefix = "nghttp2-1.47.0",
        urls = [
            "https://github.com/nghttp2/nghttp2/releases/download/v1.47.0/nghttp2-1.47.0.tar.gz",
        ],
    )

    http_archive(
        name = "com_google_farmhash",
        build_file = Label("//build_defs/cc/shared:farmhash.BUILD"),
        integrity = "sha256-GDks8HNuHWLsu41pXDFJa2UHhZ6MdVQdetC6CS3FIRU=",
        strip_prefix = "farmhash-0d859a811870d10f53a594927d0d0b97573ad06d",
        # Commits on May 13, 2019 as the latest stable version. The repo is archived at Jan 10, 2023.
        urls = [
            "https://github.com/google/farmhash/archive/0d859a811870d10f53a594927d0d0b97573ad06d.tar.gz",
        ],
    )

cc_deps_extension = module_extension(
    implementation = _cc_deps_extension_impl,
)
