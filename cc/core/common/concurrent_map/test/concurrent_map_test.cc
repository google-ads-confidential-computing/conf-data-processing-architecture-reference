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

#include "core/common/concurrent_map/src/concurrent_map.h"

#include <gtest/gtest.h>

#include <atomic>
#include <thread>
#include <vector>

#include "core/common/uuid/src/uuid.h"
#include "core/test/utils/scp_test_base.h"
#include "public/core/test/interface/execution_result_matchers.h"

using google::scp::core::ExecutionResult;
using google::scp::core::common::ConcurrentMap;
using google::scp::core::test::ResultIs;
using google::scp::core::test::ScpTestBase;
using std::atomic;
using std::make_pair;
using std::thread;
using std::vector;
using std::chrono::duration;
using std::chrono::duration_cast;
using std::chrono::milliseconds;
using std::chrono::nanoseconds;
using std::chrono::steady_clock;
using std::this_thread::yield;

namespace google::scp::core::common::test {

class ConcurrentMapTests : public ScpTestBase {};

TEST_F(ConcurrentMapTests, InsertElement) {
  ConcurrentMap<int, int> map;

  int i;
  auto result = map.Insert(make_pair(1, 1), i);

  EXPECT_SUCCESS(result);
  EXPECT_EQ(i, 1);
}

TEST_F(ConcurrentMapTests, InsertExistingElement) {
  ConcurrentMap<int, int> map;

  int i;
  auto result = map.Insert(make_pair(1, 1), i);
  result = map.Insert(make_pair(1, 1), i);

  EXPECT_THAT(result, ResultIs(FailureExecutionResult(
                          errors::SC_CONCURRENT_MAP_ENTRY_ALREADY_EXISTS)));
}

TEST_F(ConcurrentMapTests, DeleteExistingElement) {
  ConcurrentMap<int, int> map;

  int val = 1, key = 2;
  auto result = map.Insert(make_pair(key, val), val);
  result = map.Erase(key);

  EXPECT_SUCCESS(result);

  result = map.Find(key, val);
  EXPECT_THAT(result, ResultIs(FailureExecutionResult(
                          errors::SC_CONCURRENT_MAP_ENTRY_DOES_NOT_EXIST)));
}

TEST_F(ConcurrentMapTests, DeleteNonExistingElement) {
  ConcurrentMap<int, int> map;
  int i = 0;
  auto result = map.Erase(i);
  EXPECT_THAT(result, ResultIs(FailureExecutionResult(
                          errors::SC_CONCURRENT_MAP_ENTRY_DOES_NOT_EXIST)));
}

TEST_F(ConcurrentMapTests, FindAnExistingElement) {
  ConcurrentMap<int, int> map;

  int i;
  int value;
  auto result = map.Insert(make_pair(1, 1), i);
  result = map.Find(i, value);

  EXPECT_SUCCESS(result);
  EXPECT_EQ(value, 1);
}

TEST_F(ConcurrentMapTests, FindAnExistingElementUuid) {
  ConcurrentMap<Uuid, Uuid, UuidCompare> map;

  Uuid uuid_key = Uuid::GenerateUuid();
  Uuid uuid_value = Uuid::GenerateUuid();

  Uuid value;
  auto result = map.Insert(make_pair(uuid_key, uuid_value), uuid_value);
  result = map.Find(uuid_key, value);

  EXPECT_SUCCESS(result);
  EXPECT_EQ(value, uuid_value);
}

TEST_F(ConcurrentMapTests, GetKeysWithAccessor) {
  ConcurrentMap<Uuid, Uuid, UuidCompare> map;

  Uuid uuid_key = Uuid::GenerateUuid();
  Uuid uuid_value = Uuid::GenerateUuid();

  Uuid uuid_key1 = Uuid::GenerateUuid();
  Uuid uuid_value1 = Uuid::GenerateUuid();

  auto result = map.Insert(make_pair(uuid_key, uuid_value), uuid_value);
  EXPECT_SUCCESS(result);

  result = map.Insert(make_pair(uuid_key1, uuid_value1), uuid_value1);
  EXPECT_SUCCESS(result);

  auto start_time = steady_clock::now();

  auto kNumThreads = 1000;
  std::vector<std::thread> work_threads;
  work_threads.reserve(kNumThreads);
  size_t key_size_sum = 0;
  atomic<uint64_t> all_threads_duration{0};
  for (int i = 0; i < kNumThreads; i++) {
    work_threads.emplace_back([&] {
      Uuid value;
      auto st_start_time = steady_clock::now();
      for (auto i = 0; i < 100; i++) {
        result = map.Find(uuid_key1, value);
        EXPECT_SUCCESS(result);
      }
      auto st_end_time = steady_clock::now();

      all_threads_duration.fetch_add(
          duration_cast<nanoseconds>(st_end_time - st_start_time).count());
    });
  }

  for (auto& t : work_threads) {
    if (t.joinable()) {
      t.join();
    }
  }

  auto end_time = steady_clock::now();

  std::cout << "Threads Find Time taken (ms): " << all_threads_duration / 10e6
            << " ms" << std::endl;

  auto ms = duration_cast<milliseconds>(end_time - start_time);
  std::cout << "Time taken (ms): " << ms.count() << " ms" << std::endl;

  vector<Uuid> keys;
  result = map.Keys(keys);
  EXPECT_SUCCESS(result);

  if (keys[0] == uuid_key) {
    EXPECT_EQ(keys[1], uuid_key1);
  } else if (keys[0] == uuid_key1) {
    EXPECT_EQ(keys[1], uuid_key);
  } else {
    EXPECT_EQ(true, false);
  }
}

TEST_F(ConcurrentMapTests, GetKeysWithConstAccessor) {
  ConcurrentMap<Uuid, Uuid, UuidCompare> map =
      ConcurrentMap<Uuid, Uuid, UuidCompare>(true);

  Uuid uuid_key = Uuid::GenerateUuid();
  Uuid uuid_value = Uuid::GenerateUuid();

  Uuid uuid_key1 = Uuid::GenerateUuid();
  Uuid uuid_value1 = Uuid::GenerateUuid();

  auto result = map.Insert(make_pair(uuid_key, uuid_value), uuid_value);
  EXPECT_SUCCESS(result);

  result = map.Insert(make_pair(uuid_key1, uuid_value1), uuid_value1);
  EXPECT_SUCCESS(result);

  auto start_time = steady_clock::now();

  auto kNumThreads = 1000;
  std::vector<std::thread> work_threads;
  work_threads.reserve(kNumThreads);
  size_t key_size_sum = 0;
  atomic<uint64_t> all_threads_duration{0};
  for (int i = 0; i < kNumThreads; i++) {
    work_threads.emplace_back([&] {
      Uuid value;
      auto st_start_time = steady_clock::now();
      for (auto i = 0; i < 100; i++) {
        result = map.Find(uuid_key1, value);
        EXPECT_SUCCESS(result);
      }
      auto st_end_time = steady_clock::now();

      all_threads_duration.fetch_add(
          duration_cast<nanoseconds>(st_end_time - st_start_time).count());
    });
  }

  for (auto& t : work_threads) {
    if (t.joinable()) {
      t.join();
    }
  }

  auto end_time = steady_clock::now();

  std::cout << "Threads Find Time taken (ms): " << all_threads_duration / 10e6
            << " ms" << std::endl;

  auto ms = duration_cast<milliseconds>(end_time - start_time);
  std::cout << "Time taken (ms): " << ms.count() << " ms" << std::endl;

  vector<Uuid> keys;
  result = map.Keys(keys);
  EXPECT_SUCCESS(result);

  if (keys[0] == uuid_key) {
    EXPECT_EQ(keys[1], uuid_key1);
  } else if (keys[0] == uuid_key1) {
    EXPECT_EQ(keys[1], uuid_key);
  } else {
    EXPECT_EQ(true, false);
  }
}
}  // namespace google::scp::core::common::test
