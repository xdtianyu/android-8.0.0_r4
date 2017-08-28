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

#ifndef CHRE_PLATFORM_LINUX_STATIC_NANOAPP_INIT_H_
#define CHRE_PLATFORM_LINUX_STATIC_NANOAPP_INIT_H_

#include "chre/core/nanoapp.h"
#include "chre/platform/fatal_error.h"
#include "chre/util/unique_ptr.h"

/**
 * Initializes a static nanoapp that is based on the Linux implementation of
 * PlatformNanoappBase.
 *
 * @param appName the name of the nanoapp. This will be prefixed by gNanoapp
 * when creating the global instance of the nanoapp.
 * @param appId the app's unique 64-bit ID
 */
#define CHRE_STATIC_NANOAPP_INIT(appName, appId, appVersion) \
namespace chre {                                             \
UniquePtr<Nanoapp> *gNanoapp##appName;                       \
                                                             \
__attribute__((constructor))                                 \
static void initializeStaticNanoapp##appName() {             \
  static UniquePtr<Nanoapp> nanoapp = MakeUnique<Nanoapp>(); \
  if (nanoapp.isNull()) {                                    \
    FATAL_ERROR("Failed to allocate nanoapp " #appName);     \
  } else {                                                   \
    nanoapp->mStart = nanoappStart;                          \
    nanoapp->mHandleEvent = nanoappHandleEvent;              \
    nanoapp->mEnd = nanoappEnd;                              \
    nanoapp->mAppId = appId;                                 \
    nanoapp->mAppVersion = appVersion;                       \
    gNanoapp##appName = &nanoapp;                            \
  }                                                          \
}                                                            \
}  /* namespace chre */

#endif  // CHRE_PLATFORM_LINUX_STATIC_NANOAPP_INIT_H_
