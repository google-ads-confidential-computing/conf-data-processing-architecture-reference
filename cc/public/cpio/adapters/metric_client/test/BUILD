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

load("@rules_cc//cc:defs.bzl", "cc_test")

package(default_visibility = ["//cc:scp_cc_internal_pkg"])

exports_files(["test_aws_metric_client.h"])

filegroup(
    name = "test_aws_metric_client_srcs",
    srcs = [
        ":test_aws_metric_client.h",
    ],
)

cc_test(
    name = "metric_client_test",
    size = "small",
    timeout = "long",
    srcs = ["metric_client_test.cc"],
    copts = [
        "-std=c++17",
    ],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/core/interface:interface_lib",
        "//cc/core/test/utils:utils_lib",
        "//cc/cpio/client_providers/metric_client_provider/src:metric_client_provider_lib",
        "//cc/cpio/client_providers/otel_metric_client_provider/src:otel_metric_client_provider_lib",
        "//cc/public/core/test/interface:execution_result_matchers",
        "//cc/public/cpio/adapters/metric_client/mock:metric_client_mock",
        "//cc/public/cpio/core/mock:lib_cpio_mock",
        "//cc/public/cpio/interface:cpio_errors",
        "//cc/public/cpio/interface:type_def",
        "//cc/public/cpio/interface/metric_client",
        "//cc/public/cpio/interface/metric_client:type_def",
        "//cc/public/cpio/proto/metric_service/v1:metric_service_cc_proto",
        "@com_google_googletest//:gtest_main",
    ],
)
