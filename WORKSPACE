# LINT.IfChange
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

################################################################################
# Download all http_archives and git_repositories: Begin
################################################################################

# Declare explicit protobuf version and hash, to override any implicit dependencies.
# Please update both while upgrading to new versions.
PROTOBUF_CORE_VERSION_FOR_CC = "28.0"

PROTOBUF_SHA_256_FOR_CC = "13e7749c30bc24af6ee93e092422f9dc08491c7097efa69461f88eb5f61805ce"

#############################
# CC SDK Dependencies Rules #
#############################

load("//build_defs/cc:sdk.bzl", "sdk_dependencies")

sdk_dependencies(PROTOBUF_CORE_VERSION_FOR_CC, PROTOBUF_SHA_256_FOR_CC)

################################################################################
# Rules JVM External: Begin
################################################################################

########
## NOTE: This block must come AFTER sdk_dependencies,
## so that we (SCP) set the java_rules version and not rules_jvm_external
########

load("//build_defs/java:rules_jvm_external.bzl", "rules_jvm_external")

rules_jvm_external()

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()
################################################################################
# Rules JVM External: End
################################################################################

#############
# CPP Rules #
#############
load("@rules_cc//cc:repositories.bzl", "rules_cc_dependencies", "rules_cc_toolchains")

rules_cc_dependencies()

rules_cc_toolchains()

###########################
# CC Dependencies #
###########################

# Add our own newer opentelemetry before gcp c++ sdk
load("//build_defs/shared:opentelemetry.bzl", "opentelemetry_cpp")

opentelemetry_cpp()

# Load indirect dependencies due to
#     https://github.com/bazelbuild/bazel/issues/1943
load("@com_github_googleapis_google_cloud_cpp//bazel:google_cloud_cpp_deps.bzl", "google_cloud_cpp_deps")

google_cloud_cpp_deps()

##########
# GRPC C Deps #
##########

load("@com_github_grpc_grpc//bazel:grpc_deps.bzl", "grpc_deps")

grpc_deps()

load("@com_github_grpc_grpc//bazel:grpc_extra_deps.bzl", "grpc_extra_deps")

grpc_extra_deps()

bind(
    name = "cares",
    actual = "@com_github_cares_cares//:ares",
)

bind(
    name = "madler_zlib",
    actual = "@zlib//:zlib",
)

###############
# Proto rules #
###############
load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

# Boost
load("@com_github_nelhage_rules_boost//:boost/boost.bzl", "boost_deps")

boost_deps()

# Foreign CC
load("@rules_foreign_cc//foreign_cc:repositories.bzl", "rules_foreign_cc_dependencies")

rules_foreign_cc_dependencies()

load("@io_opentelemetry_cpp//bazel:repository.bzl", "opentelemetry_cpp_deps")

opentelemetry_cpp_deps()

# (required after v1.8.0) Load extra dependencies required for OpenTelemetry
load("@io_opentelemetry_cpp//bazel:extra_deps.bzl", "opentelemetry_extra_deps")

opentelemetry_extra_deps()

#################################
# SCP Shared Dependencies Rules #
#################################

# This bazel file contains all the dependencies in SCP, except the dependencies
# only used in SDK. Eventually, each project will have its own bazel file for
# its dependencies, and this file will be removed.
load("//build_defs:scp_dependencies.bzl", "scp_dependencies")

scp_dependencies(PROTOBUF_CORE_VERSION_FOR_CC, PROTOBUF_SHA_256_FOR_CC)

load("@io_grpc_grpc_java//:repositories.bzl", "grpc_java_repositories")

grpc_java_repositories()
##########################################################

################################################################################
# Download all http_archives and git_repositories: End
################################################################################

################################################################################
# Download Maven Dependencies: Begin
################################################################################
load("//build_defs/java:maven_dependencies.bzl", "maven_dependencies")

maven_dependencies()

################################################################################
# Download Maven Dependencies: End
################################################################################

################################################################################
# Download Indirect Dependencies: Begin
################################################################################
# Note: The order of statements in this section is extremely fragile
load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")

rules_java_dependencies()

rules_java_toolchains()

#############
# PKG Rules #
#############
load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")

rules_pkg_dependencies()

################################################################################
# Download Indirect Dependencies: End
################################################################################

load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies")

############
# Go rules #
############
# Need to be after grpc_extra_deps to share go_register_toolchains.
load("@io_bazel_rules_go//go:deps.bzl", "go_rules_dependencies")

go_rules_dependencies()

gazelle_dependencies()

###################
# Container rules #
###################
load(
    "@io_bazel_rules_docker//repositories:repositories.bzl",
    container_repositories = "repositories",
)

container_repositories()

load("@io_bazel_rules_docker//repositories:deps.bzl", container_deps = "deps")

container_deps()

load("@rules_oci//oci:dependencies.bzl", "rules_oci_dependencies")

rules_oci_dependencies()

load("@rules_oci//oci:repositories.bzl", "oci_register_toolchains")

oci_register_toolchains(name = "oci")

