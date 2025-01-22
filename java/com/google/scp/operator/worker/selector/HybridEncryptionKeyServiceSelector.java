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

package com.google.scp.operator.worker.selector;

import com.google.inject.Module;
import com.google.scp.operator.cpio.cryptoclient.aws.AwsEnclaveHybridEncryptionKeyServiceModule;
import com.google.scp.operator.cpio.cryptoclient.aws.AwsEnclaveMultiPartyHybridEncryptionKeyServiceModule;
import com.google.scp.operator.cpio.cryptoclient.aws.AwsKmsHybridEncryptionKeyServiceModule;
import com.google.scp.operator.cpio.cryptoclient.aws.AwsKmsMultiPartyHybridEncryptionKeyServiceModule;
import com.google.scp.operator.cpio.cryptoclient.gcp.GcpKmsHybridEncryptionKeyServiceModule;
import com.google.scp.operator.cpio.cryptoclient.gcp.GcpKmsMultiPartyHybridEncryptionKeyServiceModule;
import com.google.scp.operator.cpio.cryptoclient.local.LocalFileHybridEncryptionKeyServiceModule;

public enum HybridEncryptionKeyServiceSelector {
  LOCAL_FILE_DECRYPTION_KEY_SERVICE(new LocalFileHybridEncryptionKeyServiceModule()),
  // Non-enclave implementation.
  AWS_KMS_DECRYPTION_KEY_SERVICE(new AwsKmsHybridEncryptionKeyServiceModule()),
  // Enclave implementation.
  AWS_ENCLAVE_CLI_DECRYPTION_KEY_SERVICE(new AwsEnclaveHybridEncryptionKeyServiceModule()),
  // GCP single party implementation
  GCP_KMS_DECRYPTION_KEY_SERVICE(new GcpKmsHybridEncryptionKeyServiceModule()),
  // GCP multiparty implementation
  GCP_KMS_MULTI_PARTY_DECRYPTION_KEY_SERVICE(
      new GcpKmsMultiPartyHybridEncryptionKeyServiceModule()),
  // Multi-party Non-enclave implementation.
  AWS_KMS_MULTI_PARTY_DECRYPTION_KEY_SERVICE(
      new AwsKmsMultiPartyHybridEncryptionKeyServiceModule()),
  // Multi-party enclave implementation.
  AWS_ENCLAVE_CLI_MULTI_PARTY_DECRYPTION_KEY_SERVICE(
      new AwsEnclaveMultiPartyHybridEncryptionKeyServiceModule());

  private final Module hybridEncryptionKeyServiceModule;

  HybridEncryptionKeyServiceSelector(Module pullerGuiceModule) {
    this.hybridEncryptionKeyServiceModule = pullerGuiceModule;
  }

  public Module getHybridEncryptionKeyServiceModule() {
    return hybridEncryptionKeyServiceModule;
  }
}
