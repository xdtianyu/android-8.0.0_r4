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

#ifndef ANDROID_HARDWARE_ISERVICE_MANAGER_H
#define ANDROID_HARDWARE_ISERVICE_MANAGER_H

#include <string>
#include <utils/StrongPointer.h>

namespace android {

namespace hidl {
namespace manager {
namespace V1_0 {
    struct IServiceManager;
}; // namespace V1_0
}; // namespace manager
}; // namespace hidl

namespace hardware {

// These functions are for internal use by hidl. If you want to get ahold
// of an interface, the best way to do this is by calling IFoo::getService()

sp<::android::hidl::manager::V1_0::IServiceManager> defaultServiceManager();
sp<::android::hidl::manager::V1_0::IServiceManager> getPassthroughServiceManager();

namespace details {
// e.x.: android.hardware.foo@1.0, IFoo, default
void onRegistration(const std::string &packageName,
                    const std::string &interfaceName,
                    const std::string &instanceName);

// e.x.: android.hardware.foo@1.0::IFoo, default
void waitForHwService(const std::string &interface, const std::string &instanceName);
};

}; // namespace hardware
}; // namespace android

#endif // ANDROID_HARDWARE_ISERVICE_MANAGER_H

