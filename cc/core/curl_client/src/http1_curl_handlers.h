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
#pragma once

#include "core/interface/http_types.h"
#include "public/core/interface/execution_result.h"

namespace google::scp::core {

/**
 * @brief Interprets contents as a char* of length byte_size * num_bytes and
 * writes them into output which should be a BytesBuffer*
 *
 * https://curl.se/libcurl/c/CURLOPT_WRITEFUNCTION.html
 *
 * @param contents The contents to write to the output
 * @param byte_size The size of each member (char in this case; always 1)
 * @param num_bytes How many members (chars) are in contents
 * @param output A BytesBuffer* to write contents into
 * @return size_t The amount of data written
 */
size_t ResponsePayloadHandler(char* contents, size_t byte_size,
                              size_t num_bytes, void* output);

/**
 * @brief Interprets output as a HttpHeaders*. Parses contents into a
 * colon-separated header string and stores the key-value pair in output.
 * This is called for each header individually - including the blank line
 * header.
 *
 * https://curl.se/libcurl/c/CURLOPT_HEADERFUNCTION.html
 *
 * @param contents A header acquired from the response - not null terminated
 * @param byte_size The size of each member (char in this case; always 1)
 * @param num_bytes How many members (chars) are in contents
 * @param output The header map to place this header into.
 * @return size_t The amount of characters processed.
 */
size_t ResponseHeaderHandler(char* contents, size_t byte_size, size_t num_bytes,
                             void* output);

/**
 * @brief Read the request userdata to contents.
 *
 * https://curl.se/libcurl/c/CURLOPT_READFUNCTION.html
 *
 * @param contents The output array to copy userdata into.
 * @param byte_size The size of each member (char in this case; always 1)
 * @param num_bytes How many members (chars) are in contents
 * @param userdata BytesBuffer* of the data to copy into contents
 * @return size_t The amount of characters processed.
 */
size_t RequestReadHandler(char* contents, size_t byte_size, size_t num_bytes,
                          void* userdata);

}  // namespace google::scp::core
