# Copyright 2024 Google LLC
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

"""Bazel rule to build a base runtime image for CPIO."""

load("@rules_distroless//distroless:defs.bzl", "group")
load("@rules_oci//oci:defs.bzl", "oci_image", "oci_load")

def base_runtime_image(
        *,
        name,
        version):
    """Builds a base runtime image for CPIO.

    Args:
      name: The name of the target. Used to prefix load and push targets.
      version: The version tag for the image.
    """

    # Needed for rsyslog. Group adm must exist in the runtime image.
    # Creates file /etc/group in the filesystem, containing:
    #   root:x:0:
    #   adm:x:4:
    group(
        name = "%s_group" % name,
        entries = [
            dict(
                name = "root",
                password = "x",
                gid = 0,
            ),
            dict(
                name = "adm",
                password = "x",
                gid = 4,
            ),
        ],
    )

    oci_image(
        name = name,
        base = "@java_base_oci",
        tars = [
            "@scp_base_runtime_image_debian_packages//:flat",
            ":%s_group" % name,
        ],
        tags = ["manual"],
    )

    oci_load(
        name = "%s_load" % name,
        image = ":%s" % name,
        repo_tags = ["%s:%s" % (name, version)],
        tags = ["manual"],
    )
