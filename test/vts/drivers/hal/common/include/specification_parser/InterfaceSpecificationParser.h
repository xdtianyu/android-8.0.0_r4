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

#ifndef __VTS_SYSFUZZER_COMMON_SPECPARSER_IFSPECPARSER_H__
#define __VTS_SYSFUZZER_COMMON_SPECPARSER_IFSPECPARSER_H__

#include <string>

#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

using namespace std;

namespace android {
namespace vts {

// Main class to parse an interface specification from a file.
class InterfaceSpecificationParser {
 public:
  InterfaceSpecificationParser() {}

  // Parses the given proto text file (1st arg). The parsed result is stored in
  // the 2nd arg. Returns true iff successful.
  static bool parse(const char* file_path,
                    ComponentSpecificationMessage* is_message);
};

}  // namespace vts
}  // namespace android

#endif  // __VTS_SYSFUZZER_COMMON_SPECPARSER_IFSPECPARSER_H__
