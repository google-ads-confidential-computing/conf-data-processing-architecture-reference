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

def cc_utils():
    maybe(
        http_archive,
        name = "nlohmann_json",
        urls = [
            "https://github.com/nlohmann/json/archive/v3.11.3.tar.gz",
        ],
        sha256 = "0d8ef5af7f9794e3263480193c491549b2ba6cc74bb018906202ada498a79406",
        strip_prefix = "json-3.11.3",
    )

    maybe(
        http_archive,
        name = "onetbb",
        urls = [
            "https://github.com/uxlfoundation/oneTBB/archive/v2021.10.0.tar.gz",
        ],
        sha256 = "487023a955e5a3cc6d3a0d5f89179f9b6c0ae7222613a7185b0227ba0c83700b",
        strip_prefix = "oneTBB-2021.10.0",
    )

    maybe(
        http_archive,
        # The name should match what is needed from google-cloud-cpp.
        name = "com_github_curl_curl",
        build_file = Label("//build_defs/cc/shared/build_targets:curl.BUILD"),
        # Matches the version needed from google-cloud-cpp.
        sha256 = "01ae0c123dee45b01bbaef94c0bc00ed2aec89cb2ee0fd598e0d302a6b5e0a98",
        strip_prefix = "curl-7.69.1",
        urls = [
            "https://storage.googleapis.com/mirror.tensorflow.org/curl.haxx.se/download/curl-7.69.1.tar.gz",
            "https://curl.haxx.se/download/curl-7.69.1.tar.gz",
        ],
    )
