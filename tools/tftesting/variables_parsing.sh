#!/usr/bin/awk -f
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

# Parses the input Terraform variables file.
# For each variable that does not have a default declared,
# prints out the name of the variable and its type.
# For variables without a type, does not print them.

BEGIN {
  # Set the Record Separator to a newline immediately followed by a '}'
  # This is important so that we ignore '}' inside of "variable" blocks
  RS = "\n}"
}



/[[:space:]]*variable[[:space:]]"/ {
  match($0, /variable[[:space:]]*"([^"]+)"/, name_arr)
  var_name = name_arr[1]

  if (var_name == "") next

  # This variable has a default, we can omit it from the variables list.
  # It can be manually added if it is necessary.
  if ($0 ~ /[[:space:]]*default[[:space:]]*=/) next

  # Check for type within this block
  if ($0 ~ /[[:space:]]*type[[:space:]]*=[[:space:]]*([a-z]+)/) {
    match($0, /type[[:space:]]*=[[:space:]]*([a-z]+)/, type_arr)
    var_type = type_arr[1]
    print var_name, var_type
  } else {
    # Variables with no type declared are considered "any"
    print var_name, "any"
    next
  }
}