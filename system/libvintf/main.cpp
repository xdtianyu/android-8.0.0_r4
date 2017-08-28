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

#include <iostream>
#include <vintf/parse_xml.h>
#include <vintf/parse_string.h>
#include <vintf/VintfObject.h>

// A convenience binary to dump information available through libvintf.
int main(int, char **) {
    using namespace ::android::vintf;

    std::cout << "======== Device HAL Manifest =========" << std::endl;

    const HalManifest *vm = VintfObject::GetDeviceHalManifest();
    if (vm != nullptr)
        std::cout << gHalManifestConverter(*vm);

    std::cout << "======== Framework HAL Manifest =========" << std::endl;

    const HalManifest *fm = VintfObject::GetFrameworkHalManifest();
    if (fm != nullptr)
        std::cout << gHalManifestConverter(*fm);

    std::cout << "======== Device Compatibility Matrix =========" << std::endl;

    const CompatibilityMatrix *vcm = VintfObject::GetDeviceCompatibilityMatrix();
    if (vcm != nullptr)
        std::cout << gCompatibilityMatrixConverter(*vcm);

    std::cout << "======== Framework Compatibility Matrix =========" << std::endl;

    const CompatibilityMatrix *fcm = VintfObject::GetFrameworkCompatibilityMatrix();
    if (fcm != nullptr)
        std::cout << gCompatibilityMatrixConverter(*fcm);

    std::cout << "======== Runtime Info =========" << std::endl;

    const RuntimeInfo* ki = VintfObject::GetRuntimeInfo();
    if (ki != nullptr) std::cout << dump(*ki);
    std::cout << std::endl;

    std::cout << "======== Compatibility check =========" << std::endl;
    std::cout << "Device HAL Manifest? " << (vm != nullptr) << std::endl
              << "Device Compatibility Matrix? " << (vcm != nullptr) << std::endl
              << "Framework HAL Manifest? " << (fm != nullptr) << std::endl
              << "Framework Compatibility Matrix? " << (fcm != nullptr) << std::endl;
    std::string error;
    if (vm && fcm) {
        bool compatible = vm->checkCompatibility(*fcm, &error);
        std::cout << "Device HAL Manifest <==> Framework Compatibility Matrix? "
                  << compatible;
        if (!compatible)
            std::cout << ", " << error;
        std::cout << std::endl;
    }
    if (fm && vcm) {
        bool compatible = fm->checkCompatibility(*vcm, &error);
        std::cout << "Framework HAL Manifest <==> Device Compatibility Matrix? "
                  << compatible;
        if (!compatible)
            std::cout << ", " << error;
        std::cout << std::endl;
    }
    if (ki && fcm) {
        bool compatible = ki->checkCompatibility(*fcm, &error);
        std::cout << "Runtime info <==> Framework Compatibility Matrix? " << compatible;
        if (!compatible) std::cout << ", " << error;
        std::cout << std::endl;
    }

    {
        auto compatible = VintfObject::CheckCompatibility({}, &error);
        std::cout << "VintfObject::CheckCompatibility (0 == compatible)? " << compatible;
        if (compatible != COMPATIBLE) std::cout << ", " << error;
        std::cout << std::endl;
    }
}
