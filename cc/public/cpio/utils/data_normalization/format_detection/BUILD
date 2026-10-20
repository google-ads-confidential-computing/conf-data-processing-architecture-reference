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

load("@rules_cc//cc:cc_library.bzl", "cc_library")
load("@rules_cc//cc:cc_test.bzl", "cc_test")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "format_detection",
    srcs = ["detector_util.cc"],
    hdrs = [
        "combined_address_detector.h",
        "detector.h",
        "detector_util.h",
        "email_detector.h",
        "phone_number_detector.h",
    ],
    deps = [
        "//cc:cc_base_include_dir",
        "@com_google_absl//absl/strings",
    ],
)

cc_test(
    name = "detector_util_test",
    srcs = ["detector_util_test.cc"],
    deps = [
        ":format_detection",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "email_detector_test",
    srcs = ["email_detector_test.cc"],
    deps = [
        ":format_detection",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "phone_number_detector_test",
    srcs = ["phone_number_detector_test.cc"],
    deps = [
        ":format_detection",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "combined_address_detector_test",
    srcs = ["combined_address_detector_test.cc"],
    deps = [
        ":format_detection",
        "@com_google_googletest//:gtest_main",
    ],
)
