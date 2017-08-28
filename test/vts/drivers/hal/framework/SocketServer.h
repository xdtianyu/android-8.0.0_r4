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

#ifndef VTS_AGENT_DRIVER_COMM_BINDER  // socket

#ifndef __VTS_DRIVER_HAL_SOCKET_SERVER_
#define __VTS_DRIVER_HAL_SOCKET_SERVER_

#include <VtsDriverCommUtil.h>

#include "specification_parser/SpecificationBuilder.h"

namespace android {
namespace vts {

class VtsDriverHalSocketServer : public VtsDriverCommUtil {
 public:
  VtsDriverHalSocketServer(android::vts::SpecificationBuilder& spec_builder,
                           const char* lib_path)
      : VtsDriverCommUtil(), spec_builder_(spec_builder), lib_path_(lib_path) {}

  // Start a session to handle a new request.
  bool ProcessOneCommand();

 protected:
  void Exit();

  int32_t LoadHal(const string& path, int target_class, int target_type,
                  float target_version, const string& target_package,
                  const string& target_component_name,
                  const string& hw_binder_service_name,
                  const string& module_name);
  int32_t Status(int32_t type);
  const char* ReadSpecification(
      const string& name, int target_class, int target_type,
      float target_version, const string& target_package);
  const char* Call(const string& arg);
  const char* GetAttribute(const string& arg);
  string ListFunctions() const;

 private:
  android::vts::SpecificationBuilder& spec_builder_;
  const char* lib_path_;
};

extern int StartSocketServer(const string& socket_port_file,
                             android::vts::SpecificationBuilder& spec_builder,
                             const char* lib_path);

}  // namespace vts
}  // namespace android

#endif  // __VTS_DRIVER_HAL_SOCKET_SERVER_

#endif  // VTS_AGENT_DRIVER_COMM_BINDER
