# Copyright 2023 Google LLC
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

load("@rules_cc//cc:defs.bzl", "cc_library")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "gcp_database_factory_lib",
    srcs =
        [
            "gcp_database_factory.cc",
            "gcp_database_factory.h",
        ],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/public/cpio/interface:type_def",
        "@com_github_googleapis_google_cloud_cpp//:spanner",
    ],
)

cc_library(
    name = "gcp_utils_lib",
    srcs = [
        "gcp_utils.cc",
        "gcp_utils.h",
    ],
    copts = [
        "-std=c++17",
    ],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/core/common/global_logger/src:global_logger_lib",
        "//cc/cpio/client_providers/instance_client_provider/src/gcp:gcp_instance_client_provider_lib",
        "//cc/public/cpio/interface:cpio_errors",
        "@com_github_googleapis_google_cloud_cpp//:common",
        "@com_github_grpc_grpc//:grpc++",
        "@com_google_absl//absl/strings",
    ],
)
