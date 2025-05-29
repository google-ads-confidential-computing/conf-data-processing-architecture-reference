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

#ifndef SCP_CPIO_INTERFACE_METRIC_CLIENT_TYPE_DEF_H_
#define SCP_CPIO_INTERFACE_METRIC_CLIENT_TYPE_DEF_H_

#include <chrono>
#include <map>
#include <string>
#include <vector>

namespace google::scp::cpio {
/// Configurations for MetricClient.
struct MetricClientOptions {
  virtual ~MetricClientOptions() = default;

  MetricClientOptions() = default;

  MetricClientOptions(const MetricClientOptions& options)
      : enable_remote_metric_aggregation(
            options.enable_remote_metric_aggregation),
        enable_native_metric_aggregation(
            options.enable_native_metric_aggregation),
        remote_metric_collector_address(
            options.remote_metric_collector_address),
        metric_exporter_interval(options.metric_exporter_interval),
        enable_batch_recording(options.enable_batch_recording),
        namespace_for_batch_recording(options.namespace_for_batch_recording),
        batch_recording_time_duration(options.batch_recording_time_duration) {}

  /**
   * @brief Pushes metrics to a remote OpenTelemetry Collector server if true.
   *
   * If enabled, must specify remote_metric_collector_address.
   */
  bool enable_remote_metric_aggregation = false;
  /**
   * @brief Aggregates metric at application level if true. Cannot be used in
   * conjunction with enable_remote_metric_aggregation.
   *
   */
  bool enable_native_metric_aggregation = false;
  /**
   * @brief The hostname or IP address of the remote OpenTelemetry Collector
   * instance specified with a gRPC port.
   *
   * Example: "10.2.0.1:4317"
   */
  std::string remote_metric_collector_address;
  /**
   * @brief The interval of how frequent the OpenTelemetry API exporters
   * send aggregated metrics to the remote OpenTelemetry Collector.
   *
   * Default value is 60,000 ms.
   */
  std::chrono::milliseconds metric_exporter_interval =
      std::chrono::milliseconds(60000);
  /**
   * @brief Pushes metrics in batches if true. In most times, when the
   * batch_recording_time_duration is met, the push is triggered. Cloud has
   * its own maximum batch size, and if the maximum batch size is met before the
   * batch_recording_time_duration, the push is triggered too.
   *
   * Batching only works when the metric namespaces are the same for all the
   * metrics.
   */
  bool enable_batch_recording = false;
  /**
   * @brief The top level grouping for the application metrics. A
   * typical example would be "/application_name/environment_name".
   * Batching only works for the metrics for the same namespace.
   * If enable_batch_recording is true, set the global namespace here.
   * Then in each request, we don't need to set the namespace. If the namespace
   * is setted in the request, it should match the namespace here.
   */
  std::string namespace_for_batch_recording;
  /**
   * @brief The time duration to push metrics when enable_batch_recording is
   * true.
   */
  std::chrono::milliseconds batch_recording_time_duration =
      std::chrono::milliseconds(30000);
};
}  // namespace google::scp::cpio

#endif  // SCP_CPIO_INTERFACE_METRIC_CLIENT_TYPE_DEF_H_
