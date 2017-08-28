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


#define LOG_TAG "libvintf"
#include <android-base/logging.h>

#include "RuntimeInfo.h"

#include "CompatibilityMatrix.h"
#include "parse_string.h"

#include <dirent.h>
#include <errno.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <fstream>
#include <iostream>
#include <sstream>

#include <cutils/properties.h>
#include <selinux/selinux.h>
#include <zlib.h>

#define PROC_CONFIG "/proc/config.gz"
#define BUFFER_SIZE sysconf(_SC_PAGESIZE)

namespace android {
namespace vintf {

static void removeTrailingComments(std::string *s) {
    size_t sharpPos = s->find('#');
    if (sharpPos != std::string::npos) {
        s->erase(sharpPos);
    }
}
static void trim(std::string *s) {
    auto l = s->begin();
    for (; l != s->end() && std::isspace(*l); ++l);
    s->erase(s->begin(), l);
    auto r = s->rbegin();
    for (; r != s->rend() && std::isspace(*r); ++r);
    s->erase(r.base(), s->end());
}

struct RuntimeInfoFetcher {
    RuntimeInfoFetcher(RuntimeInfo *ki) : mRuntimeInfo(ki) { }
    status_t fetchAllInformation();
private:
    void streamConfig(const char *buf, size_t len);
    void parseConfig(std::string *s);
    status_t fetchVersion();
    status_t fetchKernelConfigs();
    status_t fetchCpuInfo();
    status_t fetchKernelSepolicyVers();
    status_t fetchAvb();
    status_t parseKernelVersion();
    RuntimeInfo *mRuntimeInfo;
    std::string mRemaining;
};

// decompress /proc/config.gz and read its contents.
status_t RuntimeInfoFetcher::fetchKernelConfigs() {
    gzFile f = gzopen(PROC_CONFIG, "rb");
    if (f == NULL) {
        LOG(ERROR) << "Could not open /proc/config.gz: " << errno;
        return -errno;
    }

    char buf[BUFFER_SIZE];
    int len;
    while ((len = gzread(f, buf, sizeof buf)) > 0) {
        streamConfig(buf, len);
    }
    status_t err = OK;
    if (len < 0) {
        int errnum;
        const char *errmsg = gzerror(f, &errnum);
        LOG(ERROR) << "Could not read /proc/config.gz: " << errmsg;
        err = (errnum == Z_ERRNO ? -errno : errnum);
    }

    // stream a "\n" to end the stream to finish the last line.
    streamConfig("\n", 1 /* sizeof "\n" */);

    gzclose(f);
    return err;
}

void RuntimeInfoFetcher::parseConfig(std::string *s) {
    removeTrailingComments(s);
    trim(s);
    if (s->empty()) {
        return;
    }
    size_t equalPos = s->find('=');
    if (equalPos == std::string::npos) {
        LOG(WARNING) << "Unrecognized line in /proc/config.gz: " << *s;
        return;
    }
    std::string key = s->substr(0, equalPos);
    std::string value = s->substr(equalPos + 1);
    if (!mRuntimeInfo->mKernelConfigs.emplace(std::move(key), std::move(value)).second) {
        LOG(WARNING) << "Duplicated key in /proc/config.gz: " << s->substr(0, equalPos);
        return;
    }
}

void RuntimeInfoFetcher::streamConfig(const char *buf, size_t len) {
    const char *begin = buf;
    const char *end = buf;
    const char *stop = buf + len;
    while (end < stop) {
        if (*end == '\n') {
            mRemaining.insert(mRemaining.size(), begin, end - begin);
            parseConfig(&mRemaining);
            mRemaining.clear();
            begin = end + 1;
        }
        end++;
    }
    mRemaining.insert(mRemaining.size(), begin, end - begin);
}

status_t RuntimeInfoFetcher::fetchCpuInfo() {
    // TODO implement this; 32-bit and 64-bit has different format.
    std::ifstream in{"/proc/cpuinfo"};
    if (!in.is_open()) {
        LOG(WARNING) << "Cannot read /proc/cpuinfo";
        return UNKNOWN_ERROR;
    }
    std::stringstream sstream;
    sstream << in.rdbuf();
    mRuntimeInfo->mCpuInfo = sstream.str();
    return OK;
}

status_t RuntimeInfoFetcher::fetchKernelSepolicyVers() {
    int pv;
#ifdef LIBVINTF_TARGET
    pv = security_policyvers();
#else
    pv = 0;
#endif
    if (pv < 0) {
        return pv;
    }
    mRuntimeInfo->mKernelSepolicyVersion = pv;
    return OK;
}

status_t RuntimeInfoFetcher::fetchVersion() {
    struct utsname buf;
    if (uname(&buf)) {
        return -errno;
    }
    mRuntimeInfo->mOsName = buf.sysname;
    mRuntimeInfo->mNodeName = buf.nodename;
    mRuntimeInfo->mOsRelease = buf.release;
    mRuntimeInfo->mOsVersion = buf.version;
    mRuntimeInfo->mHardwareId = buf.machine;

    status_t err = parseKernelVersion();
    if (err != OK) {
        LOG(ERROR) << "Could not parse kernel version from \""
                   << mRuntimeInfo->mOsRelease << "\"";
    }
    return err;
}

status_t RuntimeInfoFetcher::parseKernelVersion() {
    auto pos = mRuntimeInfo->mOsRelease.find('.');
    if (pos == std::string::npos) {
        return UNKNOWN_ERROR;
    }
    pos = mRuntimeInfo->mOsRelease.find('.', pos + 1);
    if (pos == std::string::npos) {
        return UNKNOWN_ERROR;
    }
    pos = mRuntimeInfo->mOsRelease.find_first_not_of("0123456789", pos + 1);
    // no need to check pos == std::string::npos, because substr will handle this
    if (!parse(mRuntimeInfo->mOsRelease.substr(0, pos), &mRuntimeInfo->mKernelVersion)) {
        return UNKNOWN_ERROR;
    }
    return OK;
}

status_t RuntimeInfoFetcher::fetchAvb() {
    char prop[PROPERTY_VALUE_MAX];
    property_get("ro.boot.vbmeta.avb_version", prop, "0.0");
    if (!parse(prop, &mRuntimeInfo->mBootVbmetaAvbVersion)) {
        return UNKNOWN_ERROR;
    }
    property_get("ro.boot.avb_version", prop, "0.0");
    if (!parse(prop, &mRuntimeInfo->mBootAvbVersion)) {
        return UNKNOWN_ERROR;
    }
    return OK;
}

status_t RuntimeInfoFetcher::fetchAllInformation() {
    status_t err;
    if ((err = fetchVersion()) != OK) {
        LOG(WARNING) << "Cannot fetch or parse /proc/version: " << strerror(-err);
    }
    if ((err = fetchKernelConfigs()) != OK) {
        LOG(WARNING) << "Cannot fetch or parse /proc/config.gz: " << strerror(-err);
    }
    if ((err = fetchCpuInfo()) != OK) {
        LOG(WARNING) << "Cannot fetch /proc/cpuinfo: " << strerror(-err);
    }
    if ((err = fetchKernelSepolicyVers()) != OK) {
        LOG(WARNING) << "Cannot fetch kernel sepolicy version: " << strerror(-err);
    }
    if ((err = fetchAvb()) != OK) {
        LOG(WARNING) << "Cannot fetch sepolicy avb version: " << strerror(-err);
    }
    return OK;
}

status_t RuntimeInfo::fetchAllInformation() {
    return RuntimeInfoFetcher(this).fetchAllInformation();
}

} // namespace vintf
} // namespace android
