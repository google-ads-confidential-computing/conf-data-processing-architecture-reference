# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

package(
    default_visibility = ["//visibility:public"],
)

alias(
    name = "guava",
    actual = "@maven//:com_google_guava_guava",
)

alias(
    name = "guava_testlib",
    actual = "@maven//:com_google_guava_guava_testlib",
)

alias(
    name = "guice",
    actual = "@maven//:com_google_inject_guice",
)

alias(
    name = "guice-assistedinject",
    actual = "@maven//:com_google_inject_extensions_guice_assistedinject",
)

alias(
    name = "guice-testlib",
    actual = "@maven//:com_google_inject_extensions_guice_testlib",
)

alias(
    name = "autoservice_processor",
    actual = "@maven//:com_google_auto_service_auto_service",
)

alias(
    name = "autoservice_annotations",
    actual = "@maven//:com_google_auto_service_auto_service_annotations",
)

java_plugin(
    name = "autoservice_plugin",
    processor_class = "com.google.auto.service.processor.AutoServiceProcessor",
    deps = [":autoservice_processor"],
)

java_library(
    name = "autoservice",
    exported_plugins = [
        ":autoservice_plugin",
    ],
    neverlink = True,
    exports = [":autoservice_annotations"],
)

alias(
    name = "findbugs",
    actual = "@maven//:com_google_code_findbugs_jsr305",
)

alias(
    name = "gson",
    actual = "@maven//:com_google_code_gson_gson",
)

alias(
    name = "hamcrest",
    actual = "@maven//:org_hamcrest_hamcrest_library",
)

alias(
    name = "jackson_annotations",
    actual = "@maven//:com_fasterxml_jackson_core_jackson_annotations",
)

alias(
    name = "jackson_databind",
    actual = "@maven//:com_fasterxml_jackson_core_jackson_databind",
)

alias(
    name = "jackson_core",
    actual = "@maven//:com_fasterxml_jackson_core_jackson_core",
)

alias(
    name = "jackson_datatype_guava",
    actual = "@maven//:com_fasterxml_jackson_datatype_jackson_datatype_guava",
)

alias(
    name = "jackson_datatype_jdk8",
    actual = "@maven//:com_fasterxml_jackson_datatype_jackson_datatype_jdk8",
)

alias(
    name = "jackson_datatype_jsr310",
    actual = "@maven//:com_fasterxml_jackson_datatype_jackson_datatype_jsr310",
)

alias(
    name = "javax_annotation",
    actual = "@maven//:javax_annotation_javax_annotation_api",
)

alias(
    name = "javax_inject",
    actual = "@maven//:javax_inject_javax_inject",
)

alias(
    name = "jayway_jsonpath",
    actual = "@maven//:com_jayway_jsonpath_json_path",
)

alias(
    name = "jimfs",
    actual = "@maven//:com_google_jimfs_jimfs",
)

alias(
    name = "google_truth",
    actual = "@maven//:com_google_truth_truth",
)

alias(
    name = "snakeyaml",
    actual = "@maven_yaml//:org_yaml_snakeyaml",
)

# Make sure use snakeyaml deps together to pick the right snakeyaml version.
alias(
    name = "test_parameter_injector",
    actual = "@maven//:com_google_testparameterinjector_test_parameter_injector",
)

alias(
    name = "google_truth_proto",
    actual = "@maven//:com_google_truth_extensions_truth_proto_extension",
)

alias(
    name = "google_truth8",
    actual = "@maven//:com_google_truth_extensions_truth_java8_extension",
)

alias(
    name = "autovalue_processor",
    actual = "@maven//:com_google_auto_value_auto_value",
)

alias(
    name = "autovalue_annotations",
    actual = "@maven//:com_google_auto_value_auto_value_annotations",
)

java_plugin(
    name = "autovalue_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [":autovalue_processor"],
)

java_plugin(
    name = "autooneof_plugin",
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = [":autovalue_processor"],
)

java_library(
    name = "autovalue",
    exported_plugins = [
        ":autooneof_plugin",
        ":autovalue_plugin",
    ],
    neverlink = True,
    exports = [":autovalue_annotations"],
)

alias(
    name = "autofactory_library",
    actual = "@maven//:com_google_auto_factory_auto_factory",
)

java_plugin(
    name = "autofactory_plugin",
    generates_api = True,
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    deps = [
        ":autofactory_library",
    ],
)

java_library(
    name = "autofactory",
    exported_plugins = [":autofactory_plugin"],
    neverlink = True,
    exports = [":autofactory_library"],
)

alias(
    name = "acai",
    actual = "@maven//:com_google_acai_acai",
)

alias(
    name = "jcommander",
    actual = "@maven//:com_beust_jcommander",
)

