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

#ifndef LIBTEXTCLASSIFIER_TESTS_FUNCTIONS_H_
#define LIBTEXTCLASSIFIER_TESTS_FUNCTIONS_H_

#include <math.h>

#include "common/registry.h"

namespace libtextclassifier {
namespace nlp_core {
namespace functions {
// Abstract double -> double function.
class Function : public RegisterableClass<Function> {
 public:
  virtual ~Function() {}
  virtual double Evaluate(double x) = 0;
};

class Cos : public Function {
 public:
  double Evaluate(double x) override { return cos(x); }
  TC_DEFINE_REGISTRATION_METHOD("cos", Cos);
};

class Exp : public Function {
 public:
  double Evaluate(double x) override { return exp(x); }
  TC_DEFINE_REGISTRATION_METHOD("exp", Exp);
};

// Abstract int -> int function.
class IntFunction : public RegisterableClass<IntFunction> {
 public:
  virtual ~IntFunction() {}
  virtual int Evaluate(int k) = 0;
};

class Inc : public IntFunction {
 public:
  int Evaluate(int k) override { return k + 1; }
  TC_DEFINE_REGISTRATION_METHOD("inc", Inc);
};

class Dec : public IntFunction {
 public:
  int Evaluate(int k) override { return k + 1; }
  TC_DEFINE_REGISTRATION_METHOD("dec", Dec);
};
}  // namespace functions

// Should be inside namespace libtextclassifier::nlp_core.
TC_DECLARE_CLASS_REGISTRY_NAME(functions::Function);
TC_DECLARE_CLASS_REGISTRY_NAME(functions::IntFunction);

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_TESTS_FUNCTIONS_H_
