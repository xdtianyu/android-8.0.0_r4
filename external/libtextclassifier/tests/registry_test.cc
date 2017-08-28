/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <memory>

#include "tests/functions.h"
#include "gtest/gtest.h"

namespace libtextclassifier {
namespace nlp_core {
namespace functions {

TEST(RegistryTest, InstantiateFunctionsByName) {
  // First, we need to register the functions we are interested in:
  Exp::RegisterClass();
  Inc::RegisterClass();
  Cos::RegisterClass();

  // RegisterClass methods can be called in any order, even multiple times :)
  Cos::RegisterClass();
  Inc::RegisterClass();
  Inc::RegisterClass();
  Cos::RegisterClass();
  Inc::RegisterClass();

  // NOTE: we intentionally do not register Dec.  Attempts to create an instance
  // of that function by name should fail.

  // Instantiate a few functions and check that the created functions produce
  // the expected results for a few sample values.
  std::unique_ptr<Function> f1(Function::Create("cos"));
  ASSERT_NE(f1, nullptr);
  std::unique_ptr<Function> f2(Function::Create("exp"));
  ASSERT_NE(f2, nullptr);
  EXPECT_NEAR(f1->Evaluate(-3), -0.9899, 0.0001);
  EXPECT_NEAR(f2->Evaluate(2.3), 9.9741, 0.0001);

  std::unique_ptr<IntFunction> f3(IntFunction::Create("inc"));
  ASSERT_NE(f3, nullptr);
  EXPECT_EQ(f3->Evaluate(7), 8);

  // Instantiating unknown functions should return nullptr, but not crash
  // anything.
  EXPECT_EQ(Function::Create("mambo"), nullptr);

  // Functions that are defined in the code, but are not registered are unknown.
  EXPECT_EQ(IntFunction::Create("dec"), nullptr);

  // Function and IntFunction use different registries.
  EXPECT_EQ(IntFunction::Create("exp"), nullptr);
}

}  // namespace functions
}  // namespace nlp_core
}  // namespace libtextclassifier
