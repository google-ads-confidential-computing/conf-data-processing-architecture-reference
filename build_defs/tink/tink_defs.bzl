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

"""Defines Tink dependencies."""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")

# List of Maven dependencies necessary for Tink to compile -- to be included in
# the list of Maven dependenceis passed to maven_install by the workspace.

TINK_CC_VERSION = "2.4.0"  # Nov 11, 2025
TINK_GCP_KMS_VERSION = "1.10.0"  # Mar 27, 2024
TINK_JAVA_VERSION = "1.18.0"  # Jun 18, 2025
TINK_MAVEN_ARTIFACTS = [
    "com.google.crypto.tink:tink:" + TINK_JAVA_VERSION,
    "com.google.crypto.tink:tink-gcpkms:" + TINK_GCP_KMS_VERSION,
]

def import_tink_git():
    """Imports two of the Tink Bazel workspaces.

    @tink_cc for C++ dependencies.
    @tink_proto is pulled from tink-java but is only used for proto deps. Tink-java
    was chosen arbitrarily as all tink repos have the protos.
    """

    maybe(
        http_archive,
        name = "tink_cc",
        strip_prefix = "tink-cc-{}".format(TINK_CC_VERSION),
        sha256 = "3323658909e3e3a3de5a251b9385ba2cea444ccec80cfefe1747a3f3dc6b96ec",
        url = "https://github.com/tink-crypto/tink-cc/archive/refs/tags/v{}.tar.gz".format(TINK_CC_VERSION),
    )

    maybe(
        http_archive,
        name = "tink_proto",
        strip_prefix = "tink-java-{}".format(TINK_JAVA_VERSION),
        sha256 = "3c0a9d0217fd3dc68a618abe9ef718b7fc5751faf420e7bb388eb373d6f51da1",
        url = "https://github.com/tink-crypto/tink-java/archive/refs/tags/v{}.tar.gz".format(TINK_JAVA_VERSION),
    )
