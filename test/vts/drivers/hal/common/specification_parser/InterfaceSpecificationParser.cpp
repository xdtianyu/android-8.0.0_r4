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

#include "specification_parser/InterfaceSpecificationParser.h"

#include <stdio.h>

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include <google/protobuf/message.h>
#include <google/protobuf/text_format.h>

#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

using namespace std;

namespace android {
namespace vts {

bool InterfaceSpecificationParser::parse(
    const char* file_path, ComponentSpecificationMessage* is_message) {
  ifstream in_file(file_path);
  stringstream str_stream;
  if (!in_file.is_open()) {
    cerr << "Unable to open file. " << file_path << endl;
    return false;
  }
  str_stream << in_file.rdbuf();
  in_file.close();
  const string data = str_stream.str();

  if (!google::protobuf::TextFormat::MergeFromString(data, is_message)) {
    cerr << __FUNCTION__ << ": Can't parse a given proto file " << file_path
         << "." << endl;
    cerr << data << endl;
    return false;
  }
  return true;
}

}  // namespace vts
}  // namespace android
