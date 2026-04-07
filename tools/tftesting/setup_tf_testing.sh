#!/bin/bash
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

set -eEu -o pipefail

# Script to set up a directory for TF testing.

repo_top_level_dir=$(git rev-parse --show-toplevel)
source "$repo_top_level_dir"/tools/tftesting/tf_test_helpers.sh

if [ "$#" -lt 1 ]; then
  info_msg=$(cat <<-END
    Must provide all input variables. Switches are:\n
      --dir_to_setup=<value>\n
      --test_filename=<value> (default is <name of directory>.tftest.hcl)\n
END
)
  echo -e $info_msg
  exit 1
fi

while [ $# -gt 0 ]; do
  case "$1" in
    --dir_to_setup=*)
      dir_to_setup="${1#*=}"
      ;;
    --test_filename=*)
      test_filename="${1#*=}"
      ;;
    *)
      printf "***************************\n"
      printf "* Error: Invalid argument.*\n"
      printf "***************************\n"
      exit 1
  esac
  shift
done

if [[ ! -d "$dir_to_setup" ]]; then
  err_msg=$(cat <<-END
  The directory "$dir_to_setup" does not exist.
END
)
  echo -e "$err_msg"
  exit 1
fi

cd "$dir_to_setup"

if [[ ! -v test_filename ]]; then
  test_filename="${PWD##*/}.tftest.hcl"
fi

create_template_test_file "$test_filename"

echo "Done :)"