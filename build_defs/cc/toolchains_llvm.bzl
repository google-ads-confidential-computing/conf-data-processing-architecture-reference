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

"""Toolchains LLVM build defs."""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

def toolchains_llvm():
    # -------------------------------------------------------------------------
    # Workaround for `bazel sync` fetching Windows-only dependencies and failing
    # -------------------------------------------------------------------------
    # `aspect_bazel_lib` references `bsd_tar_windows_amd64` which has a broken
    # libarchive GitHub URL in its older versions. Since `bazel sync` evaluates
    # everything indiscriminately on all platforms, it will fail on this repository
    # even when building on Linux where it's not used. We override it here with
    # a working URL to a tiny, unrelated zip file just to make `bazel sync` happy.
    http_archive(
        name = "bsd_tar_windows_amd64",
        build_file_content = """
filegroup(name = "bsdtar_toolchain")
filegroup(name = "bsdtar_bin")
""",
        sha256 = "2037875b9a4456dce4a79d112a8ae885bbc4aad968e6587dca6e64f3a0900cdf",
        urls = ["https://github.com/bazelbuild/rules_cc/releases/download/0.0.9/rules_cc-0.0.9.tar.gz"],
    )

    http_archive(
        name = "toolchains_llvm",
        sha256 = "3eae3dc24aa51211a722076d982bcc24f2901d424edfd1e099d2782d875674fc",
        strip_prefix = "toolchains_llvm-1.3.0",
        url = "https://github.com/bazel-contrib/toolchains_llvm/archive/refs/tags/v1.3.0.tar.gz",
    )
