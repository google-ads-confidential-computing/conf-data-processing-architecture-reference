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

#ifndef SCP_CORE_INTERFACE_EXECUTION_RESULT_H_
#define SCP_CORE_INTERFACE_EXECUTION_RESULT_H_

#include <utility>
#include <variant>

#include "public/core/interface/execution_result.pb.h"

namespace google::scp::core {

/// Operation's execution status.
enum class ExecutionStatus {
  /// Executed successfully.
  Success = 0,
  /// Execution failed.
  Failure = 1,
  /// did not execution and requires retry.
  Retry = 2,
};

/// Convert ExecutionStatus to Proto.
core::proto::ExecutionStatus ToStatusProto(ExecutionStatus& status);

/// Status code returned from operation execution.
typedef uint64_t StatusCode;
#define SC_OK 0UL
#define SC_UNKNOWN 1UL

struct ExecutionResult;
constexpr ExecutionResult SuccessExecutionResult();

/// Operation's execution result including status and status code.
struct ExecutionResult {
  /**
   * @brief Construct a new Execution Result object
   *
   * @param status status of the execution.
   * @param status_code code of the execution status.
   */
  constexpr ExecutionResult(ExecutionStatus status, StatusCode status_code)
      : status(status), status_code(status_code) {}

  constexpr ExecutionResult()
      : ExecutionResult(ExecutionStatus::Failure, SC_UNKNOWN) {}

  explicit ExecutionResult(const core::proto::ExecutionResult result_proto);

  bool operator==(const ExecutionResult& other) const {
    return status == other.status && status_code == other.status_code;
  }

  bool operator!=(const ExecutionResult& other) const {
    return !operator==(other);
  }

  core::proto::ExecutionResult ToProto();

  bool Successful() const { return *this == SuccessExecutionResult(); }

  bool Retryable() const { return this->status == ExecutionStatus::Retry; }

  explicit operator bool() const { return Successful(); }

  /// Status of the executed operation.
  ExecutionStatus status = ExecutionStatus::Failure;
  /**
   * @brief if the operation was not successful, status_code will indicate the
   * error code.
   */
  StatusCode status_code = SC_UNKNOWN;
};

/// ExecutionResult with success status
constexpr ExecutionResult SuccessExecutionResult() {
  return ExecutionResult(ExecutionStatus::Success, SC_OK);
}

/// ExecutionResult with failure status
class FailureExecutionResult : public ExecutionResult {
 public:
  /// Construct a new Failure Execution Result object
  explicit constexpr FailureExecutionResult(StatusCode status_code)
      : ExecutionResult(ExecutionStatus::Failure, status_code) {}
};

/// ExecutionResult with retry status
class RetryExecutionResult : public ExecutionResult {
 public:
  /// Construct a new Retry Execution Result object
  explicit RetryExecutionResult(StatusCode status_code)
      : ExecutionResult(ExecutionStatus::Retry, status_code) {}
};

// Wrapper class to allow for returning either an ExecutionResult or a value.
// Example use with a function:
//
// ExecutionResultOr<int> ConvertStringToInt(std::string str) {
//   if (str is not an int) {
//     return ExecutionResult(Failure, <some_error_code>);
//   }
//   return string_to_int(str);
// }
//
// NOTE 1: The type T should be copyable and moveable.
// NOTE 2: After moving the value out of an ExecutionResultOr result_or, the
// value in the result_or will be the same as the value v after:
// Foo w(std::move(v));
//
// i.e. use-after-move still applies, but result_or.Successful() would still be
// true.
template <typename T>
class ExecutionResultOr : public std::variant<ExecutionResult, T> {
 private:
  using base = std::variant<ExecutionResult, T>;

 public:
  using base::base;
  using base::operator=;

  ExecutionResultOr() : base(ExecutionResult()) {}

  ///////////// ExecutionResult methods ///////////////////////////////////////

  // Returns true if this contains a value - i.e. it does not contain a status.
  bool Successful() const { return result().Successful(); }

  // Returns the ExecutionResult (if contained), Success otherwise.
  ExecutionResult result() const {
    if (!HasExecutionResult()) {
      return SuccessExecutionResult();
    }
    return std::get<ExecutionResult>(*this);
  }

  ///////////// Value methods /////////////////////////////////////////////////

  // Returns true if this contains a value.
  bool has_value() const { return !HasExecutionResult(); }

  // clang-format off

  // Returns the value held by this.
  // Should be guarded by has_value() calls - otherwise the behavior is
  // undefined.
  const T& value() const& { return std::get<T>(*this); }
  // lvalue reference overload - indicated by "...() &".
  T& value() & { return std::get<T>(*this); }
  // rvalue reference overload - indicated by "...() &&".
  T&& value() && { return std::move(this->value()); }

  // Returns the value held by this if present, otherwise returns u which must
  // be convertible to T.
  template <typename U>
  T value_or(U&& u) const& {
    return has_value() ? **this : static_cast<T>(std::forward<U>(u));
  }
  // lvalue reference overload - indicated by "...() &".
  template <typename U>
  T value_or(U&& u) & {
    return has_value() ? **this : static_cast<T>(std::forward<U>(u));
  }
  // rvalue reference overload - indicated by "...() &&".
  template <typename U>
  T value_or(U&& u) && {
    return has_value() ? *std::move(*this) : static_cast<T>(std::forward<U>(u));
  }

  const T& operator*() const& { return this->value(); }
  // lvalue reference overload - indicated by "...() &".
  T& operator*() & { return this->value(); }
  // rvalue reference overload - indicated by "...() &&".
  T&& operator*() && { return std::move(this->value()); }

  // Returns a pointer to the value held by this.
  // Returns nullptr if no value is contained.
  const T* operator->() const { return std::get_if<T>(this); }
  T* operator->() { return std::get_if<T>(this); }

  // alias for value() && but no call to std::move() is necessary.
  T&& release() { return std::move(this->value()); }

  // clang-format on

 private:
  bool HasExecutionResult() const {
    return std::holds_alternative<ExecutionResult>(*this);
  }
};

namespace internal {
inline void IgnoreUnused(::google::scp::core::ExecutionResult) {}
}  // namespace internal

}  // namespace google::scp::core

#endif  // SCP_CORE_INTERFACE_EXECUTION_RESULT_H_
