/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "utils/InterfaceSpecUtil.h"

#include <iostream>
#include <sstream>
#include <string>

#include "utils/StringUtil.h"
#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

using namespace std;

namespace android {
namespace vts {

string GetFunctionNamePrefix(const ComponentSpecificationMessage& message) {
  stringstream prefix_ss;
  if (message.component_class() != HAL_HIDL) {
    prefix_ss << VTS_INTERFACE_SPECIFICATION_FUNCTION_NAME_PREFIX
              << message.component_class() << "_" << message.component_type()
              << "_" << int(message.component_type_version()) << "_";
  } else {
    string package_as_function_name(message.package());
    ReplaceSubString(package_as_function_name, ".", "_");
    prefix_ss << VTS_INTERFACE_SPECIFICATION_FUNCTION_NAME_PREFIX
        << message.component_class() << "_" << package_as_function_name << "_"
        << int(message.component_type_version()) << "_"
        << message.component_name() << "_";
  }
  return prefix_ss.str();
}

#define DEFAULT_FACTOR 10000

string GetVersionString(float version, bool for_macro) {
  std::ostringstream out;
  if (for_macro) {
    out << "V";
  }
  long version_long = version * DEFAULT_FACTOR;
  out << (version_long / DEFAULT_FACTOR);
  if (!for_macro) {
    out << ".";
  } else {
    out << "_";
  }
  version_long -= (version_long / DEFAULT_FACTOR) * DEFAULT_FACTOR;
  bool first = true;
  long factor = DEFAULT_FACTOR / 10;
  while (first || (version_long > 0 && factor > 1)) {
    out << (version_long / factor);
    version_long -= (version_long / factor) * factor;
    factor /= 10;
    first = false;
  }
  return out.str();
}

}  // namespace vts
}  // namespace android
