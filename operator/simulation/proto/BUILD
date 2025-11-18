load("@com_google_protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@com_google_protobuf//bazel:proto_library.bzl", "proto_library")

package(default_visibility = ["//visibility:public"])

proto_library(
    name = "aggregate_service_key_proto",
    srcs = ["aggregate_service_key.proto"],
)

java_proto_library(
    name = "aggregate_service_key_java_proto",
    deps = [":aggregate_service_key_proto"],
)

proto_library(
    name = "conversion_report_proto",
    srcs = ["conversion_report.proto"],
)

java_proto_library(
    name = "conversion_report_java_proto",
    deps = [":conversion_report_proto"],
)
