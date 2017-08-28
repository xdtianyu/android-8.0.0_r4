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

#include "ManifestHal.h"
#include <unordered_set>

namespace android {
namespace vintf {

bool ManifestHal::isValid() const {
    std::unordered_set<size_t> existing;
    for (const auto &v : versions) {
        if (existing.find(v.majorVer) != existing.end()) {
            return false;
        }
        existing.insert(v.majorVer);
    }
    return transportArch.isValid();
}

bool ManifestHal::operator==(const ManifestHal &other) const {
    if (format != other.format)
        return false;
    if (name != other.name)
        return false;
    if (versions != other.versions)
        return false;
    // do not compare impl
    return true;
}

} // namespace vintf
} // namespace android
