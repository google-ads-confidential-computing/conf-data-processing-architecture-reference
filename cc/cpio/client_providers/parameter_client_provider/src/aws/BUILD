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

load("@rules_cc//cc:defs.bzl", "cc_library")

package(default_visibility = ["//cc:scp_cc_internal_pkg"])

cc_library(
    name = "aws_parameter_client_provider_deps",
    srcs = [
    ],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/core/async_executor/src/aws:core_aws_async_executor_lib",
        "//cc/cpio/client_providers/instance_client_provider/src/aws:aws_instance_client_utils",
        "//cc/cpio/client_providers/interface:cpio_client_providers_interface_lib",
        "//cc/cpio/client_providers/interface:type_def",
        "//cc/cpio/common/src/aws:aws_utils_lib",
        "//cc/public/cpio/interface:cpio_errors",
        "//cc/public/cpio/proto/parameter_service/v1:parameter_service_cc_proto",
        "@aws_sdk_cpp//:core",
        "@aws_sdk_cpp//:ssm",
    ],
)

cc_library(
    name = "aws_parameter_client_provider_lib",
    srcs = [
        ":aws_parameter_client_provider_srcs",
    ],
    deps = [
        ":aws_parameter_client_provider_deps",
    ],
)

exports_files([
    "aws_parameter_client_provider.h",
    "aws_parameter_client_provider.cc",
    "ssm_error_converter.h",
    "ssm_error_converter.cc",
    "error_codes.h",
])

filegroup(
    name = "aws_parameter_client_provider_srcs",
    srcs = [
        ":aws_parameter_client_provider.cc",
        ":aws_parameter_client_provider.h",
        ":error_codes.h",
        ":ssm_error_converter.cc",
        ":ssm_error_converter.h",
    ],
)
