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

#include "fuzz_tester/FuzzerCallbackBase.h"

#include <dirent.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>

#include <iostream>
#include <string>
#include <vector>

#include <VtsDriverCommUtil.h>
#include "test/vts/proto/ComponentSpecificationMessage.pb.h"
#include "test/vts/proto/AndroidSystemControlMessage.pb.h"

#include "component_loader/DllLoader.h"
#include "utils/InterfaceSpecUtil.h"

using namespace std;

namespace android {
namespace vts {

static std::map<string, string> id_map_;

FuzzerCallbackBase::FuzzerCallbackBase() {}

FuzzerCallbackBase::~FuzzerCallbackBase() {}

bool FuzzerCallbackBase::Register(const VariableSpecificationMessage& message) {
  cout << __func__ << " type = " << message.type() << endl;
  if (!message.is_callback()) {
    cerr << __func__ << " ERROR: argument is not a callback." << endl;
    return false;
  }

  if (!message.has_type() || message.type() != TYPE_FUNCTION_POINTER) {
    cerr << __func__ << " ERROR: inconsistent message." << endl;
    return false;
  }

  for (const auto& func_pt : message.function_pointer()) {
    cout << __func__ << " map[" << func_pt.function_name()
         << "] = " << func_pt.id() << endl;
    id_map_[func_pt.function_name()] = func_pt.id();
  }
  return true;
}

const char* FuzzerCallbackBase::GetCallbackID(const string& name) {
  // TODO: handle when not found.
  cout << __func__ << ":" << __LINE__ << " " << name << endl;
  cout << __func__ << ":" << __LINE__ << " returns '" << id_map_[name].c_str()
       << "'" << endl;
  return id_map_[name].c_str();
}

void FuzzerCallbackBase::RpcCallToAgent(
    const AndroidSystemCallbackRequestMessage& message,
    const string& callback_socket_name) {
  cout << __func__ << ":" << __LINE__ << " id = '" << message.id() << "'"
       << endl;
  if (message.id().empty() || callback_socket_name.empty()) {
    cout << "Message ID: " << message.id() << endl
         << "Callback socket name: " << callback_socket_name << endl
         << "Abort callback forwarding." << endl;
    return;
  }
  VtsDriverCommUtil util;
  if (!util.Connect(callback_socket_name)) exit(-1);
  util.VtsSocketSendMessage(message);
  util.Close();
}

}  // namespace vts
}  // namespace android
