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

load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_push")
load("@io_bazel_rules_docker//docker/package_managers:download_pkgs.bzl", "download_pkgs")
load("@io_bazel_rules_docker//docker/package_managers:install_pkgs.bzl", "install_pkgs")
load("@io_bazel_rules_docker//docker/util:run.bzl", "container_run_and_commit_layer", "container_run_and_extract")

def snapshot_runtime_image(
        *,
        name,
        version):
    ############# Prepare Debian11 image starts ###############
    download_pkgs_name = "%s_download_pkgs" % name
    download_pkgs(
        name = download_pkgs_name,
        image_tar = "@debian_11//image",
        packages = [
            "rsyslog",
        ],
        tags = ["manual"],
    )

    install_pkgs_name = "%s_install_pkgs" % name
    install_pkgs(
        name = install_pkgs_name,
        image_tar = "@debian_11//image",
        installables_tar = ":%s.tar" % download_pkgs_name,
        output_image_name = install_pkgs_name,
        tags = ["manual"],
    )

    rsyslog_extract_commands = [
        "mkdir -p /extracted_files/usr/sbin/",
        "mkdir -p /extracted_files/etc/",
        "mkdir -p /extracted_files/lib/x86_64-linux-gnu/",
        "mkdir -p /extracted_files/usr/lib/x86_64-linux-gnu/",
        "mkdir -p /extracted_files/usr/lib/x86_64-linux-gnu/rsyslog/",
        "cp /usr/sbin/rsyslogd /extracted_files/usr/sbin/",
        "cp /etc/rsyslog.conf /extracted_files/etc/",
        "cp /lib/x86_64-linux-gnu/libz.so.1 /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/$(readlink /lib/x86_64-linux-gnu/libz.so.1) /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/libpthread.so.0 /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/$(readlink /lib/x86_64-linux-gnu/libpthread.so.0) /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/libdl.so.2 /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/$(readlink /lib/x86_64-linux-gnu/libdl.so.2) /extracted_files/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/libestr.so.0 /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/$(readlink /usr/lib/x86_64-linux-gnu/libestr.so.0) /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/libfastjson.so.4 /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/$(readlink /usr/lib/x86_64-linux-gnu/libfastjson.so.4) /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/libsystemd.so.0 /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/$(readlink /usr/lib/x86_64-linux-gnu/libsystemd.so.0) /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/libuuid.so.1 /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/$(readlink /usr/lib/x86_64-linux-gnu/libuuid.so.1) /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/libc.so.6 /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/$(readlink /lib/x86_64-linux-gnu/libc.so.6) /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/librt.so.1 /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/$(readlink /lib/x86_64-linux-gnu/librt.so.1) /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/liblzma.so.5 /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/$(readlink /lib/x86_64-linux-gnu/liblzma.so.5) /extracted_files/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/libzstd.so.1 /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/$(readlink /usr/lib/x86_64-linux-gnu/libzstd.so.1) /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/liblz4.so.1 /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/$(readlink /usr/lib/x86_64-linux-gnu/liblz4.so.1) /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/libgcrypt.so.20 /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/$(readlink /usr/lib/x86_64-linux-gnu/libgcrypt.so.20) /extracted_files/usr/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/libgpg-error.so.0 /extracted_files/lib/x86_64-linux-gnu/",
        "cp /lib/x86_64-linux-gnu/$(readlink /lib/x86_64-linux-gnu/libgpg-error.so.0) /extracted_files/lib/x86_64-linux-gnu/",
        "cp /usr/lib/x86_64-linux-gnu/rsyslog/* /extracted_files/usr/lib/x86_64-linux-gnu/rsyslog/",
        "cd /extracted_files && tar -zcvf rsyslog_and_dependencies.tar * && cp rsyslog_and_dependencies.tar /",
    ]

    # Gather rsyslog and all the dependencies it needs to run into a tar so that it can be copied into the runtime container
    rsyslog_extract_name = "%s_rsyslog_extract" % name
    container_run_and_extract(
        name = rsyslog_extract_name,
        commands = rsyslog_extract_commands,
        extract_file = "/rsyslog_and_dependencies.tar",
        image = ":%s.tar" % install_pkgs_name,
        tags = ["manual"],
    )
    ############# Prepare Debian11 image ends ###############

    sdk_runtime_image_files = [
        ":%s/rsyslog_and_dependencies.tar" % rsyslog_extract_name,
    ]

    # Base image to extract the dependencies in
    runtime_base_image = "%s_runtime_base_image" % name
    container_image(
        name = runtime_base_image,
        base = "@java_debug_runtime//image",
        files = sdk_runtime_image_files,
        tags = ["manual"],
    )

    runtime_dependencies_layer_commands = [
        "addgroup adm",
        "ln -s /busybox/sh /bin/sh",
        "cd / && tar -xf rsyslog_and_dependencies.tar",
        "rm /rsyslog_and_dependencies.tar",
    ]
    runtime_dependencies_layer = "%s_runtime_dependencies_layer" % name
    container_run_and_commit_layer(
        name = runtime_dependencies_layer,
        commands = runtime_dependencies_layer_commands,
        docker_run_flags = [
            "--entrypoint=''",
            "--user root",
        ],
        image = "%s.tar" % runtime_base_image,
        tags = ["manual"],
    )

    container_image(
        name = "linux_debian_11_runtime",
        base = "@java_base//image",
        layers = [":%s" % runtime_dependencies_layer],
        tags = ["manual"],
    )

    container_push(
        name = name,
        format = "Docker",
        image = ":linux_debian_11_runtime",
        registry = "us-docker.pkg.dev",
        repository = "admcloud-scp/cc-runtime-snapshot/linux_debian_11_runtime",
        tag = version,
        tags = ["manual"],
    )
