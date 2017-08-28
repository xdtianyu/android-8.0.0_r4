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

#ifndef ANDROID_VINTF_HAL_INTERFACE_H_
#define ANDROID_VINTF_HAL_INTERFACE_H_

#include <set>
#include <string>

namespace android {
namespace vintf {

// manifest.hal.interface element / compatibility-matrix.hal.interface element
struct HalInterface {
    std::string name;
    std::set<std::string> instances;
};

bool operator==(const HalInterface&, const HalInterface&);

} // namespace vintf
} // namespace android

#endif // ANDROID_VINTF_HAL_INTERFACE_H_
