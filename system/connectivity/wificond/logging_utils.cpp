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

#include "wificond/logging_utils.h"

#include <iomanip>
#include <vector>

#include <android-base/macros.h>

using std::string;
using std::stringstream;
using std::vector;

namespace android {
namespace wificond {

string LoggingUtils::GetMacString(const vector<uint8_t>& mac_address) {
  stringstream ss;
  for (const uint8_t& b : mac_address) {
    ss << std::hex << std::setfill('0') << std::setw(2) << static_cast<int>(b);
    if (&b != &mac_address.back()) {
      ss << ":";
    }
  }
  return ss.str();
}

}  // namespace wificond
}  // namespace android
