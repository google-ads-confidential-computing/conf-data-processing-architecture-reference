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

# Mounts the source code into an AmazonLinux2023 docker container and performs a
# build of just the proxy dependencies. Used to ensure compatability with Amazon
# Linux 2's host libraries (specifically glibc).

load("@io_bazel_rules_docker//container:container.bzl", "container_image")

package(
    default_visibility = ["//visibility:public"],
)

container_image(
    name = "build_container_al2023",
    base = "@amazonlinux_2023//image",
)

genrule(
    name = "reproducible_proxy_outputs_al2023",
    srcs = [
        "//:source_code.tar",
        ":build_container_al2023.tar",
        "@bazelisk//file:bazelisk",
    ],
    outs = [
        "proxy",
    ],
    cmd = """
    cp `realpath $(location //:source_code.tar)` ./
    cp `realpath $(location @bazelisk//file:bazelisk)` ./

    export OUTPUT_DIR=`mktemp -d -t PROXY_BUILD_OUTPUTS_XXXXX`
    docker load < $(location :build_container_al2023.tar)

    rand_container_id=proxy-build-container-`head /dev/urandom | tr -dc A-Za-z0-9 | head -c 32`
    echo "Starting container $${rand_container_id}"
    # Make sure container is killed if build fails
    trap "docker rm -f $${rand_container_id} > /dev/null 2> /dev/null" ERR

    docker run -d --name $${rand_container_id} \
      bazel/cc/proxy/parent_instance/aws/al2023:build_container_al2023 \
      bash -c '
        tail -f /dev/null
      '
    # Copy artifacts into container and make sure container user has access to them
    docker cp `realpath source_code.tar` $${rand_container_id}:/source_code.tar
    docker cp `realpath bazelisk` $${rand_container_id}:/bin/bazel

    # Run dependency install as root within container and change file ownership to bazel sandbox user
    docker exec $${rand_container_id} bash -c '
        set -eux
        dnf install glibc-devel-2.34-52.amzn2023.0.3 clang-15.0.6-3.amzn2023.0.2 tar git -y > /dev/null
      '
    # Build proxy and copy proxy artifact to output dir
    docker exec \
      $${rand_container_id}  \
      bash -c '
        set -eux
        tar xf /source_code.tar -C /tmp
        cd /tmp/scp
        export CXX=/usr/bin/clang++-15
        export CC=/usr/bin/clang-15
        mkdir /tmp/proxy_build_output
        bazel build \
          //cc/proxy/src:proxy
        cp $$(bazel info bazel-bin)/cc/proxy/src/proxy /tmp/proxy_build_output
      '
    docker cp $${rand_container_id}:/tmp/proxy_build_output/proxy $${OUTPUT_DIR}

    cp "$${OUTPUT_DIR}/proxy" $(location proxy)
    rm -r "$${OUTPUT_DIR}"
    docker rm -f $${rand_container_id} > /dev/null 2> /dev/null
    """,
    tags = ["manual"],
)
