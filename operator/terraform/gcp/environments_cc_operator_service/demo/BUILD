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

load("@rules_pkg//:mappings.bzl", "pkg_files", "pkg_mklink")

package(default_visibility = ["//visibility:public"])

# pkg_tar requires links be declared with mklink, otherwise the linked file is
# copied.
pkg_mklink(
    name = "operator_service_link",
    link_name = "demo/operator_service.tf",
    target = "../../environments_cc_operator_service/shared/operator_service.tf",
)

pkg_mklink(
    name = "operator_service_vars_link",
    link_name = "demo/variables.tf",
    target = "../../environments_cc_operator_service/shared/variables.tf",
)

pkg_mklink(
    name = "operator_service_parameter_vars_link",
    link_name = "demo/parameter_variables.tf",
    target = "../../environments_cc_operator_service/shared/parameter_variables.tf",
)

pkg_mklink(
    name = "operator_service_outputs_link",
    link_name = "demo/outputs.tf",
    target = "../../environments_cc_operator_service/shared/outputs.tf",
)

pkg_files(
    name = "demo_files",
    srcs = [
        "example.auto.tfvars",
        "main.tf",
    ],
    prefix = "demo",
)
