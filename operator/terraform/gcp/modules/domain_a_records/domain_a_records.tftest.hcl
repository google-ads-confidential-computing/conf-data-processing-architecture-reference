/**
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

test {
  parallel = true
}
mock_provider "google" {
  source          = "../../../../../tfmocks/google/"
  override_during = plan
}

# All run blocks should have "command = plan".
# Take great care when writing tests with "command = apply".
variables {
  parent_domain_name            = "parent.domain.name"
  parent_domain_name_project    = "domain_name_project"
  service_domain_to_address_map = {}
}

run "makes_dns_managed_zone" {
  command = plan

  assert {
    condition     = data.google_dns_managed_zone.dns_zone.name == "parent-domain-name"
    error_message = "Wrong zone name"
  }
}

run "dns_managed_zone_fails_if_domain_name_empty" {
  command = plan

  variables {
    parent_domain_name         = ""
    parent_domain_name_project = ""
  }

  expect_failures = [data.google_dns_managed_zone.dns_zone]
}

run "creates_dns_record_for_each_domain" {
  command = plan

  variables {
    service_domain_to_address_map = {
      domain1 = "address1"
      domain2 = "address2"
    }
  }

  assert {
    condition = jsonencode([for record in google_dns_record_set.a : {
      name = record.name
      rrdatas = record.rrdatas }]) == jsonencode([
      {
        name    = "domain1."
        rrdatas = ["address1"]
        }, {
        name    = "domain2."
        rrdatas = ["address2"]
      }
    ])
    error_message = "Wrong record set"
  }
}
