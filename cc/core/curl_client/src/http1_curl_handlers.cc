/*
 * Copyright 2022 Google LLC
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
#include "http1_curl_handlers.h"

#include <cstring>
#include <memory>
#include <regex>
#include <string>
#include <vector>

#include "core/common/global_logger/src/global_logger.h"
#include "core/common/uuid/src/uuid.h"
#include "core/interface/http_types.h"
#include "public/core/interface/execution_result.h"

#include "error_codes.h"

namespace google::scp::core {
namespace {

using google::scp::core::common::kZeroUuid;

constexpr char kHttp1CurlWrapper[] = "Http1CurlWrapper";

}  // namespace

size_t ResponsePayloadHandler(char* contents, size_t byte_size,
                              size_t num_bytes, void* output) {
  BytesBuffer* output_buffer = static_cast<BytesBuffer*>(output);
  size_t chunk_size = byte_size * num_bytes;
  if (chunk_size == 0) {
    return 0;
  }

  // Initialize the vector on the first chunk only. This handler may be called
  // multiple times
  if (output_buffer->bytes == nullptr) {
    output_buffer->bytes = std::make_shared<std::vector<Byte>>();
  }

  size_t current_size = output_buffer->bytes->size();
  size_t new_size = current_size + chunk_size;
  output_buffer->bytes->resize(new_size);
  std::memcpy(output_buffer->bytes->data() + current_size, contents,
              chunk_size);

  output_buffer->length = new_size;
  output_buffer->capacity = new_size;

  return chunk_size;
}

size_t ResponseHeaderHandler(char* contents, size_t byte_size, size_t num_bytes,
                             void* output) {
  HttpHeaders* header_map = static_cast<HttpHeaders*>(output);
  size_t contents_size = byte_size * num_bytes;
  if (contents_size <= 2) {
    // Empty field line (i.e. "\r\n") - skip.
    return contents_size;
  }
  std::string contents_str(contents, contents_size);
  if (std::regex r("HTTP.*[0-9]{3}"); std::regex_search(contents_str, r)) {
    // The header is just the HTTP response code.
    return contents_size;
  }

  // The index of the carriage return character '\r'.
  size_t contents_end = contents_str.find('\r');
  // The index of the colon character ':'.
  size_t colon_index = contents_str.find(':');

  if (colon_index > contents_end) {
    SCP_ERROR(
        kHttp1CurlWrapper, kZeroUuid,
        FailureExecutionResult(errors::SC_CURL_CLIENT_BAD_HEADER_RECEIVED),
        "The ':' was found after the '\r' in the header: \"%s\"",
        contents_str.c_str());
    return contents_size;
  }
  if (colon_index == std::string::npos) {
    SCP_ERROR(
        kHttp1CurlWrapper, kZeroUuid,
        FailureExecutionResult(errors::SC_CURL_CLIENT_BAD_HEADER_RECEIVED),
        "No ':' was found in the header: \"%s\"", contents_str.c_str());
    return contents_size;
  }
  bool has_space_after_colon = contents_str[colon_index + 1] == ' ';

  // Copy the position after the colon until the end.
  size_t value_index = colon_index + 1;
  if (has_space_after_colon) value_index++;

  header_map->insert(
      {contents_str.substr(0, colon_index),
       contents_str.substr(value_index, contents_end - value_index)});
  return contents_size;
}

size_t RequestReadHandler(char* contents, size_t byte_size, size_t num_bytes,
                          void* userdata) {
  BytesBuffer* input_buffer = static_cast<BytesBuffer*>(userdata);

  int64_t bytes_to_read = byte_size * num_bytes;
  if (bytes_to_read > input_buffer->length) {
    bytes_to_read = input_buffer->length;
  }

  if (bytes_to_read) {
    memcpy(contents, input_buffer->bytes->data(), bytes_to_read);
  }
  return bytes_to_read;
}

}  // namespace google::scp::core
