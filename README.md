ADM Cloud Team's CMRT (Common Multi-cloud RunTime) Repo.

# Common Multi-cloud RunTime

CMRT (Common Multi-cloud RunTime) provides facilities to deploy and operate applications in a secure execution environment at scale. This includes managing cryptographic keys, keeping track of the privacy budget, accessing storage, orchestrating a policy-based horizontal autoscaling, and providing cross-cloud interface to access cloud services.

# Building

This repo uses **Bazel 7.7.1** for building all components. By default, it uses a **hermetic LLVM 19.1.0** compiler for C++ (targeting C++17) and **hermetic JDK 21** for Java.

> [!NOTE]
> Bazel hermetic toolchains for C++ (LLVM 19.1.0) requires `libxml2`.
>
> ```bash
> sudo apt-get install libxml2
> ```

Since these toolchains are hermetic and managed by Bazel, you do not need to pre-install or configure a local C++ compiler or JDK to build the project.

To build the repo, run:
```bash
bazel build ...
```

# Contribution

Please see CONTRIBUTING.md for details.
