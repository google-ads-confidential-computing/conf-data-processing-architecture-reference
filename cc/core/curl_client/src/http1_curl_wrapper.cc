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
#include "http1_curl_wrapper.h"

#include <algorithm>
#include <memory>
#include <regex>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/str_cat.h"
#include "absl/strings/str_format.h"
#include "absl/strings/str_split.h"
#include "core/common/global_logger/src/global_logger.h"
#include "core/common/uuid/src/uuid.h"
#include "core/utils/src/http.h"
#include "public/core/interface/execution_result_macros.h"
#include "public/core/interface/execution_result_or_macros.h"

#include "error_codes.h"
#include "http1_curl_handlers.h"

using google::scp::core::common::kZeroUuid;
using google::scp::core::utils::GetEscapedUriWithQuery;
using std::make_shared;
using std::make_unique;
using std::move;
using std::regex;
using std::regex_search;
using std::shared_ptr;
using std::smatch;
using std::stoi;
using std::string;
using std::unique_ptr;
using std::vector;

namespace google::scp::core {

namespace {

constexpr int64_t kTrueAsLong = 1L;
constexpr int64_t kCurlOptTimeout = 60L;
constexpr char kHttp1CurlWrapper[] = "Http1CurlWrapper";

ExecutionResult GetExecutionResultFromCurlError(const string& err_buffer) {
  regex error_code_regex("([0-9]{3})");
  smatch http_code_match;
  int http_code;
  if (regex_search(err_buffer, http_code_match, error_code_regex)) {
    try {
      http_code = stoi(http_code_match.str(0));
    } catch (...) {
      auto result = RetryExecutionResult(
          errors::SC_CURL_CLIENT_REQUEST_BAD_REGEX_PARSING);
      SCP_ERROR(kHttp1CurlWrapper, kZeroUuid, result,
                "Could not parse HTTP status code to integer: %s",
                http_code_match.str(0).c_str());
      return result;
    }
  } else {
    auto result =
        RetryExecutionResult(errors::SC_CURL_CLIENT_REQUEST_BAD_REGEX_PARSING);
    SCP_ERROR(kHttp1CurlWrapper, kZeroUuid, result,
              "Could not find HTTP status in HTTP error message: %s",
              err_buffer.c_str());
    return result;
  }
  if (http_code < 400) {
    return SuccessExecutionResult();
  }
  switch (http_code) {
    case 400:
      return FailureExecutionResult(errors::SC_CURL_CLIENT_REQUEST_FAILED);
    case 401:
      return FailureExecutionResult(
          errors::SC_CURL_CLIENT_REQUEST_UNAUTHORIZED);
    case 403:
      return FailureExecutionResult(errors::SC_CURL_CLIENT_REQUEST_FORBIDDEN);
    case 404:
      return FailureExecutionResult(errors::SC_CURL_CLIENT_REQUEST_NOT_FOUND);
    case 408:
      return FailureExecutionResult(errors::SC_CURL_CLIENT_REQUEST_TIMEOUT);
    case 409:
      return FailureExecutionResult(errors::SC_CURL_CLIENT_REQUEST_CONFLICT);
    case 412:
      return FailureExecutionResult(
          errors::SC_CURL_CLIENT_REQUEST_PRECONDITION_FAILED);
    case 429:
      return FailureExecutionResult(
          errors::SC_CURL_CLIENT_REQUEST_TOO_MANY_REQUESTS);
    case 500:
      return RetryExecutionResult(errors::SC_CURL_CLIENT_REQUEST_SERVER_ERROR);
    case 501:
      return RetryExecutionResult(
          errors::SC_CURL_CLIENT_REQUEST_NOT_IMPLEMENTED);
    case 502:
      return FailureExecutionResult(errors::SC_CURL_CLIENT_REQUEST_BAD_GATEWAY);
    case 503:
      return RetryExecutionResult(
          errors::SC_CURL_CLIENT_REQUEST_SERVICE_UNAVAILABLE);
    default:
      SCP_WARNING(kHttp1CurlWrapper, kZeroUuid,
                  "Found other HTTP status code: %d", http_code);
      return RetryExecutionResult(
          errors::SC_CURL_CLIENT_REQUEST_OTHER_HTTP_ERROR);
  }
}

}  // namespace

ExecutionResultOr<shared_ptr<Http1CurlWrapper>>
Http1CurlWrapper::MakeWrapper() {
  CURL* curl = curl_easy_init();
  if (!curl) {
    auto result = RetryExecutionResult(errors::SC_CURL_CLIENT_CURL_INIT_ERROR);
    SCP_ERROR(kHttp1CurlWrapper, kZeroUuid, result, "failed to make wrapper");
    return result;
  }
  return make_shared<Http1CurlWrapper>(curl);
}

ExecutionResultOr<unique_ptr<curl_slist, CurlListDeleter>>
Http1CurlWrapper::AddHeadersToRequest(
    const std::shared_ptr<HttpHeaders>& headers) {
  if (!headers || headers->empty()) {
    return SuccessExecutionResult();
  }
  curl_slist* header_list = nullptr;
  for (const auto& [key, value] : *headers) {
    string header = absl::StrCat(key, ": ", value);
    auto* result = curl_slist_append(header_list, header.c_str());
    if (result == nullptr) {
      curl_slist_free_all(header_list);
      return RetryExecutionResult(errors::SC_CURL_CLIENT_CURL_HEADER_ADD_ERROR);
    }
    header_list = result;
  }
  curl_easy_setopt(curl_.get(), CURLOPT_HTTPHEADER, header_list);
  // Wrap the returned raw pointer in a unique_ptr to prevent memory leaks.
  return unique_ptr<curl_slist, CurlListDeleter>(header_list);
}

void Http1CurlWrapper::SetUpResponseHeaderHandler(
    HttpHeaders* returned_header_destination) {
  curl_easy_setopt(curl_.get(), CURLOPT_HEADERFUNCTION, ResponseHeaderHandler);
  curl_easy_setopt(curl_.get(), CURLOPT_HEADERDATA,
                   returned_header_destination);
}

void Http1CurlWrapper::SetUpPostData(const BytesBuffer& body) {
  if (body.length == 0) {
    return;
  }
  curl_easy_setopt(curl_.get(), CURLOPT_POSTFIELDS, body.bytes->data());
  // This method of upload supports up to 2GB upload data.
  // See https://curl.se/libcurl/c/CURLOPT_POSTFIELDSIZE_LARGE.html for larger
  // uploads.
  curl_easy_setopt(curl_.get(), CURLOPT_POSTFIELDSIZE, body.length);
}

void Http1CurlWrapper::SetUpPutData(const BytesBuffer& body) {
  curl_easy_setopt(curl_.get(), CURLOPT_READFUNCTION, RequestReadHandler);

  curl_easy_setopt(curl_.get(), CURLOPT_READDATA, &body);

  curl_easy_setopt(curl_.get(), CURLOPT_INFILESIZE_LARGE, body.length);
}

// Performs the request. Logs any error that occurs and returns the status
// of the request. If the request was successful, response_ will now hold the
// body of the response.
ExecutionResultOr<HttpResponse> Http1CurlWrapper::PerformRequest(
    const HttpRequest& request) {
  if (!request.path || request.path->empty()) {
    return FailureExecutionResult(errors::SC_CURL_CLIENT_NO_PATH_SUPPLIED);
  }
  CURLoption option;
  switch (request.method) {
    case HttpMethod::GET:
      option = CURLOPT_HTTPGET;
      break;
    case HttpMethod::POST:
      option = CURLOPT_POST;
      SetUpPostData(request.body);
      break;
    case HttpMethod::PUT:
      option = CURLOPT_UPLOAD;
      SetUpPutData(request.body);
      break;
    case HttpMethod::UNKNOWN:
    default:
      return FailureExecutionResult(errors::SC_CURL_CLIENT_UNSUPPORTED_METHOD);
  }
  curl_easy_setopt(curl_.get(), option, kTrueAsLong);

  auto header_list = AddHeadersToRequest(request.headers);
  RETURN_IF_FAILURE(header_list.result());
  // Build the URL with the escaped path.
  auto uri = GetEscapedUriWithQuery(request);
  RETURN_IF_FAILURE(uri.result());

  curl_easy_setopt(curl_.get(), CURLOPT_URL, uri->c_str());
  // Use define USE_UNIX_SOCKETS in configuration to support
  // CURLOPT_UNIX_SOCKET_PATH.
  if (request.unix_socket_path && !request.unix_socket_path->empty()) {
    auto er = curl_easy_setopt(curl_.get(), CURLOPT_UNIX_SOCKET_PATH,
                               request.unix_socket_path->c_str());
    if (er != CURLE_OK) {
      curl_version_info_data* ver;
      ver = curl_version_info(CURLVERSION_NOW);
      SCP_ERROR(kHttp1CurlWrapper, kZeroUuid,
                FailureExecutionResult(SC_UNKNOWN),
                "Failed to set CURLOPT_UNIX_SOCKET_PATH error code: %d. "
                "libcurl/%s. Unix Socket Support: %s",
                er, ver->version,
                (ver->features & CURL_VERSION_UNIX_SOCKETS) ? "Yes" : "No");
    }
  }

  HttpResponse response;
  response.headers = make_shared<HttpHeaders>();
  SetUpResponseHeaderHandler(response.headers.get());

  // Add the handler indicating what to do with the returned HTTP response.
  curl_easy_setopt(curl_.get(), CURLOPT_WRITEFUNCTION, ResponsePayloadHandler);
  curl_easy_setopt(curl_.get(), CURLOPT_WRITEDATA, &response.body);
  curl_easy_setopt(curl_.get(), CURLOPT_TIMEOUT, kCurlOptTimeout);
  curl_easy_setopt(curl_.get(), CURLOPT_FAILONERROR, kTrueAsLong);
  // Create a buffer to place any error messages in.
  string err_str(CURL_ERROR_SIZE, '\0');
  curl_easy_setopt(curl_.get(), CURLOPT_ERRORBUFFER, err_str.data());

  // Execute the request.
  CURLcode perform_res = curl_easy_perform(curl_.get());
  if (perform_res != CURLE_OK) {
    auto result = GetExecutionResultFromCurlError(err_str);
    if (err_str.empty()) err_str = "<empty>";
    SCP_ERROR(kHttp1CurlWrapper, kZeroUuid, result,
              "CURL HTTP request failed with error code: %s, message: %s",
              curl_easy_strerror(perform_res), err_str.c_str());
    return result;
  }
  response.code = errors::HttpStatusCode::OK;
  return response;
}

Http1CurlWrapper::Http1CurlWrapper(CURL* curl) {
  // Wrap the returned raw pointer in a unique_ptr to prevent memory leaks.
  curl_ = unique_ptr<CURL, CurlHandleDeleter>(curl);
}

ExecutionResultOr<shared_ptr<Http1CurlWrapper>>
Http1CurlWrapperProvider::MakeWrapper() {
  return Http1CurlWrapper::MakeWrapper();
}

}  // namespace google::scp::core
