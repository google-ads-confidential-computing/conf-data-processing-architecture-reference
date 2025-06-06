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

load("@rules_cc//cc:defs.bzl", "cc_library", "cc_proto_library")
load("@rules_java//java:defs.bzl", "java_proto_library")
load("@rules_proto//proto:defs.bzl", "proto_library")

package(default_visibility = ["//visibility:public"])

proto_library(
    name = "execution_result_proto",
    srcs = glob(
        [
            "execution_result.proto",
        ],
    ),
)

cc_proto_library(
    name = "execution_result_cc_proto",
    deps = [":execution_result_proto"],
)

##########################################
# Java
##########################################
java_proto_library(
    name = "execution_result_java_proto",
    deps = [":execution_result_proto"],
)

cc_library(
    name = "errors",
    srcs = ["//cc/public/core/src:errors.cc"],
    hdrs =
        [
            "errors.h",
        ],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/core/interface:interface_lib",
    ],
)

cc_library(
    name = "execution_result",
    srcs = ["//cc/public/core/src:execution_result.cc"],
    hdrs =
        [
            "execution_result.h",
            "execution_result_macros.h",
            "execution_result_or_macros.h",
        ],
    deps = [
        ":execution_result_cc_proto",
        "//cc:cc_base_include_dir",
    ],
)
