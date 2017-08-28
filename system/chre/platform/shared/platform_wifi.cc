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

#include "chre/platform/platform_wifi.h"

#include <cinttypes>

#include "chre/core/event_loop_manager.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/platform/log.h"

namespace chre {

PlatformWifi::PlatformWifi() {
  mWifiApi = chrePalWifiGetApi(CHRE_PAL_WIFI_API_CURRENT_VERSION);
  if (mWifiApi != nullptr) {
    mWifiCallbacks.scanMonitorStatusChangeCallback =
        PlatformWifi::scanMonitorStatusChangeCallback;
    mWifiCallbacks.scanResponseCallback =
        PlatformWifiBase::scanResponseCallback;
    mWifiCallbacks.scanEventCallback =
        PlatformWifiBase::scanEventCallback;
    if (!mWifiApi->open(&gChrePalSystemApi, &mWifiCallbacks)) {
      LOGE("WiFi PAL open returned false");
      mWifiApi = nullptr;
    }
  } else {
    LOGW("Requested Wifi PAL (version %08" PRIx32 ") not found",
         CHRE_PAL_WIFI_API_CURRENT_VERSION);
  }
}

PlatformWifi::~PlatformWifi() {
  if (mWifiApi != nullptr) {
    mWifiApi->close();
  }
}

uint32_t PlatformWifi::getCapabilities() {
  if (mWifiApi != nullptr) {
    return mWifiApi->getCapabilities();
  } else {
    return CHRE_WIFI_CAPABILITIES_NONE;
  }
}

bool PlatformWifi::configureScanMonitor(bool enable) {
  if (mWifiApi != nullptr) {
    return mWifiApi->configureScanMonitor(enable);
  } else {
    return false;
  }
}

bool PlatformWifi::requestScan(const struct chreWifiScanParams *params) {
  if (mWifiApi != nullptr) {
    return mWifiApi->requestScan(params);
  } else {
    return false;
  }
}

void PlatformWifi::releaseScanEvent(struct chreWifiScanEvent *event) {
  if (mWifiApi != nullptr) {
    mWifiApi->releaseScanEvent(event);
  }
}

void PlatformWifiBase::scanMonitorStatusChangeCallback(bool enabled,
                                                       uint8_t errorCode) {
  EventLoopManagerSingleton::get()->getWifiRequestManager()
      .handleScanMonitorStateChange(enabled, errorCode);
}

void PlatformWifiBase::scanResponseCallback(bool pending, uint8_t errorCode) {
  EventLoopManagerSingleton::get()->getWifiRequestManager()
      .handleScanResponse(pending, errorCode);
}

void PlatformWifiBase::scanEventCallback(struct chreWifiScanEvent *event) {
  EventLoopManagerSingleton::get()->getWifiRequestManager()
      .handleScanEvent(event);
}

}  // namespace chre
