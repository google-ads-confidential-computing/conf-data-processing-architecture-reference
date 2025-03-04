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
#include <gmock/gmock.h>

#include <memory>

#include "opentelemetry/common/key_value_iterable.h"
#include "opentelemetry/context/context.h"
#include "opentelemetry/sdk/metrics/meter.h"

namespace google::scp::cpio::client_providers {

template <class T>
class MockCounter : public opentelemetry::metrics::Counter<T> {
 public:
  MOCK_METHOD(void, Add, (T value), (noexcept, override));

  MOCK_METHOD(void, Add,
              (T value, const opentelemetry::context::Context& context),
              (noexcept, override));
  MOCK_METHOD(void, Add,
              (T value,
               const opentelemetry::common::KeyValueIterable& attributes),
              (noexcept, override));
  MOCK_METHOD(void, Add,
              (T value,
               const opentelemetry::common::KeyValueIterable& attributes,
               const opentelemetry::context::Context& context),
              (noexcept, override));
};

template <class T>
class MockHistogram : public opentelemetry::metrics::Histogram<T> {
 public:
  MOCK_METHOD(void, Record, (T value), (noexcept, override));

  MOCK_METHOD(void, Record,
              (T value,
               const opentelemetry::common::KeyValueIterable& attributes),
              (noexcept, override));
  MOCK_METHOD(void, Record,
              (T value, const opentelemetry::context::Context& context),
              (noexcept, override));
  MOCK_METHOD(void, Record,
              (T value,
               const opentelemetry::common::KeyValueIterable& attributes,
               const opentelemetry::context::Context& context),
              (noexcept, override));
};

template <class T>
class MockGauge : public opentelemetry::metrics::Gauge<T> {
 public:
  MOCK_METHOD(void, Record, (T value), (noexcept, override));
  MOCK_METHOD(void, Record,
              (T value,
               const opentelemetry::common::KeyValueIterable& attributes),
              (noexcept, override));
  MOCK_METHOD(void, Record,
              (T value, const opentelemetry::context::Context& context),
              (noexcept, override));
  MOCK_METHOD(void, Record,
              (T value,
               const opentelemetry::common::KeyValueIterable& attributes,
               const opentelemetry::context::Context& context),
              (noexcept, override));
};

class MockMeter : public opentelemetry::metrics::Meter {
 public:
  MOCK_METHOD(std::unique_ptr<opentelemetry::metrics::Counter<std::uint64_t>>,
              CreateUInt64Counter,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::unique_ptr<opentelemetry::metrics::Counter<double>>,
              CreateDoubleCounter,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::shared_ptr<opentelemetry::metrics::ObservableInstrument>,
              CreateInt64ObservableCounter,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::shared_ptr<opentelemetry::metrics::ObservableInstrument>,
              CreateDoubleObservableCounter,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::unique_ptr<opentelemetry::metrics::Histogram<std::uint64_t>>,
              CreateUInt64Histogram,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::unique_ptr<opentelemetry::metrics::Histogram<double>>,
              CreateDoubleHistogram,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::unique_ptr<opentelemetry::metrics::Gauge<std::int64_t>>,
              CreateInt64Gauge,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::unique_ptr<opentelemetry::metrics::Gauge<double>>,
              CreateDoubleGauge,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::shared_ptr<opentelemetry::metrics::ObservableInstrument>,
              CreateInt64ObservableGauge,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::shared_ptr<opentelemetry::metrics::ObservableInstrument>,
              CreateDoubleObservableGauge,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(
      std::unique_ptr<opentelemetry::metrics::UpDownCounter<std::int64_t>>,
      CreateInt64UpDownCounter,
      (std::string_view, std::string_view, std::string_view),
      (noexcept, override));
  MOCK_METHOD(std::unique_ptr<opentelemetry::metrics::UpDownCounter<double>>,
              CreateDoubleUpDownCounter,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::shared_ptr<opentelemetry::metrics::ObservableInstrument>,
              CreateInt64ObservableUpDownCounter,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
  MOCK_METHOD(std::shared_ptr<opentelemetry::metrics::ObservableInstrument>,
              CreateDoubleObservableUpDownCounter,
              (std::string_view, std::string_view, std::string_view),
              (noexcept, override));
};

}  // namespace google::scp::cpio::client_providers
