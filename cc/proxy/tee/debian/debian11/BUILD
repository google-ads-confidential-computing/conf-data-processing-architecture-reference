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

load("@io_bazel_rules_docker//container:container.bzl", "container_image")

package(
    default_visibility = ["//visibility:public"],
)

container_image(
    name = "build_container_debian11",
    base = "@debian_11//image",
    tags = ["manual"],
)

genrule(
    name = "reproducible_proxy_outputs_debian11",
    srcs = [
        "//:source_code.tar",
        ":build_container_debian11.tar",
        "@bazelisk//file:bazelisk",
    ],
    outs = [
        "libproxy_preload.so",
        "proxify",
        "socket_vendor",
    ],
    cmd = """
    export OUTPUT_DIR=`mktemp -d -t PROXY_BUILD_OUTPUTS_XXXXX`

    cp `realpath $(location //:source_code.tar)` ./
    cp `realpath $(location @bazelisk//file:bazelisk)` ./

    docker load < $(location :build_container_debian11.tar)

    rand_container_id=proxy-debian-build-container-`head /dev/urandom | tr -dc A-Za-z0-9 | head -c 32`
    echo "Starting container $${rand_container_id}"
    # Make sure container is killed if build fails
    trap "docker rm -f $${rand_container_id} > /dev/null 2> /dev/null" ERR

    docker run -d --name $${rand_container_id} \
      bazel/cc/proxy/tee/debian/debian11:build_container_debian11 \
      bash -c '
        tail -f /dev/null
      '

    # Copy artifacts into container and make sure container user has access to them
    docker cp `realpath source_code.tar` $${rand_container_id}:/source_code.tar
    docker cp `realpath bazelisk` $${rand_container_id}:/bin/bazel

    # Build proxy and copy proxy artifacts to output dir
    docker exec \
      $${rand_container_id}  \
      bash -c '
        set -ex
        apt-get update && apt-get install -y ca-certificates clang-13 git tar
        export CC="/usr/bin/clang-13"
        export CXX="/usr/bin/clang++-13"
        tar xf /source_code.tar -C /tmp
        cd /tmp/scp
        mkdir /tmp/proxy_build_output
        bazel build \
          //cc/proxy/src:proxify \
          //cc/proxy/src:proxy_preload \
          //cc/proxy/src:socket_vendor
        cp $$(bazel info bazel-bin)/cc/proxy/src/{libproxy_preload.so,proxify,socket_vendor} /tmp/proxy_build_output
      '

    docker cp $${rand_container_id}:/tmp/proxy_build_output/libproxy_preload.so $${OUTPUT_DIR}
    docker cp $${rand_container_id}:/tmp/proxy_build_output/proxify $${OUTPUT_DIR}
    docker cp $${rand_container_id}:/tmp/proxy_build_output/socket_vendor $${OUTPUT_DIR}

    cp "$${OUTPUT_DIR}/libproxy_preload.so" $(location libproxy_preload.so)
    cp "$${OUTPUT_DIR}/proxify" $(location proxify)
    cp "$${OUTPUT_DIR}/socket_vendor" $(location socket_vendor)
    rm -r "$${OUTPUT_DIR}"
    docker rm -f $${rand_container_id} > /dev/null 2> /dev/null
    """,
    tags = ["manual"],
)
