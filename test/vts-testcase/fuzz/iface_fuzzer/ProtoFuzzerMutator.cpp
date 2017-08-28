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
#include <iostream>

using std::endl;
using std::cerr;
using std::cout;
using std::make_unique;
using std::unordered_map;
using namespace std::placeholders;

namespace android {
namespace vts {
namespace fuzzer {

ProtoFuzzerMutator::ProtoFuzzerMutator(
    Random &rand, unordered_map<string, TypeSpec> predefined_types,
    ProtoFuzzerMutatorConfig mutator_config)
    : rand_(rand),
      predefined_types_(predefined_types),
      mutator_config_(mutator_config) {
  // Default function used for mutation/random generation. Used for types for
  // which the notion of mutation/random generation is not defined, e.g.
  // TYPE_HANDLE, TYPE_HIDL_CALLBACK.
  VarTransformFn default_transform =
      [](const VariableSpecificationMessage &var_spec) {
        return VariableSpecificationMessage{var_spec};
      };

  // Initialize random_gen_fns_ and mutate_fns_ tables.
  random_gen_fns_[TYPE_ARRAY] =
      std::bind(&ProtoFuzzerMutator::ArrayRandomGen, this, _1);
  mutate_fns_[TYPE_ARRAY] =
      std::bind(&ProtoFuzzerMutator::ArrayMutate, this, _1);

  random_gen_fns_[TYPE_ENUM] =
      std::bind(&ProtoFuzzerMutator::EnumRandomGen, this, _1);
  mutate_fns_[TYPE_ENUM] = std::bind(&ProtoFuzzerMutator::EnumMutate, this, _1);

  random_gen_fns_[TYPE_HANDLE] = default_transform;
  mutate_fns_[TYPE_HANDLE] = default_transform;

  random_gen_fns_[TYPE_HIDL_CALLBACK] = default_transform;
  mutate_fns_[TYPE_HIDL_CALLBACK] = default_transform;

  random_gen_fns_[TYPE_SCALAR] =
      std::bind(&ProtoFuzzerMutator::ScalarRandomGen, this, _1);
  mutate_fns_[TYPE_SCALAR] =
      std::bind(&ProtoFuzzerMutator::ScalarMutate, this, _1);

  random_gen_fns_[TYPE_STRUCT] =
      std::bind(&ProtoFuzzerMutator::StructRandomGen, this, _1);
  mutate_fns_[TYPE_STRUCT] =
      std::bind(&ProtoFuzzerMutator::StructMutate, this, _1);

  random_gen_fns_[TYPE_UNION] =
      std::bind(&ProtoFuzzerMutator::UnionRandomGen, this, _1);
  mutate_fns_[TYPE_UNION] =
      std::bind(&ProtoFuzzerMutator::UnionMutate, this, _1);

  random_gen_fns_[TYPE_VECTOR] =
      std::bind(&ProtoFuzzerMutator::VectorRandomGen, this, _1);
  mutate_fns_[TYPE_VECTOR] =
      std::bind(&ProtoFuzzerMutator::VectorMutate, this, _1);
}

ExecSpec ProtoFuzzerMutator::RandomGen(const IfaceSpec &iface_spec,
                                       size_t num_calls) {
  ExecSpec result{};

  for (size_t i = 0; i < num_calls; ++i) {
    size_t num_apis = iface_spec.api_size();
    size_t rand_api_idx = rand_(num_apis);
    FuncSpec rand_api = RandomGen(iface_spec.api(rand_api_idx));
    result.add_api()->Swap(&rand_api);
  }

  return result;
}

void ProtoFuzzerMutator::Mutate(const IfaceSpec &iface_spec,
                                ExecSpec *exec_spec) {
  // Mutate a randomly chosen function call with probability
  // odds_for/(odds_for + odds_against).
  uint64_t odds_for = mutator_config_.func_mutated_.first;
  uint64_t odds_against = mutator_config_.func_mutated_.second;
  uint64_t rand_num = rand_(odds_for + odds_against);

  if (rand_num < odds_for) {
    // Mutate a random function in execution.
    size_t idx = rand_(exec_spec->api_size());
    const FuncSpec &rand_api = exec_spec->api(idx);
    (*exec_spec->mutable_api(idx)) = Mutate(rand_api);
  } else {
    // Generate a random function call in place of randomly chosen function in
    // execution.
    size_t func_idx = rand_(exec_spec->api_size());
    size_t blueprint_idx = rand_(iface_spec.api_size());
    *(exec_spec->mutable_api(func_idx)) =
        RandomGen(iface_spec.api(blueprint_idx));
  }
}

FuncSpec ProtoFuzzerMutator::RandomGen(const FuncSpec &func_spec) {
  FuncSpec result{func_spec};
  // We'll repopulate arg field.
  result.clear_arg();
  result.clear_return_type_hidl();
  for (const auto &var_spec : func_spec.arg()) {
    VarInstance rand_var_spec = RandomGen(var_spec);
    auto *new_var = result.add_arg();
    new_var->Swap(&rand_var_spec);
  }
  return result;
}

FuncSpec ProtoFuzzerMutator::Mutate(const FuncSpec &func_spec) {
  FuncSpec result{func_spec};
  size_t num_args = result.arg_size();
  if (num_args > 0) {
    size_t rand_arg_idx = rand_(num_args);
    VarInstance rand_arg = Mutate(result.arg(rand_arg_idx));
    result.mutable_arg(rand_arg_idx)->Swap(&rand_arg);
  }
  return result;
}

static VariableSpecificationMessage Transform(
    const VariableSpecificationMessage &var_spec,
    unordered_map<VariableType, VarTransformFn> &transform_fns) {
  auto type = var_spec.type();
  auto transform_fn = transform_fns.find(type);
  if (transform_fn == transform_fns.end()) {
    cerr << "Transformation function not found for type: " << type << endl;
    exit(1);
  }
  return transform_fn->second(var_spec);
}

VarInstance ProtoFuzzerMutator::RandomGen(const VarSpec &var_spec) {
  return Transform(var_spec, random_gen_fns_);
}

VarInstance ProtoFuzzerMutator::Mutate(const VarInstance &var_instance) {
  return Transform(var_instance, mutate_fns_);
}

const TypeSpec &ProtoFuzzerMutator::FindPredefinedType(string name) {
  auto type_spec = predefined_types_.find(name);
  if (type_spec == predefined_types_.end()) {
    cerr << "Predefined type not found: " << name << endl;
    exit(1);
  }
  return type_spec->second;
}

}  // namespace fuzzer
}  // namespace vts
}  // namespace android
