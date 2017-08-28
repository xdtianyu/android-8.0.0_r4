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

#ifndef CHRE_PLATFORM_PLATFORM_WIFI_H_
#define CHRE_PLATFORM_PLATFORM_WIFI_H_

#include "chre/target_platform/platform_wifi_base.h"

namespace chre {

class PlatformWifi : public PlatformWifiBase {
 public:
  /**
   * Performs platform-specific initialization of the PlatformWifi instance.
   */
  PlatformWifi();

  /**
   * Performs platform-specific deinitialization of the PlatformWifi instance.
   */
  ~PlatformWifi();

  /**
   * Returns the set of WiFi capabilities that the platform has exposed. This
   * may return CHRE_WIFI_CAPABILITIES_NONE if wifi is not supported.
   *
   * @return the WiFi capabilities exposed by this platform.
   */
  uint32_t getCapabilities();

  /**
   * Configures the scan monitoring function of the platform Wifi. For more info
   * see the WiFi PAL documentation. The result of this operation is
   * asynchronous and must be delivered to CHRE by invoking the
   * WifiRequestManager::handleScanMonitorStateChange method.
   *
   * @param enable true to enable listening for scan results.
   * @return true to indicate that the request was accepted.
   */
  bool configureScanMonitor(bool enable);

  /**
   * Requests that the WiFi chipset perform an active wifi scan. Refer to the
   * {@link chrePalWifiApi} struct of the CHRE API which includes further
   * documentation. Note that the implementation of this method may be supplied
   * by the CHRE PAL but is not required to be. The semantics of this
   * implementation, however, must be the same those of the requestScan PAL API.
   *
   * @param params The configuration of the wifi scan.
   * @return true to indicate that the request was accepted.
   */
  bool requestScan(const struct chreWifiScanParams *params);

  /**
   * Releases a previously published wifi scan event. Refer to the
   * {@link chrePalWifiApi} struct of the CHRE API for further documentation.
   *
   * @param event A pointer to an event to be released.
   */
  void releaseScanEvent(struct chreWifiScanEvent *event);
};

}  // namespace chre

#endif  // CHRE_PLATFORM_PLATFORM_WIFI_H_
