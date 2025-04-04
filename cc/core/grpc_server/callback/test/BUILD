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

load(
    "@com_google_googleapis_imports//:imports.bzl",
    "cc_proto_library",
)
load("@rules_cc//cc:defs.bzl", "cc_test")
load("@rules_proto//proto:defs.bzl", "proto_library")

# package(default_visibility = ["//visibility:public"])

proto_library(
    name = "callback_server_proto",
    srcs = ["callback_server.proto"],
    deps = [
        "//cc/public/core/interface:execution_result_proto",
    ],
)

##############################################################################
# C++
##############################################################################
cc_proto_library(
    name = "callback_server_cc_proto",
    deps = [":callback_server_proto"],
)

cc_test(
    name = "read_reactor_test",
    srcs = ["read_reactor_test.cc"],
    deps = [
        ":callback_server_cc_proto",
        "//cc:cc_base_include_dir",
        "//cc/core/grpc_server/callback/src:grpc_callback_lib",
        "//cc/core/interface:interface_lib",
        "//cc/core/test/utils:utils_lib",
        "//cc/public/core/test/interface:execution_result_matchers",
        "@com_github_grpc_grpc//:grpc++",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "write_reactor_test",
    srcs = ["write_reactor_test.cc"],
    deps = [
        ":callback_server_cc_proto",
        "//cc:cc_base_include_dir",
        "//cc/core/grpc_server/callback/src:grpc_callback_lib",
        "//cc/core/interface:interface_lib",
        "//cc/core/test/utils:utils_lib",
        "//cc/public/core/test/interface:execution_result_matchers",
        "@com_github_grpc_grpc//:grpc++",
        "@com_google_googletest//:gtest_main",
    ],
)