alias(
    name = "avro",
    actual = "@maven//:org_apache_avro_avro",
)

alias(
    name = "apache_httpclient",
    actual = "@maven//:org_apache_httpcomponents_httpclient",
)

alias(
    name = "apache_httpclient5",
    actual = "@maven//:org_apache_httpcomponents_client5_httpclient5",
)

alias(
    name = "apache_httpcore",
    actual = "@maven//:org_apache_httpcomponents_httpcore",
)

alias(
    name = "apache_httpcore5",
    actual = "@maven//:org_apache_httpcomponents_core5_httpcore5",
)

alias(
    name = "apache_httpcore5_h2",
    actual = "@maven//:org_apache_httpcomponents_core5_httpcore5_h2",
)

alias(
    name = "aws_api_gateway_v1",
    actual = "@maven//:software_amazon_awssdk_apigateway",
)

alias(
    name = "aws_arns",
    actual = "@maven//:software_amazon_awssdk_arns",
)

alias(
    name = "aws_auto_scaling",
    actual = "@maven//:software_amazon_awssdk_autoscaling",
)

alias(
    name = "aws_cloudwatch",
    actual = "@maven//:software_amazon_awssdk_cloudwatch",
)

alias(
    name = "aws_sdk",
    actual = "@maven//:software_amazon_awssdk_aws_sdk_java",
)

alias(
    name = "aws_ec2",
    actual = "@maven//:software_amazon_awssdk_ec2",
)

alias(
    name = "aws_pricing",
    actual = "@maven//:software_amazon_awssdk_pricing",
)

alias(
    name = "aws_s3",
    actual = "@maven//:software_amazon_awssdk_s3",
)

alias(
    name = "aws_sns",
    actual = "@maven//:software_amazon_awssdk_sns",
)

alias(
    name = "aws_ssm",
    actual = "@maven//:software_amazon_awssdk_ssm",
)

alias(
    name = "aws_sts",
    actual = "@maven//:software_amazon_awssdk_sts",
)

alias(
    name = "aws_sqs",
    actual = "@maven//:software_amazon_awssdk_sqs",
)

alias(
    name = "aws_regions",
    actual = "@maven//:software_amazon_awssdk_regions",
)

alias(
    name = "aws_dynamodb",
    actual = "@maven//:software_amazon_awssdk_dynamodb",
)

alias(
    name = "aws_dynamodb_enhanced",
    actual = "@maven//:software_amazon_awssdk_dynamodb_enhanced",
)

alias(
    name = "aws_lambda_java_core",
    actual = "@maven//:com_amazonaws_aws_lambda_java_core",
)

alias(
    name = "aws_lambda_java_events",
    actual = "@maven//:com_amazonaws_aws_lambda_java_events",
)

alias(
    name = "aws_lambda_java_events_sdk_transformer",
    actual = "@maven//:com_amazonaws_aws_lambda_java_events_sdk_transformer",
)

alias(
    name = "slf4j",
    actual = "@maven//:org_slf4j_slf4j_api",
)

alias(
    name = "slf4j_simple",
    actual = "@maven//:org_slf4j_slf4j_simple",
)

alias(
    name = "slf4j_log4j",
    actual = "@maven//:org_slf4j_slf4j_reload4j",
    # log4j is only used in tests.
    visibility = ["//javatests:__subpackages__"],
)

alias(
    name = "log4j_12_api",
    actual = "@maven//:org_apache_logging_log4j_log4j_1_2_api",
    # log4j is only used in tests.
    visibility = ["//javatests:__subpackages__"],
)

alias(
    name = "log4j_core",
    actual = "@maven//:org_apache_logging_log4j_log4j_core",
    # log4j is only used in tests.
    visibility = ["//javatests:__subpackages__"],
)

alias(
    name = "differential_privacy",
    actual = "@com_google_differential_privacy//java/main/com/google/privacy/differentialprivacy",
)

alias(
    name = "differential_privacy_reference_noise",
    actual = "@com_google_differential_privacy//java/main/com/google/privacy/differentialprivacy/testing:reference_noise",
)

alias(
    name = "differential_privacy_statistical_utils",
    actual = "@com_google_differential_privacy//java/main/com/google/privacy/differentialprivacy/testing:statistical_utils",
)

alias(
    name = "tink",
    actual = "@maven//:com_google_crypto_tink_tink",
)

alias(
    name = "tink-gcpkms",
    actual = "@maven//:com_google_crypto_tink_tink_gcpkms",
)

alias(
    name = "gcp-http-client-jackson2",
    actual = "@maven//:com_google_http_client_google_http_client_jackson2",
)

