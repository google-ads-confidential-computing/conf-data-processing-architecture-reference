# LINT.IfChange
################################################################################
# Download all http_archives and git_repositories: Begin
################################################################################

#############################
# CC SDK Dependencies Rules #
#############################

load("//build_defs/cc:sdk.bzl", "sdk_dependencies")

# Declare explicit protobuf version and hash, to override any implicit dependencies.
# Please update both while upgrading to new versions.
load("//build_defs/shared:protobuf.bzl", "DEFAULT_PROTOBUF_CORE_VERSION", "DEFAULT_PROTOBUF_SHA_256")

sdk_dependencies(DEFAULT_PROTOBUF_CORE_VERSION, DEFAULT_PROTOBUF_SHA_256)

#############################
# CC hermetic toolchain setup
#############################

# A fixed python toolchain is required for the hermetic build
load("@rules_python//python:repositories.bzl", "py_repositories", "python_register_toolchains")

py_repositories()

python_register_toolchains(
    name = "python3_9",
    ignore_root_user_error = True,
    python_version = "3.9",
)

load("//build_defs/cc:toolchains_llvm.bzl", "toolchains_llvm")

toolchains_llvm()

load("@toolchains_llvm//toolchain:deps.bzl", "bazel_toolchain_dependencies")

bazel_toolchain_dependencies()

load("//build_defs/cc:sysroot_ext.bzl", "chromium_sysroot")

chromium_sysroot()

load("@toolchains_llvm//toolchain:rules.bzl", "llvm_toolchain")

llvm_toolchain(
    name = "llvm_toolchain",
    llvm_version = "19.1.0",
    sysroot = {
        "linux-x86_64": "@chromium_sysroot//:sysroot",
    },
)

load("@llvm_toolchain//:toolchains.bzl", "llvm_register_toolchains")

llvm_register_toolchains()

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

load("@io_opentelemetry_cpp//bazel:repository.bzl", "opentelemetry_cpp_deps")

opentelemetry_cpp_deps()

# (required after v1.8.0) Load extra dependencies required for OpenTelemetry
load("@io_opentelemetry_cpp//bazel:extra_deps.bzl", "opentelemetry_extra_deps")

opentelemetry_extra_deps()

#################################
# SCP Shared Dependencies Rules #
#################################

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

###################
# Container rules #
###################

load("@rules_oci//oci:dependencies.bzl", "rules_oci_dependencies")

rules_oci_dependencies()

load("@rules_oci//oci:repositories.bzl", "oci_register_toolchains")

oci_register_toolchains(name = "oci")

load("@aspect_bazel_lib//lib:repositories.bzl", "aspect_bazel_lib_dependencies", "aspect_bazel_lib_register_toolchains")

aspect_bazel_lib_dependencies()

aspect_bazel_lib_register_toolchains()

load("@rules_distroless//apt:apt.bzl", "apt")

apt.install(
    name = "scp_base_runtime_image_debian_packages",
    lock = "//cc/public/cpio/build_deps/shared:base_runtime_image_packages_manifest.lock.json",
    manifest = "//cc/public/cpio/build_deps/shared:base_runtime_image_packages_manifest.yaml",
)

load("@scp_base_runtime_image_debian_packages//:packages.bzl", "scp_base_runtime_image_debian_packages_packages")

scp_base_runtime_image_debian_packages_packages()

################################################################################
# Download Containers: Begin

################################################################################
load("@rules_oci//oci:pull.bzl", "oci_pull")

# Distroless image for running Java.
oci_pull(
    name = "java_base_oci",
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

################################################################################
# Download Containers: End
################################################################################

# LINT.ThenChange(
#   WORKSPACE.bzlmod,
#   //performance_test/gcp/WORKSPACE
# )
