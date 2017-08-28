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


#ifndef ANDROID_VINTF_MANIFEST_HAL_H
#define ANDROID_VINTF_MANIFEST_HAL_H

#include <string>
#include <vector>
#include <map>

#include "HalFormat.h"
#include "HalInterface.h"
#include "TransportArch.h"
#include "Version.h"

namespace android {
namespace vintf {

// A component of HalManifest.
struct ManifestHal {

    bool operator==(const ManifestHal &other) const;

    HalFormat format = HalFormat::HIDL;
    std::string name;
    std::vector<Version> versions;
    TransportArch transportArch;
    std::map<std::string, HalInterface> interfaces;

    inline bool hasVersion(Version v) const {
        return std::find(versions.begin(), versions.end(), v) != versions.end();
    }
    inline Transport transport() const {
        return transportArch.transport;
    }

private:
    friend struct LibVintfTest;
    friend struct ManifestHalConverter;
    friend struct HalManifest;
    friend bool parse(const std::string &s, ManifestHal *hal);

    // Whether this hal is a valid one. Note that an empty ManifestHal
    // (constructed via ManifestHal()) is valid.
    bool isValid() const;
};

} // namespace vintf
} // namespace android

#endif // ANDROID_VINTF_MANIFEST_HAL_H
