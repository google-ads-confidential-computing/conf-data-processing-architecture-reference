# Copyright 2022 Google LLC
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

load("@com_github_google_rpmpack//:def.bzl", "pkg_tar2rpm")
load("@io_bazel_rules_docker//container:container.bzl", "container_image")
load("@rules_pkg//:pkg.bzl", "pkg_tar")

package(
    default_visibility = ["//visibility:public"],
)

# Tag all targets in this package as manual because all //cc/... targets are
# currently built in a container, conflicting with :reproducible_proxy_outputs
# which is built in a different container. These targets should only be built
# when being explicitly included as an RPM for an AMI.

pkg_tar(
    name = "proxy_bin",
    srcs = [
        # Use files built inside AmazonLinux2 container to maintain compatability with AL2.
        ":proxy",
    ],
    mode = "0755",
    ownername = "root.root",
    package_dir = "opt/google/proxy",
    tags = ["manual"],
)

pkg_tar(
    name = "proxy_bin_al2023",
    srcs = [
        # Use files built inside AmazonLinux2023 container to maintain compatability with AL2023.
        "//cc/proxy/parent_instance/aws/al2023:proxy",
    ],
    mode = "0755",
    ownername = "root.root",
    package_dir = "opt/google/proxy",
    tags = ["manual"],
)

pkg_tar(
    name = "proxy_service",
    srcs = [
        "vsockproxy.service",
    ],
    mode = "0644",
    ownername = "root.root",
    package_dir = "etc/systemd/system",
    tags = ["manual"],
)

pkg_tar(
    name = "proxy_tar",
    ownername = "root.root",
    tags = ["manual"],
    deps = [
        ":proxy_bin",
        ":proxy_service",
    ],
)

pkg_tar(
    name = "proxy_al2023_tar",
    ownername = "root.root",
    tags = ["manual"],
    deps = [
        ":proxy_bin_al2023",
        ":proxy_service",
    ],
)

container_image(
    name = "build_container",
    base = "@amazonlinux_2//image",
)

# Mounts the source code into an AmazonLinux2 docker container and performs a
# build of just the proxy dependencies. Used to ensure compatability with Amazon
# Linux 2's host libraries (specifically glibc).
genrule(
    name = "reproducible_proxy_outputs",
    srcs = [
        "//:source_code.tar",
        ":build_container.tar",
        "@bazelisk//file:bazelisk",
    ],
    outs = [
        "libproxy_preload.so",
        "proxify",
        "proxy",
        "socket_vendor",
    ],
    cmd = """
    export OUTPUT_DIR=`mktemp -d -t PROXY_BUILD_OUTPUTS_XXXXX`
    docker load < $(location :build_container.tar)
    docker run --rm \
      -v `realpath $(location //:source_code.tar)`:/source_code.tar \
      -v `realpath $(location @bazelisk//file:bazelisk)`:/bin/bazel \
      -v `realpath "$${OUTPUT_DIR}"`:/output \
      bazel/cc/proxy:build_container \
      bash -c '
        set -eux
        yum install glibc-devel-2.26-63.amzn2 glibc-headers-2.26-63.amzn2 gcc-7.3.1-15.amzn2 gcc-c++-7.3.1-15.amzn2 tar git -y > /dev/null
        tar xf /source_code.tar -C /
        ls
        cd /scp
        # Remove clang flags
        sed -i '/fbracket-depth=512/d' .bazelrc
        bazel build \
          //cc/proxy/src:proxify \
          //cc/proxy/src:proxy \
          //cc/proxy/src:proxy_preload \
          //cc/proxy/src:socket_vendor
        cp $$(bazel info bazel-bin)/cc/proxy/src/{libproxy_preload.so,proxify,proxy,socket_vendor} /output
      '
    cp "$${OUTPUT_DIR}/libproxy_preload.so" $(location libproxy_preload.so)
    cp "$${OUTPUT_DIR}/proxify" $(location proxify)
    cp "$${OUTPUT_DIR}/proxy" $(location proxy)
    cp "$${OUTPUT_DIR}/socket_vendor" $(location socket_vendor)
    rm -r "$${OUTPUT_DIR}"
    """,
    tags = ["manual"],
)

pkg_tar2rpm(
    name = "vsockproxy_rpm",
    data = ":proxy_tar",
    pkg_name = "vsockproxy",
    # Signal to systemd that the service files changed on install.
    postin = """
    systemctl daemon-reload
    """,
    # Signal to systemd that the service files changed on uninstall.
    postun = """
    systemctl daemon-reload
    """,
    # Stop service before uninstall starts.
    preun = """
    systemctl stop vsockproxy.service
    systemctl disable vsockproxy.service
    """,
    release = "0",
    tags = ["manual"],
    use_dir_allowlist = True,
    version = "0.0.1",
)

pkg_tar2rpm(
    name = "vsockproxy_rpm_al2023",
    data = ":proxy_al2023_tar",
    pkg_name = "vsockproxy",
    # Signal to systemd that the service files changed on install.
    postin = """
    systemctl daemon-reload
    """,
    # Signal to systemd that the service files changed on uninstall.
    postun = """
    systemctl daemon-reload
    """,
    # Stop service before uninstall starts.
    preun = """
    systemctl stop vsockproxy.service
    systemctl disable vsockproxy.service
    """,
    release = "0",
    tags = ["manual"],
    use_dir_allowlist = True,
    version = "0.0.1",
)
