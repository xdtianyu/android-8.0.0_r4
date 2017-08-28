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

#ifndef ANDROID_VINTF_UTILS_H
#define ANDROID_VINTF_UTILS_H

#include <fstream>
#include <iostream>
#include <sstream>

#include <android-base/logging.h>
#include <utils/Errors.h>

#include "parse_xml.h"

namespace android {
namespace vintf {
namespace details {

// Return the file from the given location as a string.
//
// This class can be used to create a mock for overriding.
class FileFetcher {
   public:
    virtual ~FileFetcher() {}
    virtual status_t fetch(const std::string& path, std::string& fetched) {
        std::ifstream in;

        in.open(path);
        if (!in.is_open()) {
            LOG(WARNING) << "Cannot open " << path;
            return INVALID_OPERATION;
        }

        std::stringstream ss;
        ss << in.rdbuf();
        fetched = ss.str();

        return OK;
    }
};

extern FileFetcher* gFetcher;

class PartitionMounter {
   public:
    virtual ~PartitionMounter() {}
    virtual status_t mountSystem() const { return OK; }
    virtual status_t mountVendor() const { return OK; }
    virtual status_t umountSystem() const { return OK; }
    virtual status_t umountVendor() const { return OK; }
};

extern PartitionMounter* gPartitionMounter;

template <typename T>
status_t fetchAllInformation(const std::string& path, const XmlConverter<T>& converter,
                             T* outObject) {
    std::string info;

    if (gFetcher == nullptr) {
        // Should never happen.
        return NO_INIT;
    }

    status_t result = gFetcher->fetch(path, info);

    if (result != OK) {
        return result;
    }

    bool success = converter(outObject, info);
    if (!success) {
        LOG(ERROR) << "Illformed file: " << path << ": "
                   << converter.lastError();
        return BAD_VALUE;
    }
    return OK;
}

}  // namespace details
}  // namespace vintf
}  // namespace android



#endif