alias(
    name = "aws_core",
    actual = "@maven//:software_amazon_awssdk_sdk_core",
)

alias(
    name = "aws_auth",
    actual = "@maven//:software_amazon_awssdk_auth",
)

alias(
    name = "aws_http_client_spi",
    actual = "@maven//:software_amazon_awssdk_http_client_spi",
)

alias(
    name = "aws_kms",
    actual = "@maven//:software_amazon_awssdk_kms",
)

alias(
    name = "aws_profiles",
    actual = "@maven//:software_amazon_awssdk_profiles",
)

alias(
    name = "aws_utils",
    actual = "@maven//:software_amazon_awssdk_utils",
)

alias(
    name = "aws_url_connection_client",
    actual = "@maven//:software_amazon_awssdk_url_connection_client",
)

alias(
    name = "aws_apache_http",
    actual = "@maven//:software_amazon_awssdk_apache_client",
)

alias(
    name = "aws_lambda_v2",
    actual = "@maven//:software_amazon_awssdk_lambda",
)

alias(
    name = "mockserver_core",
    actual = "@maven//:org_mock_server_mockserver_core",
)

alias(
    name = "mockserver_client",
    actual = "@maven//:org_mock_server_mockserver_client_java",
)

alias(
    name = "mockserver_junit_rule",
    actual = "@maven//:org_mock_server_mockserver_junit_rule",
)

alias(
    name = "testcontainers",
    actual = "@maven//:org_testcontainers_testcontainers",
)

alias(
    name = "gcp_core",
    actual = "@maven//:com_google_cloud_google_cloud_core",
)

alias(
    name = "gcp_storage",
    actual = "@maven//:com_google_cloud_google_cloud_storage",
)

alias(
    name = "gcp_storage_transfer",
    actual = "@maven//:com_google_cloud_google_cloud_storage_transfer",
)

alias(
    name = "google_gax",
    actual = "@maven//:com_google_api_gax",
)

alias(
    name = "google_api_client",
    actual = "@maven//:com_google_api_client_google_api_client",
)

alias(
    name = "google_auth_library_oauth2_http",
    actual = "@maven//:com_google_auth_google_auth_library_oauth2_http",
)

alias(
    name = "google_auth_library_credentials",
    actual = "@maven//:com_google_auth_google_auth_library_credentials",
)

alias(
    name = "localstack_containers",
    actual = "@maven//:org_testcontainers_localstack",
)

alias(
    # localstack_containers uses this dependency in the background.
    # We do not directly use this outdated version of the API.
    name = "aws_sqs_java_sdk1",
    actual = "@maven//:com_amazonaws_aws_java_sdk_sqs",
    visibility = ["//javatests:__subpackages__"],
)

alias(
    # localstack_containers uses this dependency in the background.
    # We do not directly use this outdated version of the API.
    name = "aws_s3_java_sdk1",
    actual = "@maven//:com_amazonaws_aws_java_sdk_s3",
    visibility = ["//javatests:__subpackages__"],
)

alias(
    # localstack_containers uses this dependency in the background.
    # We do not directly use this outdated version of the API.
    name = "aws_kms_java_sdk1",
    actual = "@maven//:com_amazonaws_aws_java_sdk_kms",
    visibility = ["//javatests:__subpackages__"],
)

alias(
    # localstack_containers uses this dependency in the background.
    # We do not directly use this outdated version of the API.
    name = "aws_core_java_sdk1",
    actual = "@maven//:com_amazonaws_aws_java_sdk_core",
    visibility = ["//javatests:__subpackages__"],
)

alias(
    name = "mockito",
    actual = "@maven//:org_mockito_mockito_core",
    visibility = ["//javatests:__subpackages__"],
)

alias(
    name = "gcp_spanner",
    actual = "@maven//:com_google_cloud_google_cloud_spanner",
)

alias(
    name = "gcp_secretmanager",
    actual = "@maven//:com_google_cloud_google_cloud_secretmanager",
)

alias(
    name = "gcp_secretmanager_proto",
    actual = "@maven//:com_google_api_grpc_proto_google_cloud_secretmanager_v1",
)

alias(
    name = "gcp_compute",
    actual = "@maven//:com_google_cloud_google_cloud_compute",
)

alias(
    name = "gcp_cloudevent_types",
    actual = "@maven//:com_google_cloud_google_cloudevent_types",
)

alias(
    name = "gcp_compute_proto",
    actual = "@maven//:com_google_api_grpc_proto_google_cloud_compute_v1",
)

alias(
    name = "gcp_kms",
    actual = "@maven//:com_google_cloud_google_cloud_kms",
)

alias(
    name = "gcp_pubsub",
    actual = "@maven//:com_google_cloud_google_cloud_pubsub",
)

