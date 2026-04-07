# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@rules_cc//cc:cc_library.bzl", "cc_library")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "key_fetching_metric_utils",
    srcs = ["key_fetching_metric_utils.cc"],
    hdrs = ["key_fetching_metric_utils.h"],
    deps = [
        "//cc/core/common/uuid/src:uuid_lib",
        "//cc/public/core/interface:execution_result",
        "//cc/public/cpio/interface:cpio_errors",
        "//cc/public/cpio/proto/metric_service/v1:metric_service_cc_proto",
        "//cc/public/cpio/utils/dual_writing_metric_client/interface:dual_writing_metric_client_interface",
        "@com_google_absl//absl/strings",
    ],
)
