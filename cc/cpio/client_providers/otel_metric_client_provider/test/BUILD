# Copyright 2025 Google LLC
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

package(default_visibility = ["//cc:scp_cc_internal_pkg"])

cc_test(
    name = "otel_metric_client_provider_test",
    size = "small",
    srcs = [
        "otel_metric_client_provider_test.cc",
    ],
    copts = [
        "-std=c++17",
    ],
    deps = [
        "//cc/core/interface:interface_lib",
        "//cc/core/test/utils:utils_lib",
        "//cc/cpio/client_providers/instance_client_provider/mock:instance_client_provider_mock",
        "//cc/cpio/client_providers/otel_metric_client_provider/mock:mock_opentelemetry_lib",
        "//cc/cpio/client_providers/otel_metric_client_provider/src:otel_metric_client_provider_lib",
        "//cc/public/core/test/interface:execution_result_matchers",
        "//cc/public/cpio/proto/metric_service/v1:metric_service_cc_proto",
        "@com_google_protobuf//:protobuf",
    ],
)
