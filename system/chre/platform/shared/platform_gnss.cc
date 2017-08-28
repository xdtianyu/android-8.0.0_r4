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

#include "chre/platform/platform_gnss.h"

#include <cinttypes>

#include "chre/core/event_loop_manager.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/platform/log.h"

namespace chre {

PlatformGnss::PlatformGnss() {
  mGnssApi = chrePalGnssGetApi(CHRE_PAL_GNSS_API_CURRENT_VERSION);
  if (mGnssApi != nullptr) {
    mGnssCallbacks.requestStateResync =
        PlatformGnssBase::requestStateResyncCallback;
    mGnssCallbacks.locationStatusChangeCallback =
        PlatformGnssBase::locationStatusChangeCallback;
    mGnssCallbacks.locationEventCallback =
        PlatformGnssBase::locationEventCallback;
    mGnssCallbacks.measurementStatusChangeCallback =
        PlatformGnssBase::measurementStatusChangeCallback;
    mGnssCallbacks.measurementEventCallback =
        PlatformGnssBase::measurementEventCallback;
    if (!mGnssApi->open(&gChrePalSystemApi, &mGnssCallbacks)) {
      LOGE("GNSS PAL open returned false");
      mGnssApi = nullptr;
    }
  } else {
    LOGW("Requested GNSS PAL (version %08" PRIx32 ") not found",
         CHRE_PAL_GNSS_API_CURRENT_VERSION);
  }
}

PlatformGnss::~PlatformGnss() {
  if (mGnssApi != nullptr) {
    mGnssApi->close();
  }
}

uint32_t PlatformGnss::getCapabilities() {
  if (mGnssApi != nullptr) {
    return mGnssApi->getCapabilities();
  } else {
    return CHRE_GNSS_CAPABILITIES_NONE;
  }
}

void PlatformGnssBase::requestStateResyncCallback() {
  // TODO: Implement this.
}

void PlatformGnssBase::locationStatusChangeCallback(bool enabled,
                                                    uint8_t errorCode) {
  // TODO: Implement this.
}

void PlatformGnssBase::locationEventCallback(
    struct chreGnssLocationEvent *event) {
  // TODO: Implement this.
}

void PlatformGnssBase::measurementStatusChangeCallback(bool enabled,
                                                       uint8_t errorCode) {
  // TODO: Implement this.
}

void PlatformGnssBase::measurementEventCallback(
    struct chreGnssDataEvent *event) {
  // TODO: Implement this.
}

}  // namespace chre
