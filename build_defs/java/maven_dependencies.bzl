# Copyright 2023 Google LLC
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

"""Defines Maven dependencies used by maven_install."""

load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven")
load("//build_defs/tink:tink_defs.bzl", "TINK_MAVEN_ARTIFACTS")

# LINT.IfChange
JACKSON_VERSION = "2.15.2"

AUTO_VALUE_VERSION = "1.7.4"

AWS_SDK_VERSION = "2.17.239"

GOOGLE_GAX_VERSION = "2.47.0"

AUTO_SERVICE_VERSION = "1.0"

def maven_dependencies():
    maven_install(
        name = "maven",
        artifacts = [
            "org.slf4j:slf4j-api:2.0.16",
            "org.slf4j:slf4j-log4j12:2.0.16",
            "org.slf4j:slf4j-simple:2.0.16",
            "org.apache.tomcat:annotations-api:6.0.53",
            # Specify the protobuf-java explicitly to make sure
            # the version will be upgraded with protobuf cc.
            "com.google.protobuf:protobuf-java:4.28.0",
            "com.google.protobuf:protobuf-java-util:4.28.0",
            "com.google.protobuf:protobuf-javalite:4.28.0",
            "com.beust:jcommander:1.81",
            "com.fasterxml.jackson.core:jackson-annotations:" + JACKSON_VERSION,
            "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VERSION,
            "com.fasterxml.jackson.core:jackson-databind:" + JACKSON_VERSION,
            "com.fasterxml.jackson.datatype:jackson-datatype-guava:" + JACKSON_VERSION,
            "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:" + JACKSON_VERSION,
            "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:" + JACKSON_VERSION,
            "com.google.acai:acai:1.1",
            "com.google.auto.factory:auto-factory:1.0",
            "com.google.auto.service:auto-service-annotations:" + AUTO_SERVICE_VERSION,
            "com.google.auto.service:auto-service:" + AUTO_SERVICE_VERSION,
            "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
            "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
            "com.google.code.findbugs:jsr305:3.0.2",
            "com.google.code.gson:gson:2.10",
            # Need to include these two, otherwise,
            # complain about google-api-client and cloudkms mismatch.
            "com.google.api-client:google-api-client:2.7.0",
            "com.google.cloud:google-cloud-kms:2.48.0",
            "com.google.cloud:google-cloud-pubsub:1.132.0",
            "com.google.cloud:google-cloud-storage:2.41.0",
            "com.google.cloud:google-cloud-storage-transfer:1.46.0",
            "com.google.cloud:google-cloud-spanner:6.71.0",
            "com.google.cloud:google-cloud-secretmanager:2.46.0",
            "com.google.cloud:google-cloud-compute:1.57.0",
            "com.google.cloud:google-cloudevent-types:0.14.0",
            "com.google.api.grpc:proto-google-cloud-compute-v1:1.58.0",
            "com.google.cloud.functions.invoker:java-function-invoker:1.3.1",
            "com.google.auth:google-auth-library-oauth2-http:1.24.1",
            "com.google.cloud.functions:functions-framework-api:1.1.0",
            "commons-logging:commons-logging:1.1.1",
            "com.google.api:gax:" + GOOGLE_GAX_VERSION,
            "com.google.http-client:google-http-client-jackson2:1.40.0",
            "com.google.cloud:google-cloud-monitoring:3.31.0",
            "com.google.api.grpc:proto-google-cloud-monitoring-v3:3.31.0",
            "com.google.api.grpc:proto-google-cloud-storage-transfer-v1:1.17.0",
            "com.google.api.grpc:proto-google-common-protos:2.27.0",
            "com.google.guava:guava:33.2.1-jre",
            "com.google.guava:guava-testlib:33.2.1-jre",
            "com.google.inject:guice:5.1.0",
            "com.google.inject.extensions:guice-assistedinject:5.1.0",
            "com.google.inject.extensions:guice-testlib:5.1.0",
            "com.google.jimfs:jimfs:1.2",
            "com.google.testparameterinjector:test-parameter-injector:1.19",
            "com.google.truth.extensions:truth-java8-extension:1.3.0",
            "com.google.truth.extensions:truth-proto-extension:1.3.0",
            "com.google.truth:truth:1.3.0",
            "io.cloudevents:cloudevents-api:2.5.0",
            "io.github.resilience4j:resilience4j-core:1.7.1",
            "io.github.resilience4j:resilience4j-retry:1.7.1",
            "javax.annotation:javax.annotation-api:1.3.2",
            "javax.inject:javax.inject:1",
            "junit:junit:4.12",
            "org.apache.avro:avro:1.10.2",
            "org.apache.commons:commons-lang3:3.14.0",
            "org.apache.commons:commons-math3:3.6.1",
            "org.apache.httpcomponents:httpcore:4.4.14",
            "org.apache.httpcomponents:httpclient:4.5.13",
            "org.apache.httpcomponents.client5:httpclient5:5.1.3",
            "org.apache.httpcomponents.core5:httpcore5:5.1.4",
            "org.apache.httpcomponents.core5:httpcore5-h2:5.1.4",  # Explicit transitive dependency to avoid https://issues.apache.org/jira/browse/HTTPCLIENT-2222
            "org.apache.logging.log4j:log4j-1.2-api:2.17.0",
            "org.apache.logging.log4j:log4j-core:2.17.0",
            "org.awaitility:awaitility:3.0.0",
            "org.hamcrest:hamcrest-library:1.3",
            "org.mockito:mockito-core:5.4.0",
            "org.testcontainers:testcontainers:1.21.4",
            "org.testcontainers:localstack:1.21.4",
            "software.amazon.awssdk:auth:" + AWS_SDK_VERSION,
            "software.amazon.awssdk:kms:" + AWS_SDK_VERSION,
            "software.amazon.awssdk:regions:" + AWS_SDK_VERSION,
            "software.amazon.awssdk:sdk-core:" + AWS_SDK_VERSION,
            "software.amazon.awssdk:sts:" + AWS_SDK_VERSION,
            "software.amazon.awssdk:utils:" + AWS_SDK_VERSION,
            "com.google.api:gapic-generator-java:2.44.0",  # To use generated gRpc Java interface
            "io.opentelemetry:opentelemetry-api:1.31.0",
            "io.opentelemetry:opentelemetry-exporter-otlp:1.31.0",
            "io.opentelemetry:opentelemetry-sdk:1.31.0",
            "io.opentelemetry:opentelemetry-sdk-common:1.31.0",
            "io.opentelemetry:opentelemetry-sdk-metrics:1.31.0",
            "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.31.0",
            "com.google.cloud.opentelemetry:detector-resources:0.26.0-alpha",
            # maven_install can't generate the right url to download this library
            # with com.google.apis:google-api-services-cloudkms:<version>
            maven.artifact(
                group = "com.google.apis",
                artifact = "google-api-services-cloudkms",
                version = "v1-rev20240808-2.0.0",
            ),
        ] + TINK_MAVEN_ARTIFACTS,
        repositories = [
            "https://repo1.maven.org/maven2",
        ],
    )

# LINT.ThenChange(/MODULE.bazel:maven_deps)
