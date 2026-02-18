# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@rules_cc//cc:cc_library.bzl", "cc_library")

package(default_visibility = ["//cc:scp_cc_internal_pkg"])

cc_library(
    name = "test_aws_otel_metric_client_provider_lib",
    srcs = [
        "test_aws_otel_metric_client_provider.cc",
        "test_aws_otel_metric_client_provider.h",
        "//cc/cpio/client_providers/otel_metric_client_provider/src/aws:aws_otel_metric_client_provider_srcs",
    ],
    copts = [
        "-DTEST_CPIO=1",
    ],
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/cpio/client_providers/otel_metric_client_provider/src:opentelemetry_utils_lib",
        "//cc/cpio/client_providers/otel_metric_client_provider/src/aws:aws_otel_metric_client_provider_deps",
        "//cc/cpio/common/test/aws:test_aws_utils_lib",
        "//cc/public/cpio/test/metric_client:test_aws_metric_client_options",
    ],
)