alias(
    name = "gcp_pubsub_v1",
    actual = "@maven//:com_google_api_grpc_proto_google_cloud_pubsub_v1",
)

alias(
    name = "gcp_api_common",
    actual = "@maven//:com_google_api_api_common",
)

alias(
    name = "gcp_api_gax",
    actual = "@maven//:com_google_api_gax",
)

alias(
    name = "grpc_api",
    actual = "@maven//:io_grpc_grpc_api",
)

alias(
    name = "gcp_gax_grpc",
    actual = "@maven//:com_google_api_gax_grpc",
)

# The protobuf version 24.4 is not available in maven yet.
# Use protobuf_java from com_google_protobuf repo directly.
alias(
    name = "protobuf_java",
    actual = "@com_google_protobuf//:protobuf_java",
)

# The protobuf version 24.4 is not available in maven yet.
# Use protobuf_java_util from com_google_protobuf repo directly.
alias(
    name = "protobuf_java_util",
    actual = "@com_google_protobuf//:protobuf_java_util",
)

alias(
    name = "gcp_cloud_function",
    actual = "@maven//:com_google_cloud_functions_functions_framework_api",
)

alias(
    name = "io_cloudevents_api",
    actual = "@maven//:io_cloudevents_cloudevents_api",
)

alias(
    name = "commons_logging",
    actual = "@maven//:commons_logging_commons_logging",
)

alias(
    name = "awaitility",
    actual = "@maven//:org_awaitility_awaitility",
    visibility = ["//javatests:__subpackages__"],
)

alias(
    name = "aws_core2",
    actual = "@maven//:software_amazon_awssdk_aws_core",
)

alias(
    name = "junit",
    actual = "@maven//:junit_junit",
)

alias(
    name = "resilience4j_retry",
    actual = "@maven//:io_github_resilience4j_resilience4j_retry",
)

alias(
    name = "resilience4j_core",
    actual = "@maven//:io_github_resilience4j_resilience4j_core",
)

alias(
    name = "gcp_function_invoker",
    actual = "@maven//:com_google_cloud_functions_invoker_java_function_invoker",
)

alias(
    name = "bazel_runfiles",
    actual = "@bazel_tools//tools/java/runfiles",
)

alias(
    name = "gcp_cloud_monitoring",
    actual = "@maven//:com_google_cloud_google_cloud_monitoring",
)

alias(
    name = "gcp_monitoring_v3",
    actual = "@maven//:com_google_api_grpc_proto_google_cloud_monitoring_v3",
)

alias(
    name = "gcp_storage_transfer_v1",
    actual = "@maven//:com_google_api_grpc_proto_google_cloud_storage_transfer_v1",
)

alias(
    name = "google_common_proto",
    actual = "@maven//:com_google_api_grpc_proto_google_common_protos",
)

alias(
    name = "google_api_services_cloudkms",
    actual = "@maven//:com_google_apis_google_api_services_cloudkms",
)

alias(
    name = "google_http_client",
    actual = "@maven//:com_google_http_client_google_http_client",
)

alias(
    name = "google_http_client_gson",
    actual = "@maven//:com_google_http_client_google_http_client_gson",
)

alias(
    name = "grpc_netty",
    actual = "@maven//:io_grpc_grpc_netty",
)

alias(
    name = "grcp_netty_shaded",
    actual = "@maven//:io_grpc_grpc_netty_shaded",
)

alias(
    name = "conscrypt_openjdk",
    actual = "@maven//:org_conscrypt_conscrypt_openjdk_uber",
)

alias(
    name = "opentelemetry_api",
    actual = "@maven//:io_opentelemetry_opentelemetry_api",
)

alias(
    name = "opentelemetry_sdk",
    actual = "@maven//:io_opentelemetry_opentelemetry_sdk",
)

alias(
    name = "opentelemetry_sdk_common",
    actual = "@maven//:io_opentelemetry_opentelemetry_sdk_common",
)

alias(
    name = "opentelemetry_sdk_metrics",
    actual = "@maven//:io_opentelemetry_opentelemetry_sdk_metrics",
)

alias(
    name = "opentelemetry_sdk_extension_autoconfigure",
    actual = "@maven//:io_opentelemetry_opentelemetry_sdk_extension_autoconfigure",
)

alias(
    name = "opentelemetry_exporter_otlp",
    actual = "@maven//:io_opentelemetry_opentelemetry_exporter_otlp",
)

alias(
    name = "google_cloud_opentelemetry_detector_resources",
    actual = "@maven//:com_google_cloud_opentelemetry_detector_resources",
)

alias(
    name = "org_threeten_bp",
    actual = "@maven//:org_threeten_threetenbp",
)
