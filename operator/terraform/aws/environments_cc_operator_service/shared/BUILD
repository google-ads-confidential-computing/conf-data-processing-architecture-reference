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

pkg_mklink(
    name = "shared_operator_service_vars_link",
    link_name = "shared/variables.tf",
    target = "../../applications/cc-operator-service/variables.tf",
)

pkg_mklink(
    name = "shared_operator_service_parameter_vars_link",
    link_name = "shared/parameter_variables.tf",
    target = "../../applications/cc-operator-service/parameter_variables.tf",
)

pkg_files(
    name = "shared_files",
    srcs = [
        "operator_service.tf",
        "//operator/terraform/aws:ami_params_auto_tfvars",
    ],
    prefix = "shared",
)
