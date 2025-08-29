/*
 * Copyright 2025 Google LLC
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

package com.google.scp.coordinator.keymanagement.shared.serverless.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link RequestHeaderParsingUtil}. */
@RunWith(JUnit4.class)
public class RequestHeaderParsingUtilTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private RequestContext mockRequestContext;

  @Test
  public void getCallerEmail_success() {
    String email = "test-user@example.com";
    String payload = String.format("{\"email\":\"%s\"}", email);
    String encodedPayload = Base64.getUrlEncoder().encodeToString(payload.getBytes());
    String jwt = String.format("header.%s.signature", encodedPayload);
    when(mockRequestContext.getFirstHeader("Authorization")).thenReturn(Optional.of("Bearer " + jwt));

    Optional<String> result = RequestHeaderParsingUtil.getCallerEmail(mockRequestContext);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(email);
  }

  @Test
  public void getCallerEmail_noAuthHeader_returnsEmpty() {
    when(mockRequestContext.getFirstHeader("Authorization")).thenReturn(Optional.empty());

    Optional<String> result = RequestHeaderParsingUtil.getCallerEmail(mockRequestContext);

    assertThat(result).isEmpty();
  }

  @Test
  public void getCallerEmail_jwtWrongNumberOfParts_returnsEmpty() {
    String jwt = "justonepart";
    when(mockRequestContext.getFirstHeader("Authorization")).thenReturn(Optional.of("Bearer " + jwt));

    Optional<String> result = RequestHeaderParsingUtil.getCallerEmail(mockRequestContext);

    assertThat(result).isEmpty();
  }

  @Test
  public void getCallerEmail_badBase64Payload_returnsEmpty() {
    String jwt = "header.not-valid-base64.signature";
    when(mockRequestContext.getFirstHeader("Authorization")).thenReturn(Optional.of("Bearer " + jwt));

    Optional<String> result = RequestHeaderParsingUtil.getCallerEmail(mockRequestContext);

    assertThat(result).isEmpty();
  }

  @Test
  public void getCallerEmail_payloadNotJson_returnsEmpty() {
    String payload = "this is not json";
    String encodedPayload = Base64.getUrlEncoder().encodeToString(payload.getBytes());
    String jwt = String.format("header.%s.signature", encodedPayload);
    when(mockRequestContext.getFirstHeader("Authorization")).thenReturn(Optional.of("Bearer " + jwt));

    Optional<String> result = RequestHeaderParsingUtil.getCallerEmail(mockRequestContext);

    assertThat(result).isEmpty();
  }

  @Test
  public void getCallerEmail_payloadMissingEmailField_returnsEmpty() {
    String payload = "{\"some_other_field\":\"value\"}";
    String encodedPayload = Base64.getUrlEncoder().encodeToString(payload.getBytes());
    String jwt = String.format("header.%s.signature", encodedPayload);
    when(mockRequestContext.getFirstHeader("Authorization")).thenReturn(Optional.of("Bearer " + jwt));

    Optional<String> result = RequestHeaderParsingUtil.getCallerEmail(mockRequestContext);

    assertThat(result).isEmpty();
  }
}
