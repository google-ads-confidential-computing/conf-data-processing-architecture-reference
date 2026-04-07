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
    name = "autoscaling",
    srcs = glob(
        ["*"],
        exclude = [
            "BUILD",
            "*.tftest.hcl",
        ],
    ),
    prefix = "autoscaling",
)

tf_module(
    name = "autoscaling_module",
    deps = [
        "//operator/terraform/gcp/modules/shared/cloud_run_alarms:cloud_run_alarms_module",
        "//operator/terraform/gcp/modules/shared/cloudfunction_alarms:cloudfunction_alarms_module",
    ],
)

tf_test(
    name = "autoscaling_test",
    srcs = glob([
        "*.tftest.hcl",
    ]),
    deps = [
        ":autoscaling_module",
    ],
)
