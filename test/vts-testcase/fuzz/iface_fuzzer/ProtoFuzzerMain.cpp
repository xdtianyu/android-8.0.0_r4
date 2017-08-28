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

#include "ProtoFuzzerMutator.h"

#include "specification_parser/InterfaceSpecificationParser.h"
#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

#include <unistd.h>

#include <iostream>
#include <memory>
#include <string>
#include <vector>

using std::cout;
using std::endl;
using std::make_unique;
using std::string;
using std::unique_ptr;
using std::vector;

namespace android {
namespace vts {
namespace fuzzer {

// 64-bit random number generator.
static Random random{static_cast<uint64_t>(time(0))};
// Parameters that passed in to fuzzer.
static ProtoFuzzerParams params;
// CompSpec corresponding to the interface targeted by
// fuzzer.
static CompSpec target_comp_spec;
// VTS hal driver used to call functions of targeted interface.
static unique_ptr<FuzzerBase> hal;
// Used to mutate inputs to hal driver.
static unique_ptr<ProtoFuzzerMutator> mutator;

static ProtoFuzzerMutatorConfig mutator_config{
    // Heuristic: values close to 0 are likely to be meaningful scalar input
    // values.
    [](Random &rand) {
      size_t dice_roll = rand(10);
      if (dice_roll < 3) {
        // With probability of 30% return an integer in range [0, 10).
        return rand(10);
      } else if (dice_roll >= 3 && dice_roll < 6) {
        // With probability of 30% return an integer in range [0, 100).
        return rand(100);
      } else if (dice_roll >= 6 && dice_roll < 9) {
        // With probability of 30% return an integer in range [0, 100).
        return rand(1000);
      }
      if (rand(10) == 0) {
        // With probability of 1% return 0xffffffffffffffff.
        return 0xffffffffffffffff;
      }
      // With probability 9% result is uniformly random.
      return rand.Rand();
    },
    // Odds of an enum being treated like a scalar are 1:1000.
    {1, 1000}};

extern "C" int LLVMFuzzerInitialize(int *argc, char ***argv) {
  params = ExtractProtoFuzzerParams(*argc, *argv);
  target_comp_spec =
      FindTargetCompSpec(params.comp_specs_, params.target_iface_);
  mutator = make_unique<ProtoFuzzerMutator>(
      random, ExtractPredefinedTypes(params.comp_specs_), mutator_config);
  hal.reset(
      InitHalDriver(target_comp_spec, params.service_name_, params.get_stub_));
  return 0;
}

extern "C" size_t LLVMFuzzerCustomMutator(uint8_t *data, size_t size,
                                          size_t max_size, unsigned int seed) {
  ExecSpec exec_spec;
  if (!exec_spec.ParseFromArray(data, size)) {
    exec_spec =
        mutator->RandomGen(target_comp_spec.interface(), params.exec_size_);
  } else {
    mutator->Mutate(target_comp_spec.interface(), &exec_spec);
  }
  cout << exec_spec.DebugString() << endl;
  exec_spec.SerializeToArray(data, exec_spec.ByteSize());
  return exec_spec.ByteSize();
}

// TODO(trong): implement a meaningful cross-over mechanism.
size_t LLVMFuzzerCustomCrossOver(const uint8_t *data1, size_t size1,
                                 const uint8_t *data2, size_t size2,
                                 uint8_t *out, size_t max_out_size,
                                 unsigned int seed) {
  memcpy(out, data1, size1);
  return size1;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  ExecSpec exec_spec;
  if (!exec_spec.ParseFromArray(data, size) || exec_spec.api_size() == 0) {
    return 0;
  }
  Execute(hal.get(), exec_spec);
  return 0;
}

}  // namespace fuzzer
}  // namespace vts
}  // namespace android
