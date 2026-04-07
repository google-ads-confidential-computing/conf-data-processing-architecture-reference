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

shopt -s nullglob

# Prints out a default value for a variable, given its type.
create_var_default() {
  local varname="$1"
  local vartype="$2"
  case "$vartype" in
    string)
      echo "$varname = \"\""
      ;;
    number)
      echo "$varname = 0"
      ;;
    bool)
      echo "$varname = false"
      ;;
    list)
      ;& # fallthrough
    set)
      echo "$varname = []"
      ;;
    map)
      ;& # fallthrough
    object)
      echo "$varname = {}"
      ;;
    any)
      echo "$varname = \"any\""
      ;;
  esac
}

# Appends a convenient variables {} section to $test_filename given the
# variables defined in any files ending in variables.tf
append_variables_section() {
  local test_filename="$1"
  local repo_top_level_dir=$(git rev-parse --show-toplevel)
  local parsing_script_file="$repo_top_level_dir/tools/tftesting/variables_parsing.sh"

  echo "variables {" >> "$test_filename"
  for file in *variables.tf; do
    variable_names_and_types=$($parsing_script_file $file)
    while IFS= read -r line; do
      local name=$(echo "$line" | awk '{print $1}')
      local type=$(echo "$line" | awk '{print $2}')
      echo "  $(create_var_default "$name" "$type")" >> "$test_filename"
    done <<< "$variable_names_and_types"
  done
  echo "}" >> "$test_filename"
}

create_template_test_file() {
  local filename="$1"
  if [ -f "$filename" ]; then
    echo "Test file already exists, skipping"
    return 0
  fi
  repo_top_level_dir=$(git rev-parse --show-toplevel)
  cp "$repo_top_level_dir/tools/tftesting/tf_test_template.tftest.hcl" "$filename"

  # Modify <MOCK_RELATIVE_PATH> to be the actual path relative to its directory.
  local mocks_path=$(realpath -s --relative-to="$(pwd)" "$repo_top_level_dir/tools/tftesting/tfmocks/")
  # We use '~' as the delimiter for sed since $mocks_path will have '/' in it.
  sed -i -e "s~<MOCK_RELATIVE_PATH>~$mocks_path~g" "$filename"

  append_variables_section "$filename"
  echo "# All run blocks should have \"command = plan\"." >> "$filename"
  echo "# Take great care when writing tests with \"command = apply\"." >> "$filename"
}
