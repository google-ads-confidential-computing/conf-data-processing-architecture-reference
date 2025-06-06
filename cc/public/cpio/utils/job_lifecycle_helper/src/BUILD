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
    name = "job_lifecycle_helper",
    srcs = glob(
        [
            "*.cc",
            "*.h",
        ],
    ),
    deps = [
        "//cc:cc_base_include_dir",
        "//cc/core/common/concurrent_map/src:concurrent_map_lib",
        "//cc/core/common/operation_dispatcher/src:operation_dispatcher_lib",
        "//cc/core/interface:interface_lib",
        "//cc/cpio/client_providers/metric_client_provider/src:metric_client_provider_lib",
        "//cc/cpio/server/interface:server_interface_lib",
        "//cc/cpio/server/interface/auto_scaling_service:auto_scaling_service_interface_lib",
        "//cc/cpio/server/interface/job_service:job_service_interface_lib",
        "//cc/public/cpio/interface:type_def",
        "//cc/public/cpio/interface/auto_scaling_client",
        "//cc/public/cpio/interface/job_client",
        "//cc/public/cpio/interface/metric_client:metric_client_interface",
        "//cc/public/cpio/utils/job_lifecycle_helper/interface:job_lifecycle_helper_interface",
        "//cc/public/cpio/utils/metric_instance/interface:metric_instance_interface",
        "//cc/public/cpio/utils/metric_instance/interface:type_def",
        "//cc/public/cpio/utils/metric_instance/noop:noop_metric_instance",
        "//cc/public/cpio/utils/metric_instance/src:metric_instance",
        "@com_google_protobuf//:protobuf",
    ],
)
