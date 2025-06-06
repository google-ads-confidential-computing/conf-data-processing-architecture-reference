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

cc_test(
    name = "errors_test",
    size = "small",
    srcs = ["errors_test.cc"],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/public/core/interface:errors",
        "//cc/public/cpio/interface:cpio_errors",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "execution_result_test",
    size = "small",
    srcs = ["execution_result_test.cc"],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/core/common/global_logger/src:global_logger_lib",
        "//cc/core/logger/mock:logger_mock",
        "//cc/public/core/interface:execution_result",
        "//cc/public/core/interface:execution_result_cc_proto",
        "//cc/public/core/test/interface:execution_result_matchers",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "execution_result_macros_test",
    size = "small",
    srcs = ["execution_result_macros_test.cc"],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/core/common/global_logger/src:global_logger_lib",
        "//cc/core/logger/mock:logger_mock",
        "//cc/public/core/interface:execution_result",
        "//cc/public/core/interface:execution_result_cc_proto",
        "//cc/public/core/test/interface:execution_result_matchers",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "struct_matchers_test",
    size = "small",
    srcs = ["struct_matchers_test.cc"],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/public/core/test/interface:struct_matchers",
        "@com_google_googletest//:gtest_main",
    ],
)
