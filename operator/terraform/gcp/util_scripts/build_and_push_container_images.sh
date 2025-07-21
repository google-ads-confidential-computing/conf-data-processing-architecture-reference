#!/usr/bin/env bash

# Copyright 2025 Google LLC
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

# Example usage:
#
# ./build_and_push_container_images.sh \
# --registry_url="us-docker.pkg.dev/<project>/<repo>" \
# --version_tag="<version>"

set -uo pipefail

if [[ "$#" -lt 1 || $1 == "help" || $1 == "-h" || $1 == "--help"  ]]; then
help_msg=$(cat <<-END
  \n
This script can be used to build and upload the operator cloud run container images to an artifact registry.\n\n

PRECONDITIONS:\n\n

1. gcloud must be available\n
2. bazel and access to the repo where the zipped JAR target is located\n
3. GCP project configured in gcloud, with valid credentials. [gcloud config set project]\n
\n\n
Example usage:\n\n

    ./build_and_push_container_images.sh \ \n
        \t --registry_url="us-docker.pkg.dev/admcloud-adtech1/docker-repo-dev" \ \n
        \t --version_tag="v0.000.1" \ \n
END
)
  echo -e $help_msg
  exit 1
fi

if [ "$#" -lt 2 ]; then
error_msg=$(cat <<-END
  \n[ERROR]\n
  Must provide all required inputs:\n
    --registry_url=<value>\n
    --version_tag=<value>\n
END
)
  echo -e $error_msg
  exit 1
fi

while [ $# -gt 0 ]; do
  case "$1" in
    --registry_url=*)
      registry_url="${1#*=}"
      ;;
    --version_tag=*)
      version_tag="${1#*=}"
      ;;
    *)
      printf "***************************\n"
      printf "* Error: Invalid argument.*\n"
      printf "***************************\n"
      exit 1
  esac
  shift
done

set -e

fe_handler_target="java/com/google/scp/operator/frontend/service/gcp:FrontendCloudFunctionRuntimeContainer"

bazel run //$fe_handler_target

fe_handler_tag="$registry_url/operator-fe-service:$version_tag"

docker tag bazel/$fe_handler_target $fe_handler_tag

docker push $fe_handler_tag
