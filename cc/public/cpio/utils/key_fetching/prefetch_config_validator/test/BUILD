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

load("@rules_cc//cc:cc_test.bzl", "cc_test")

package(default_visibility = ["//visibility:public"])

cc_test(
    name = "encryption_key_prefetch_config_validator_test",
    size = "small",
    srcs = ["encryption_key_prefetch_config_validator_test.cc"],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/core/test/utils:utils_lib",
        "//cc/public/core/test/interface:execution_result_matchers",
        "//cc/public/cpio/utils/dual_writing_metric_client/mock:dual_writing_metric_client_mock",
        "//cc/public/cpio/utils/key_fetching/prefetch_config_validator/src:encryption_key_prefetch_config_validator",
        "//cc/public/cpio/utils/key_fetching/src:key_fetching_metric_utils",
        "@com_google_googletest//:gtest_main",
    ],
)
