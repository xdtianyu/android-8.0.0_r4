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

#include "chre/platform/platform_nanoapp.h"

#include "chre/platform/assert.h"
#include "chre/platform/log.h"
#include "chre/platform/memory.h"
#include "chre/platform/shared/nanoapp_support_lib_dso.h"
#include "chre_api/chre/version.h"

#include "dlfcn.h"

#include <inttypes.h>
#include <string.h>

namespace chre {

namespace {

/**
 * Performs sanity checks on the app info structure included in a dynamically
 * loaded nanoapp.
 *
 * @param expectedAppId
 * @param expectedAppVersion
 * @param appInfo
 *
 * @return true if validation was successful
 */
bool validateAppInfo(uint64_t expectedAppId, uint32_t expectedAppVersion,
                     const struct chreNslNanoappInfo *appInfo) {
  uint32_t ourApiMajorVersion = CHRE_EXTRACT_MAJOR_VERSION(chreGetApiVersion());
  uint32_t targetApiMajorVersion = CHRE_EXTRACT_MAJOR_VERSION(
      appInfo->targetApiVersion);

  bool success = false;
  if (appInfo->magic != CHRE_NSL_NANOAPP_INFO_MAGIC) {
    LOGE("Invalid app info magic: got 0x%08" PRIx32 " expected 0x%08" PRIx32,
         appInfo->magic, CHRE_NSL_NANOAPP_INFO_MAGIC);
  } else if (appInfo->appId == 0) {
    LOGE("Rejecting invalid app ID 0");
  } else if (expectedAppId != appInfo->appId) {
    LOGE("Expected app ID (0x%016" PRIx64 ") doesn't match internal one (0x%016"
         PRIx64 ")", expectedAppId, appInfo->appId);
  } else if (expectedAppVersion != appInfo->appVersion) {
    LOGE("Expected app version (0x%" PRIx32 ") doesn't match internal one (0x%"
         PRIx32 ")", expectedAppVersion, appInfo->appVersion);
  } else if (targetApiMajorVersion != ourApiMajorVersion) {
    LOGE("App targets a different major API version (%" PRIu32 ") than what we "
         "provide (%" PRIu32 ")", targetApiMajorVersion, ourApiMajorVersion);
  } else if (strlen(appInfo->name) > CHRE_NSL_DSO_NANOAPP_STRING_MAX_LEN) {
    LOGE("App name is too long");
  } else if (strlen(appInfo->name) > CHRE_NSL_DSO_NANOAPP_STRING_MAX_LEN) {
    LOGE("App vendor is too long");
  } else {
    success = true;
  }

  return success;
}

}  // anonymous namespace

PlatformNanoapp::~PlatformNanoapp() {
  closeNanoapp();
  if (mAppBinary != nullptr) {
    memoryFree(mAppBinary);
  }
}

bool PlatformNanoapp::start() {
  // Invoke the start entry point after successfully opening the app
  return (mIsStatic || openNanoapp()) ? mAppInfo->entryPoints.start() : false;
}

void PlatformNanoapp::handleEvent(uint32_t senderInstanceId,
                                  uint16_t eventType,
                                  const void *eventData) {
  mAppInfo->entryPoints.handleEvent(senderInstanceId, eventType, eventData);
}

void PlatformNanoapp::end() {
  mAppInfo->entryPoints.end();
  closeNanoapp();
}

bool PlatformNanoappBase::loadFromBuffer(uint64_t appId, uint32_t appVersion,
                                         const void *appBinary,
                                         size_t appBinaryLen) {
  CHRE_ASSERT(!isLoaded());
  bool success = false;
  constexpr size_t kMaxAppSize = 2 * 1024 * 1024;  // 2 MiB

  if (appBinaryLen > kMaxAppSize) {
    LOGE("Rejecting app size %zu above limit %zu", appBinaryLen, kMaxAppSize);
  } else {
    mAppBinary = memoryAlloc(appBinaryLen);
    if (mAppBinary == nullptr) {
      LOGE("Couldn't allocate %zu byte buffer for nanoapp 0x%016" PRIx64,
           appBinaryLen, appId);
    } else {
      mExpectedAppId = appId;
      mExpectedAppVersion = appVersion;
      mAppBinaryLen = appBinaryLen;
      memcpy(mAppBinary, appBinary, appBinaryLen);
      success = true;
    }
  }

  return success;
}

void PlatformNanoappBase::loadStatic(const struct chreNslNanoappInfo *appInfo) {
  CHRE_ASSERT(!isLoaded());
  mIsStatic = true;
  mAppInfo = appInfo;
}

bool PlatformNanoappBase::isLoaded() const {
  return (mIsStatic || mAppBinary != nullptr);
}

void PlatformNanoappBase::closeNanoapp() {
  if (mDsoHandle != nullptr) {
    if (dlclose(mDsoHandle) != 0) {
      const char *name = (mAppInfo != nullptr) ? mAppInfo->name : "unknown";
      LOGE("dlclose of %s failed: %s", name, dlerror());
    }
    mDsoHandle = nullptr;
  }
}

bool PlatformNanoappBase::openNanoapp() {
  bool success = false;

  // Populate a filename string (just a requirement of the dlopenbuf API)
  constexpr size_t kMaxFilenameLen = 17;
  char filename[kMaxFilenameLen];
  snprintf(filename, sizeof(filename), "%016" PRIx64, mExpectedAppId);

  CHRE_ASSERT(mAppBinary != nullptr);
  CHRE_ASSERT_LOG(mDsoHandle == nullptr, "Re-opening nanoapp");
  mDsoHandle = dlopenbuf(
      filename, static_cast<const char *>(mAppBinary),
      static_cast<int>(mAppBinaryLen), RTLD_NOW);
  if (mDsoHandle == nullptr) {
    LOGE("Failed to load nanoapp: %s", dlerror());
  } else {
    mAppInfo = static_cast<const struct chreNslNanoappInfo *>(
        dlsym(mDsoHandle, CHRE_NSL_DSO_NANOAPP_INFO_SYMBOL_NAME));
    if (mAppInfo == nullptr) {
      LOGE("Failed to find app info symbol: %s", dlerror());
    } else {
      success = validateAppInfo(mExpectedAppId, mExpectedAppVersion, mAppInfo);
      if (!success) {
        mAppInfo = nullptr;
      } else {
        LOGI("Successfully loaded nanoapp: %s (0x%016" PRIx64 ") version 0x%"
             PRIx32, mAppInfo->name, mAppInfo->appId, mAppInfo->appVersion);
      }
    }
  }

  return success;
}

uint64_t PlatformNanoapp::getAppId() const {
  return (mAppInfo != nullptr) ? mAppInfo->appId : mExpectedAppId;
}

uint32_t PlatformNanoapp::getAppVersion() const {
  return (mAppInfo != nullptr) ? mAppInfo->appVersion : mExpectedAppVersion;
}

uint32_t PlatformNanoapp::getTargetApiVersion() const {
  return (mAppInfo != nullptr) ? mAppInfo->targetApiVersion : 0;
}

bool PlatformNanoapp::isSystemNanoapp() const {
  // Right now, we assume that system nanoapps are always static nanoapps. Since
  // mAppInfo can only be null either prior to loading the app (in which case
  // this function is not expected to return a valid value anyway), or when a
  // dynamic nanoapp is not running, "false" is the correct return value in that
  // case.
  return (mAppInfo != nullptr) ? mAppInfo->isSystemNanoapp : false;
}

}  // namespace chre
