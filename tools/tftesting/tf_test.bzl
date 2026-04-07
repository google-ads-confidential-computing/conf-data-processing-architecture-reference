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

"""Module for Terraform testing rules in Bazel."""

def _tf_test(ctx):
    out = ctx.actions.declare_file(ctx.label.name + "tftest.sh")
    ctx.actions.write(
        output = out,
        content = """
            #!/bin/bash

            # TODO: b/491467542 - Determine whether cleaning up the .terraform
            # folder is necessary here
            cd {}
            # TODO: b/491712151 - Use a bazel toolchain to import an
            # appropriate terraform binary.
            terraform init
            terraform test
        """.format(ctx.label.package),
        is_executable = True,
    )

    # We need the source .tf, source .tftest.hcl, .tf files from all
    # dependencies, and the mock files in order to run the test.
    runfiles = ctx.runfiles(
        files = ctx.files.srcs + ctx.files.deps + ctx.files._mock_files,
    )

    # We return all the files necessary to run the test in the filegroup so that
    # other tests that depend on this module can depend on the test files as
    # well.
    return [DefaultInfo(executable = out, runfiles = runfiles, files = runfiles.files)]

# Creates a test target and also a filegroup that has all of the
# files needed for depending on this TF module for other tests.
tf_test = rule(
    implementation = _tf_test,
    test = True,
    attrs = {
        "srcs": attr.label_list(allow_files = [".tftest.hcl"]),
        "deps": attr.label_list(allow_files = [".tf", ".tf.json"], default = []),
        "_mock_files": attr.label_list(allow_files = [".tfmock.hcl"], default = ["//tools/tftesting:tfmocks"]),
    },
)

def _glob_terraform_configuration_files():
    # Terraform configuration files must end in either .tf or .tf.json.
    # Otherwise, the files are ignored.
    # Also see
    # https://www.terraform.io/docs/configuration/index.html#code-organization.
    return native.glob(["*.tf", "*.tf.json"])

def tf_module(
        name,
        deps = None,
        additional_srcs = None,
        visibility = None):
    """Gathers the files of this package's Terraform module under a single label.

    Args:
      name: name of this BUILD rule and the filegroup it creates.
      deps: other Terraform modules that this module directly depends on.
      additional_srcs: list of additional source files (besides the default glob
          of *.tf and *.tf.json), which are part of the given Terraform module.
      visibility: visibility label.
    """

    deps = deps or []
    additional_srcs = additional_srcs or []

    native.filegroup(
        name = name,
        srcs = _glob_terraform_configuration_files() + deps + additional_srcs,
        visibility = visibility,
    )
