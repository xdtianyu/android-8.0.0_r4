/*
 * Copyright 2016 The Android Open Source Project
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

#ifndef __VTS_PROTO_FUZZER_UTILS_H__
#define __VTS_PROTO_FUZZER_UTILS_H__

#include <iostream>
#include <random>
#include <string>
#include <unordered_map>
#include <vector>

#include "fuzz_tester/FuzzerBase.h"
#include "test/vts-testcase/fuzz/iface_fuzzer/proto/ExecutionSpecificationMessage.pb.h"
#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

namespace android {
namespace vts {
namespace fuzzer {

// Use shorter names for convenience.
using CompSpec = ComponentSpecificationMessage;
using ExecSpec = ExecutionSpecificationMessage;
using FuncSpec = FunctionSpecificationMessage;
using IfaceSpec = InterfaceSpecificationMessage;

// VariableSpecificationMessage can be used to describe 3 things: a type
// declaration, a variable declaration, or a runtime variable instance. These
// use cases correspond to TypeSpec, VarSpec, and VarInstance respectively.
using TypeSpec = VariableSpecificationMessage;
using VarInstance = TypeSpec;
using VarSpec = TypeSpec;

using EnumData = EnumDataValueMessage;
using ScalarData = ScalarDataValueMessage;

// 64-bit random number generator.
class Random {
 public:
  Random(uint64_t seed) : rand_(seed) {}
  virtual ~Random() {}

  // Generates a 64-bit random number.
  virtual uint64_t Rand() { return rand_(); }
  // Generates a random number in range [0, n).
  virtual uint64_t operator()(uint64_t n) { return n ? Rand() % n : 0; }

 private:
  // Used to generate a 64-bit Mersenne Twister pseudo-random number.
  std::mt19937_64 rand_;
};

// Additional non-libfuzzer parameters passed to the fuzzer.
struct ProtoFuzzerParams {
  // Number of function calls per execution (fixed throughout fuzzer run).
  size_t exec_size_;
  // VTS specs supplied to the fuzzer.
  std::vector<CompSpec> comp_specs_;
  // Service name of target interface, e.g. "INfc".
  std::string service_name_ = "default";
  // Name of target interface, e.g. "default".
  std::string target_iface_;
  // Controls whether HAL is opened in passthrough or binder mode.
  // Passthrough mode is default. Used for testsing.
  bool get_stub_ = true;
};

// Parses command-line flags to create a ProtoFuzzerParams instance.
ProtoFuzzerParams ExtractProtoFuzzerParams(int, char **);

// Returns CompSpec corresponding to targeted interface.
CompSpec FindTargetCompSpec(const std::vector<CompSpec> &, const std::string &);

// Loads VTS HAL driver library.
FuzzerBase *InitHalDriver(const CompSpec &, std::string, bool);

// Creates a key, value look-up table with keys being names of predefined types,
// and values being their definitions.
std::unordered_map<std::string, TypeSpec> ExtractPredefinedTypes(
    const std::vector<CompSpec> &);

// Call every API from call sequence specified by the
// ExecutionSpecificationMessage.
void Execute(FuzzerBase *, const ExecutionSpecificationMessage &);

}  // namespace fuzzer
}  // namespace vts
}  // namespace android

#endif  // __VTS_PROTO_FUZZER_UTILS_H__
