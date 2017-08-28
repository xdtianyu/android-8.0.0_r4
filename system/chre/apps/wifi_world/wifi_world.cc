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

#include <chre.h>
#include <cinttypes>

#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"
#include "chre/util/nanoapp/wifi.h"

#define LOG_TAG "[WifiWorld]"

//#define WIFI_WORLD_VERBOSE_WIFI_RESULT_LOGS

#ifdef CHRE_NANOAPP_INTERNAL
namespace chre {
namespace {
#endif  // CHRE_NANOAPP_INTERNAL

//! A dummy cookie to pass into the configure scan monitoring async request.
const uint32_t kScanMonitoringCookie = 0x1337;

//! A dummy cookie to pass into request scan async.
const uint32_t kOnDemandScanCookie = 0xcafe;

//! The interval for on-demand wifi scans.
const Nanoseconds kWifiScanInterval = Nanoseconds(Seconds(10));

//! A handle for the cyclic timer to request periodic on-demand wifi-scans.
uint32_t gWifiScanTimerHandle;

namespace {

/**
 * Logs a CHRE wifi scan result.
 *
 * @param result the scan result to log.
 */
void logChreWifiResult(const chreWifiScanResult& result) {
  const char *ssidStr = "<non-printable>";
  char ssidBuffer[kMaxSsidStrLen];
  if (result.ssidLen == 0) {
    ssidStr = "<empty>";
  } else if (parseSsidToStr(ssidBuffer, kMaxSsidStrLen,
                            result.ssid, result.ssidLen)) {
    ssidStr = ssidBuffer;
  }

  const char *bssidStr = "<non-printable>";
  char bssidBuffer[kBssidStrLen];
  if (parseBssidToStr(result.bssid, bssidBuffer, kBssidStrLen)) {
    bssidStr = bssidBuffer;
  }

  LOGI("Found network with SSID: %s", ssidStr);
#ifdef WIFI_WORLD_VERBOSE_WIFI_RESULT_LOGS
  LOGI("  age (ms): %" PRIu32, result.ageMs);
  LOGI("  capability info: %" PRIx16, result.capabilityInfo);
  LOGI("  bssid: %s", bssidStr);
  LOGI("  flags: %" PRIx8, result.flags);
  LOGI("  rssi: %" PRId8 "dBm", result.rssi);
  LOGI("  band: %s (%" PRIu8 ")", parseChreWifiBand(result.band), result.band);
  LOGI("  primary channel: %" PRIu32, result.primaryChannel);
  LOGI("  center frequency primary: %" PRIu32, result.centerFreqPrimary);
  LOGI("  center frequency secondary: %" PRIu32, result.centerFreqSecondary);
  LOGI("  channel width: %" PRIu8, result.channelWidth);
  LOGI("  security mode: %" PRIx8, result.securityMode);
#endif  // WIFI_WORLD_VERBOSE_WIFI_RESULT_LOGS
}

/**
 * Handles the result of an asynchronous request for a wifi resource.
 *
 * @param result a pointer to the event structure containing the result of the
 * request.
 */
void handleWifiAsyncResult(const chreAsyncResult *result) {
  if (result->requestType == CHRE_WIFI_REQUEST_TYPE_CONFIGURE_SCAN_MONITOR) {
    if (result->success) {
      LOGI("Successfully requested wifi scan monitoring");
    } else {
      LOGI("Error requesting wifi scan monitoring with %" PRIu8,
           result->errorCode);
    }

    if (result->cookie != &kScanMonitoringCookie) {
      LOGE("Scan monitoring request cookie mismatch");
    }
  } else if (result->requestType == CHRE_WIFI_REQUEST_TYPE_REQUEST_SCAN) {
    if (result->success) {
      LOGI("Successfully requested an on-demand wifi scan");
    } else {
      LOGE("Error requesting an on-demand wifi scan with %" PRIu8,
           result->errorCode);
    }

    if (result->cookie != &kOnDemandScanCookie) {
      LOGE("On-demand scan cookie mismatch");
    }
  }
}

/**
 * Handles a wifi scan event.
 *
 * @param event a pointer to the details of the wifi scan event.
 */
void handleWifiScanEvent(const chreWifiScanEvent *event) {
  for (uint8_t i = 0; i < event->resultCount; i++) {
    const chreWifiScanResult& result = event->results[i];
    logChreWifiResult(result);
  }
}

/**
 * Handles a timer event.
 *
 * @param eventData The cookie passed to the timer request.
 */
void handleTimerEvent(const void *eventData) {
  const uint32_t *timerHandle = static_cast<const uint32_t *>(eventData);
  if (*timerHandle == gWifiScanTimerHandle) {
    if (chreWifiRequestScanAsyncDefault(&kOnDemandScanCookie)) {
      LOGI("Requested a wifi scan successfully");
    } else {
      LOGE("Failed to request a wifi scan");
    }
  } else {
    LOGE("Received invalid timer handle");
  }
}

}  // namespace

bool nanoappStart() {
  LOGI("App started as instance %" PRIu32, chreGetInstanceId());

  const char *wifiCapabilitiesStr;
  uint32_t wifiCapabilities = chreWifiGetCapabilities();
  switch (wifiCapabilities) {
    case CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN
        | CHRE_WIFI_CAPABILITIES_SCAN_MONITORING:
      wifiCapabilitiesStr = "ON_DEMAND_SCAN | SCAN_MONITORING";
      break;
    case CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN:
      wifiCapabilitiesStr = "ON_DEMAND_SCAN";
      break;
    case CHRE_WIFI_CAPABILITIES_SCAN_MONITORING:
      wifiCapabilitiesStr = "SCAN_MONITORING";
      break;
    case CHRE_WIFI_CAPABILITIES_NONE:
      wifiCapabilitiesStr = "NONE";
      break;
    default:
      wifiCapabilitiesStr = "INVALID";
  }

  LOGI("Detected WiFi support as: %s (%" PRIu32 ")",
       wifiCapabilitiesStr, wifiCapabilities);

  if (wifiCapabilities & CHRE_WIFI_CAPABILITIES_SCAN_MONITORING) {
    if (chreWifiConfigureScanMonitorAsync(true, &kScanMonitoringCookie)) {
      LOGI("Scan monitor enable request successful");
    } else {
      LOGE("Error sending scan monitoring request");
    }
  }

  if (wifiCapabilities & CHRE_WIFI_CAPABILITIES_ON_DEMAND_SCAN) {
    // Schedule a timer to send an active wifi scan.
    gWifiScanTimerHandle = chreTimerSet(kWifiScanInterval.toRawNanoseconds(),
                                        &gWifiScanTimerHandle /* data */,
                                        false /* oneShot */);
    if (gWifiScanTimerHandle == CHRE_TIMER_INVALID) {
      LOGE("Failed to set periodic scan timer");
    } else {
      LOGI("Set a timer to request periodic WiFi scans");
    }
  }

  return true;
}

void nanoappHandleEvent(uint32_t senderInstanceId,
                        uint16_t eventType,
                        const void *eventData) {
  switch (eventType) {
    case CHRE_EVENT_WIFI_ASYNC_RESULT:
      handleWifiAsyncResult(static_cast<const chreAsyncResult *>(eventData));
      break;
    case CHRE_EVENT_WIFI_SCAN_RESULT:
      handleWifiScanEvent(static_cast<const chreWifiScanEvent *>(eventData));
      break;
    case CHRE_EVENT_TIMER:
      handleTimerEvent(eventData);
      break;
    default:
      LOGW("Unhandled event type %" PRIu16, eventType);
  }
}

void nanoappEnd() {
  LOGI("Wifi world app stopped");
}

#ifdef CHRE_NANOAPP_INTERNAL
}  // anonymous namespace
}  // namespace chre

#include "chre/util/nanoapp/app_id.h"
#include "chre/platform/static_nanoapp_init.h"

CHRE_STATIC_NANOAPP_INIT(WifiWorld, chre::kWifiWorldAppId, 0);
#endif  // CHRE_NANOAPP_INTERNAL
