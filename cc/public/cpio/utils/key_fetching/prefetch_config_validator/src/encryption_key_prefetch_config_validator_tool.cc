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

#include <fstream>
#include <iostream>

#include "cc/core/test/utils/logging_utils.h"
#include "public/cpio/utils/dual_writing_metric_client/mock/dual_writing_metric_client_mock.h"

#include "encryption_key_prefetch_config_validator.h"

using google::scp::core::test::TestLoggingUtils;
using google::scp::cpio::DualWritingMetricClientMock;
using std::cout;
using std::endl;
using std::ifstream;
using std::stringstream;
using testing::NiceMock;

namespace google::scp::cpio {

int PerformPrefetchValidation(int argc, char* argv[]) {
  if (argc != 2) {
    cout << "Expected 1 arg but got " << argc - 1 << endl;
    return EXIT_FAILURE;
  }
  ifstream input_file(argv[1]);
  if (!input_file.is_open()) {
    cout << "Failed to open file " << argv[1] << endl;
    return EXIT_FAILURE;
  }
  stringstream ss;
  ss << input_file.rdbuf();
  TestLoggingUtils::EnableLogOutputToConsole();
  NiceMock<DualWritingMetricClientMock> metric_client;
  // Already pass in hmac_level_key_namespaces from terraform, so we can enable
  // it in the validation tool.
  EncryptionKeyPrefetchConfigValidator validator(metric_client,
                                                 /*metric_namespace=*/"tool");

  auto config_map_or = validator.ParseValidateAndBuildMap(ss.str());

  if (!config_map_or.Successful()) {
    cout << "Failed to parse and validate prefetch config: "
         << config_map_or.result().status_code << endl;
    return EXIT_FAILURE;
  }

  cout << "Successfully parsed and validated prefetch config for "
       << config_map_or->size() << " namespaces." << endl;

  cout << "Validation succeeded :)" << endl;
  return EXIT_SUCCESS;
}

}  // namespace google::scp::cpio

int main(int argc, char* argv[]) {
  return google::scp::cpio::PerformPrefetchValidation(argc, argv);
}