###########################
# Binary Dev Dependencies #
###########################
load("@com_github_google_rpmpack//:deps.bzl", "rpmpack_dependencies")
load("@io_bazel_rules_docker//container:container.bzl", "container_pull")
load("@rules_oci//oci:pull.bzl", "oci_pull")

rpmpack_dependencies()

################################################################################
# Download Containers: Begin
################################################################################

# Distroless image for running Java.
container_pull(
    name = "java_base",
    # Using SHA-256 for reproducibility. The tag is latest-amd64. Latest as of 2023-06-12.
    digest = "sha256:1e4181aaff242e2b305bb4abbe811eb122d68ffd7fd87c25c19468a1bc387ce6",
    registry = "gcr.io",
    repository = "distroless/java17-debian11",
)

oci_pull(
    name = "java_base_21_oci",
    # Using SHA-256 for reproducibility. The tag is latest-amd64. Latest as of 2025-11-17.
    digest = "sha256:ed5be62a70c5b99708b4ad0fc53bda628d11e46e917f66720fd218cae8fe1568",
    registry = "gcr.io",
    repository = "distroless/java21-debian12",
)

# Distroless debug image for running Java. Need to use debug image to install more dependencies for CC.
container_pull(
    name = "java_debug_runtime",
    # Using SHA-256 for reproducibility.
    digest = "sha256:66f354398a000c573a1e166cf53ab99cd4766c9084a47b6fd7b814632a3379a9",
    registry = "gcr.io",
    repository = "distroless/java17-debian11",
    tag = "debug-nonroot-amd64",
)

# Distroless image for running C++.
container_pull(
    name = "cc_base",
    registry = "gcr.io",
    repository = "distroless/cc",
    # Using SHA-256 for reproducibility.
    # TODO: use digest instead of tag, currently it's not working.
    tag = "latest",
)

# Distroless image for running statically linked binaries.
container_pull(
    name = "static_base",
    registry = "gcr.io",
    repository = "distroless/static",
    # Using SHA-256 for reproducibility.
    # TODO: use digest instead of tag, currently it's not working.
    tag = "latest",
)

# Needed for reproducibly building AL2 binaries (e.g. //cc/proxy)
container_pull(
    name = "amazonlinux_2",
    # Latest as of 2023-06-12.
    digest = "sha256:cd3d9deffbb15db51382022a67ad717c02e0573c45c312713c046e4c2ac07771",
    registry = "index.docker.io",
    repository = "amazonlinux",
    tag = "2.0.20230530.0",
)

# Needed for reproducibly building AL2023 binaries (e.g. //cc/proxy)
container_pull(
    name = "amazonlinux_2023",
    # Latest as of Aug 29, 2023.
    digest = "sha256:adde60852d11d75196f747c54ae32509d97827369499839b607a6c34c23b2165",
    registry = "index.docker.io",
    repository = "amazonlinux",
    tag = "2023.1.20230825.0",
)

# Needed for cc/pbs/deploy/pbs_server/build_defs
container_pull(
    name = "debian_11",
    digest = "sha256:16f0c16160de30e40a408a6e940083bc1b409fe2a7db93bb0b04262a6ef73419",
    registry = "index.docker.io",
    repository = "amd64/debian",
    tag = "11",
)

# Needed for cc/public/cpio/build_deps
container_pull(
    name = "linux_debian_11_runtime_snapshot",
    digest = "sha256:5dcb0a12205a1381d530dd2175445467283d83b002c9478da487def7e5589ee0",
    registry = "us-docker.pkg.dev",
    repository = "admcloud-scp/cc-runtime-snapshot/linux_debian_11_runtime",
    tag = "v0.3",
)

container_pull(
    name = "linux_debian_11_build_time_snapshot",
    digest = "sha256:77d89a40b7e122d7dd22e367a7e274a126fa1cfb1c917f9c1eeedbdaa8326993",
    registry = "us-docker.pkg.dev",
    repository = "admcloud-scp/cc-build-time-snapshot/linux_debian_11_build_time",
    tag = "v0.2",
)

# Needed for cc reproducible builds
load("//cc/tools/build:build_container_params.bzl", "CC_BUILD_CONTAINER_REGISTRY", "CC_BUILD_CONTAINER_REPOSITORY", "CC_BUILD_CONTAINER_TAG")

container_pull(
    name = "prebuilt_cc_build_container_image_pull",
    registry = CC_BUILD_CONTAINER_REGISTRY,
    repository = CC_BUILD_CONTAINER_REPOSITORY,
    tag = CC_BUILD_CONTAINER_TAG,
)

##########################
## Closure dependencies ##
##########################
load("//build_defs/shared:bazel_rules_closure.bzl", "bazel_rules_closure")

bazel_rules_closure()

load(
    "@io_bazel_rules_closure//closure:repositories.bzl",
    "rules_closure_dependencies",
    "rules_closure_toolchains",
)

rules_closure_dependencies()

rules_closure_toolchains()

################################################################################
# Download Containers: End
################################################################################
