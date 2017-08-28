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

#ifndef CHRE_PLATFORM_LINUX_PLATFORM_NANOAPP_BASE_H_
#define CHRE_PLATFORM_LINUX_PLATFORM_NANOAPP_BASE_H_

#include <cstdint>

#include "chre/util/entry_points.h"

namespace chre {

/**
 * Linux-specific nanoapp functionality.
 */
struct PlatformNanoappBase {
  //! The function pointer of the nanoapp start entry point.
  chreNanoappStartFunction *mStart;

  //! The function pointer of the nanoapp handle event entry point.
  chreNanoappHandleEventFunction *mHandleEvent;

  //! The function pointer of the nanoapp end entry point.
  chreNanoappEndFunction *mEnd;

  uint64_t mAppId;
  uint32_t mAppVersion;
};

}  // namespace chre

#endif  // CHRE_PLATFORM_LINUX_PLATFORM_NANOAPP_BASE_H_
