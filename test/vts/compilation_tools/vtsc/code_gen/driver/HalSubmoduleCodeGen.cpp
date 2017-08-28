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

#include "code_gen/driver/HalSubmoduleCodeGen.h"

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

#include "VtsCompilerUtils.h"

using namespace std;
using namespace android;

namespace android {
namespace vts {

const char* const HalSubmoduleCodeGen::kInstanceVariableName = "submodule_";

void HalSubmoduleCodeGen::GenerateClassConstructionFunction(Formatter& out,
    const ComponentSpecificationMessage& /*message*/,
    const string& fuzzer_extended_class_name) {
  out << fuzzer_extended_class_name
      << "() : FuzzerBase(HAL_CONVENTIONAL_SUBMODULE) {}\n";
}

void HalSubmoduleCodeGen::GenerateAdditionalFuctionDeclarations(Formatter& out,
    const ComponentSpecificationMessage& message,
    const string& /*fuzzer_extended_class_name*/) {
  string component_name = GetComponentName(message);
  out << "void SetSubModule(" << component_name << "* submodule) {" << "\n";
  out.indent();
  out << "submodule_ = submodule;" << "\n";
  out.unindent();
  out << "}" << "\n" << "\n";
}

void HalSubmoduleCodeGen::GeneratePrivateMemberDeclarations(Formatter& out,
    const ComponentSpecificationMessage& message) {
  out << message.original_data_structure_name() << "* submodule_;\n";
}

}  // namespace vts
}  // namespace android
