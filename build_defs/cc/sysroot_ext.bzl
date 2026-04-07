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
"""Defines a Bzlmod extension to import the chromium sysroot.
This is used to expose the `chromium_sysroot` repository created in `WORKSPACE`
to `MODULE.bazel` to circumvent Bzlmod canonical repo visibility restrictions,
that prevent using WORKSPACE-defined repos in MODULE.bazel.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

def chromium_sysroot():
    http_archive(
        name = "chromium_sysroot",
        build_file_content = """
filegroup(
    name = "sysroot",
    srcs = glob(["**/*"]),
    visibility = ["//visibility:public"],
)
""",
        sha256 = "84656a6df544ecef62169cfe3ab6e41bb4346a62d3ba2a045dc5a0a2ecea94a3",
        # This is a snapshot of a sysroot for debian stretch (9). This snapshot was created on 2017-11-15T20:26:14.722Z.
        # We purposely pick an old version of the sysroot to guarantee maximum glibc compatibility.
        # The index can be found here: https://commondatastorage.googleapis.com/chrome-linux-sysroot/
        urls = ["https://commondatastorage.googleapis.com/chrome-linux-sysroot/toolchain/2202c161310ffde63729f29d27fe7bb24a0bc540/debian_stretch_amd64_sysroot.tar.xz"],
    )

# buildifier: disable=unused-variable
def _sysroot_impl(ctx):
    chromium_sysroot()

sysroot_ext = module_extension(
    implementation = _sysroot_impl,
)
