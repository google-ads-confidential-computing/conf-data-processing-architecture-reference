# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@rules_pkg//:mappings.bzl", "pkg_files")
load("//tools/tftesting:tf_test.bzl", "tf_module", "tf_test")

package(default_visibility = ["//visibility:public"])

pkg_files(
    name = "frontend",
    srcs = glob(
        ["*"],
        exclude = [
            "BUILD",
            "*.tftest.hcl",
        ],
    ),
    prefix = "frontend",
)

tf_module(
    name = "frontend_module",
    deps = [
        "//operator/terraform/gcp/modules/cloud_run_load_balancer:cloud_run_load_balancer_module",
        "//operator/terraform/gcp/modules/cloud_run_service:cloud_run_service_module",
        "//operator/terraform/gcp/modules/domain_a_records:domain_a_records_module",
        "//operator/terraform/gcp/modules/shared/cloud_run_alarms:cloud_run_alarms_module",
        "//operator/terraform/gcp/modules/shared/cloud_run_lb_alarms:cloud_run_lb_alarms_module",
        "//operator/terraform/gcp/modules/shared/cloudfunction_alarms:cloudfunction_alarms_module",
    ],
)

tf_test(
    name = "frontend_test",
    srcs = glob([
        "*.tftest.hcl",
    ]),
    deps = [
        ":frontend_module",
    ],
)
