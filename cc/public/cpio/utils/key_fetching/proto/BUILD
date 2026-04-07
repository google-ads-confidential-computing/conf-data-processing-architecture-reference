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

load("@com_google_protobuf//bazel:cc_proto_library.bzl", "cc_proto_library")
load("@com_google_protobuf//bazel:proto_library.bzl", "proto_library")

package(default_visibility = ["//visibility:public"])

proto_library(
    name = "encryption_key_prefetch_config_proto",
    srcs = ["encryption_key_prefetch_config.proto"],
    deps = ["@com_google_protobuf//:duration_proto"],
)

proto_library(
    name = "key_coordinator_configuration_proto",
    srcs = ["key_coordinator_configuration.proto"],
    deps = ["//cc/public/cpio/proto/private_key_service/v1:private_key_service_proto"],
)

##############################################################################
# C++
##############################################################################
cc_proto_library(
    name = "encryption_key_prefetch_config_cc_proto",
    deps = [":encryption_key_prefetch_config_proto"],
)

cc_proto_library(
    name = "key_coordinator_configuration_cc_proto",
    deps = [":key_coordinator_configuration_proto"],
)
