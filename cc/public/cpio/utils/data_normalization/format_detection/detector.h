/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

namespace google::scp::cpio {

enum class UserDataFormat {
  // The user data is an encoded hash string
  kHashed = 0,

  // The user data is not hashed
  kUnhashed = 1,

  // The user data format cannot be determined, but can be treat as unhashed for
  // practical purposes.
  kUnknown = 2,
};

}  // namespace google::scp::cpio
