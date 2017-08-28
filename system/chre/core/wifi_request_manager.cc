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

#include <cinttypes>

#include "chre/core/event_loop_manager.h"
#include "chre/core/wifi_request_manager.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"

namespace chre {

WifiRequestManager::WifiRequestManager() {
  // Reserve space for at least one scan monitoring nanoapp. This ensures that
  // the first asynchronous push_back will succeed. Future push_backs will be
  // synchronous and failures will be returned to the client.
  if (!mScanMonitorNanoapps.reserve(1)) {
    FATAL_ERROR("Failed to allocate scan monitoring nanoapps list at startup");
  }
}

uint32_t WifiRequestManager::getCapabilities() {
  return mPlatformWifi.getCapabilities();
}

bool WifiRequestManager::configureScanMonitor(Nanoapp *nanoapp, bool enable,
                                              const void *cookie) {
  CHRE_ASSERT(nanoapp);

  bool success = false;
  uint32_t instanceId = nanoapp->getInstanceId();
  if (!mScanMonitorStateTransitions.empty()) {
    success = addScanMonitorRequestToQueue(nanoapp, enable, cookie);
  } else if (scanMonitorIsInRequestedState(enable, instanceId)) {
    // The scan monitor is already in the requested state. A success event can
    // be posted immediately.
    success = postScanMonitorAsyncResultEvent(instanceId, true /* success */,
                                              enable, CHRE_ERROR_NONE, cookie);
  } else if (scanMonitorStateTransitionIsRequired(enable, instanceId)) {
    success = addScanMonitorRequestToQueue(nanoapp, enable, cookie);
    if (success) {
      success = mPlatformWifi.configureScanMonitor(enable);
      if (!success) {
        // TODO: Add a pop_back method.
        mScanMonitorStateTransitions.remove(
            mScanMonitorStateTransitions.size() - 1);
        LOGE("Failed to enable the scan monitor for nanoapp instance %" PRIu32,
             instanceId);
      }
    }
  } else {
    CHRE_ASSERT_LOG(false, "Invalid scan monitor configuration");
  }

  return success;
}

bool WifiRequestManager::requestScan(Nanoapp *nanoapp,
                                     const chreWifiScanParams *params,
                                     const void *cookie) {
  CHRE_ASSERT(nanoapp);

  bool success = false;
  if (!mScanRequestingNanoappInstanceId.has_value()) {
    success = mPlatformWifi.requestScan(params);
    if (success) {
      mScanRequestingNanoappInstanceId = nanoapp->getInstanceId();
      mScanRequestingNanoappCookie = cookie;
    }
  } else {
    LOGE("Active wifi scan request made while a request is in flight");
  }

  return success;
}

void WifiRequestManager::handleScanMonitorStateChange(bool enabled,
                                                      uint8_t errorCode) {
  struct CallbackState {
    bool enabled;
    uint8_t errorCode;
  };

  auto *cbState = memoryAlloc<CallbackState>();
  if (cbState == nullptr) {
    LOGE("Failed to allocate callback state for scan monitor state change");
  } else {
    cbState->enabled = enabled;
    cbState->errorCode = errorCode;

    auto callback = [](uint16_t /*eventType*/, void *eventData) {
      auto *state = static_cast<CallbackState *>(eventData);
      EventLoopManagerSingleton::get()->getWifiRequestManager()
          .handleScanMonitorStateChangeSync(state->enabled, state->errorCode);
      memoryFree(state);
    };

    EventLoopManagerSingleton::get()->deferCallback(
        SystemCallbackType::WifiScanMonitorStateChange, cbState, callback);
  }
}

void WifiRequestManager::handleScanResponse(bool pending,
                                            uint8_t errorCode) {
  struct CallbackState {
    bool pending;
    uint8_t errorCode;
  };

  auto *cbState = memoryAlloc<CallbackState>();
  if (cbState == nullptr) {
    LOGE("Failed to allocate callback state for wifi scan response");
  } else {
    cbState->pending = pending;
    cbState->errorCode = errorCode;

    auto callback = [](uint16_t /*eventType*/, void *eventData) {
      auto *state = static_cast<CallbackState *>(eventData);
      EventLoopManagerSingleton::get()->getWifiRequestManager()
          .handleScanResponseSync(state->pending, state->errorCode);
      memoryFree(state);
    };

    EventLoopManagerSingleton::get()->deferCallback(
        SystemCallbackType::WifiRequestScanResponse, cbState, callback);
  }
}

void WifiRequestManager::handleScanEvent(chreWifiScanEvent *event) {
  auto callback = [](uint16_t eventType, void *eventData) {
    chreWifiScanEvent *scanEvent = static_cast<chreWifiScanEvent *>(eventData);
    EventLoopManagerSingleton::get()->getWifiRequestManager()
        .handleScanEventSync(scanEvent);
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::WifiHandleScanEvent, event, callback);
}

bool WifiRequestManager::scanMonitorIsEnabled() const {
  return !mScanMonitorNanoapps.empty();
}

bool WifiRequestManager::nanoappHasScanMonitorRequest(
    uint32_t instanceId, size_t *nanoappIndex) const {
  size_t index = mScanMonitorNanoapps.find(instanceId);
  bool hasScanMonitorRequest = (index != mScanMonitorNanoapps.size());
  if (hasScanMonitorRequest && nanoappIndex != nullptr) {
    *nanoappIndex = index;
  }

  return hasScanMonitorRequest;
}

bool WifiRequestManager::scanMonitorIsInRequestedState(
    bool requestedState, bool nanoappHasRequest) const {
  return (requestedState == scanMonitorIsEnabled() || (!requestedState
      && (!nanoappHasRequest || mScanMonitorNanoapps.size() > 1)));
}

bool WifiRequestManager::scanMonitorStateTransitionIsRequired(
    bool requestedState, bool nanoappHasRequest) const {
  return ((requestedState && mScanMonitorNanoapps.empty())
      || (!requestedState && nanoappHasRequest
              && mScanMonitorNanoapps.size() == 1));
}

bool WifiRequestManager::addScanMonitorRequestToQueue(Nanoapp *nanoapp,
                                                      bool enable,
                                                      const void *cookie) {
  ScanMonitorStateTransition scanMonitorStateTransition;
  scanMonitorStateTransition.nanoappInstanceId = nanoapp->getInstanceId();
  scanMonitorStateTransition.cookie = cookie;
  scanMonitorStateTransition.enable = enable;

  bool success = mScanMonitorStateTransitions.push(scanMonitorStateTransition);
  if (!success) {
    LOGW("Too many scan monitor state transitions");
  }

  return success;
}

bool WifiRequestManager::updateNanoappScanMonitoringList(bool enable,
                                                         uint32_t instanceId) {
  bool success = true;
  Nanoapp *nanoapp = EventLoopManagerSingleton::get()->
      findNanoappByInstanceId(instanceId);
  if (nanoapp == nullptr) {
    CHRE_ASSERT_LOG(false, "Failed to update scan monitoring list for "
                    "non-existent nanoapp");
  } else {
    size_t nanoappIndex;
    bool hasExistingRequest = nanoappHasScanMonitorRequest(instanceId,
                                                           &nanoappIndex);
    if (enable) {
      if (!hasExistingRequest) {
        success = nanoapp->registerForBroadcastEvent(
            CHRE_EVENT_WIFI_SCAN_RESULT);
        if (!success) {
          LOGE("Failed to register nanoapp for wifi scan events");
        } else {
          // The scan monitor was successfully enabled for this nanoapp and
          // there is no existing request. Add it to the list of scan monitoring
          // nanoapps.
          success = mScanMonitorNanoapps.push_back(instanceId);
          if (!success) {
            nanoapp->unregisterForBroadcastEvent(CHRE_EVENT_WIFI_SCAN_RESULT);
            LOGE("Failed to add nanoapp to the list of scan monitoring "
                 "nanoapps");
          }
        }
      }
    } else {
      if (!hasExistingRequest) {
        success = false;
        LOGE("Received a scan monitor state change for a non-existent nanoapp");
      } else {
        // The scan monitor was successfully disabled for a previously enabled
        // nanoapp. Remove it from the list of scan monitoring nanoapps.
        mScanMonitorNanoapps.erase(nanoappIndex);
        nanoapp->unregisterForBroadcastEvent(CHRE_EVENT_WIFI_SCAN_RESULT);
      }
    }
  }

  return success;
}

bool WifiRequestManager::postScanMonitorAsyncResultEvent(
    uint32_t nanoappInstanceId, bool success, bool enable, uint8_t errorCode,
    const void *cookie) {
  // Allocate and post an event to the nanoapp requesting wifi.
  bool eventPosted = false;
  if (!success || updateNanoappScanMonitoringList(enable, nanoappInstanceId)) {
    chreAsyncResult *event = memoryAlloc<chreAsyncResult>();
    if (event == nullptr) {
      LOGE("Failed to allocate wifi scan monitor async result event");
    } else {
      event->requestType = CHRE_WIFI_REQUEST_TYPE_CONFIGURE_SCAN_MONITOR;
      event->success = success;
      event->errorCode = errorCode;
      event->reserved = 0;
      event->cookie = cookie;

      // Post the event.
      eventPosted = EventLoopManagerSingleton::get()->postEvent(
          CHRE_EVENT_WIFI_ASYNC_RESULT, event, freeWifiAsyncResultCallback,
          kSystemInstanceId, nanoappInstanceId);
    }
  }

  return eventPosted;
}

void WifiRequestManager::postScanMonitorAsyncResultEventFatal(
    uint32_t nanoappInstanceId, bool success, bool enable, uint8_t errorCode,
    const void *cookie) {
  if (!postScanMonitorAsyncResultEvent(nanoappInstanceId, success, enable,
                                       errorCode, cookie)) {
    FATAL_ERROR("Failed to send WiFi scan monitor async result event");
  }
}

bool WifiRequestManager::postScanRequestAsyncResultEvent(
    uint32_t nanoappInstanceId, bool success, uint8_t errorCode,
    const void *cookie) {
  bool eventPosted = false;
  chreAsyncResult *event = memoryAlloc<chreAsyncResult>();
  if (event == nullptr) {
    LOGE("Failed to allocate wifi scan request async result event");
  } else {
    event->requestType = CHRE_WIFI_REQUEST_TYPE_REQUEST_SCAN;
    event->success = success;
    event->errorCode = errorCode;
    event->reserved = 0;
    event->cookie = cookie;

    // Post the event.
    eventPosted = EventLoopManagerSingleton::get()->postEvent(
        CHRE_EVENT_WIFI_ASYNC_RESULT, event, freeWifiAsyncResultCallback,
        kSystemInstanceId, nanoappInstanceId);
  }

  return eventPosted;
}

void WifiRequestManager::postScanRequestAsyncResultEventFatal(
    uint32_t nanoappInstanceId, bool success, uint8_t errorCode,
    const void *cookie) {
  if (!postScanRequestAsyncResultEvent(nanoappInstanceId, success, errorCode,
                                       cookie)) {
    FATAL_ERROR("Failed to send WiFi scan request async result event");
  }
}

void WifiRequestManager::postScanEventFatal(chreWifiScanEvent *event) {
  bool eventPosted = EventLoopManagerSingleton::get()->postEvent(
      CHRE_EVENT_WIFI_SCAN_RESULT, event, freeWifiScanEventCallback,
      kSystemInstanceId, kBroadcastInstanceId);
  if (!eventPosted) {
    FATAL_ERROR("Failed to send WiFi scan event");
  }
}

void WifiRequestManager::handleScanMonitorStateChangeSync(bool enabled,
                                                          uint8_t errorCode) {
  // Success is defined as having no errors ... in life ༼ つ ◕_◕ ༽つ
  bool success = (errorCode == CHRE_ERROR_NONE);

  // Always check the front of the queue.
  CHRE_ASSERT_LOG(!mScanMonitorStateTransitions.empty(),
                  "handleScanMonitorStateChangeSync called with no transitions");
  if (!mScanMonitorStateTransitions.empty()) {
    const auto& stateTransition = mScanMonitorStateTransitions.front();
    success &= (stateTransition.enable == enabled);
    postScanMonitorAsyncResultEventFatal(stateTransition.nanoappInstanceId,
                                         success, stateTransition.enable,
                                         errorCode, stateTransition.cookie);
    mScanMonitorStateTransitions.pop();
  }

  while (!mScanMonitorStateTransitions.empty()) {
    const auto& stateTransition = mScanMonitorStateTransitions.front();
    bool hasScanMonitorRequest = nanoappHasScanMonitorRequest(
        stateTransition.nanoappInstanceId);
    if (scanMonitorIsInRequestedState(
        stateTransition.enable, hasScanMonitorRequest)) {
      // We are already in the target state so just post an event indicating
      // success
      postScanMonitorAsyncResultEventFatal(stateTransition.nanoappInstanceId,
                                           success, stateTransition.enable,
                                           errorCode, stateTransition.cookie);
    } else if (scanMonitorStateTransitionIsRequired(
        stateTransition.enable, hasScanMonitorRequest)) {
      if (mPlatformWifi.configureScanMonitor(stateTransition.enable)) {
        break;
      } else {
        postScanMonitorAsyncResultEventFatal(stateTransition.nanoappInstanceId,
                                             false /* success */,
                                             stateTransition.enable, CHRE_ERROR,
                                             stateTransition.cookie);
      }
    } else {
      CHRE_ASSERT_LOG(false, "Invalid scan monitor state");
      break;
    }

    mScanMonitorStateTransitions.pop();
  }
}

void WifiRequestManager::handleScanResponseSync(bool pending,
                                                uint8_t errorCode) {
  CHRE_ASSERT_LOG(mScanRequestingNanoappInstanceId.has_value(),
                  "handleScanResponseSync called with no outstanding request");
  if (mScanRequestingNanoappInstanceId.has_value()) {
    bool success = (pending && errorCode == CHRE_ERROR_NONE);
    postScanRequestAsyncResultEventFatal(*mScanRequestingNanoappInstanceId,
                                         success, errorCode,
                                         mScanRequestingNanoappCookie);

    // Set a flag to indicate that results may be pending.
    mScanRequestResultsArePending = pending;

    if (pending) {
      Nanoapp *nanoapp = EventLoopManagerSingleton::get()->
          findNanoappByInstanceId(*mScanRequestingNanoappInstanceId);
      if (nanoapp == nullptr) {
        CHRE_ASSERT_LOG(false, "Received WiFi scan response for unknown "
                        "nanoapp");
      } else {
        nanoapp->registerForBroadcastEvent(CHRE_EVENT_WIFI_SCAN_RESULT);
      }
    } else {
      // If the scan results are not pending, clear the nanoapp instance ID.
      // Otherwise, wait for the results to be delivered and then clear the
      // instance ID.
      mScanRequestingNanoappInstanceId.reset();
    }
  }
}

void WifiRequestManager::handleScanEventSync(chreWifiScanEvent *event) {
  if (mScanRequestResultsArePending) {
    // Reset the event distribution logic once an entire scan event has been
    // received.
    mScanEventResultCountAccumulator += event->resultCount;
    if (mScanEventResultCountAccumulator >= event->resultTotal) {
      mScanEventResultCountAccumulator = 0;
      mScanRequestResultsArePending = false;
    }
  }

  postScanEventFatal(event);
}

void WifiRequestManager::handleFreeWifiScanEvent(chreWifiScanEvent *scanEvent) {
  mPlatformWifi.releaseScanEvent(scanEvent);

  if (!mScanRequestResultsArePending
      && mScanRequestingNanoappInstanceId.has_value()) {
    Nanoapp *nanoapp = EventLoopManagerSingleton::get()->
        findNanoappByInstanceId(*mScanRequestingNanoappInstanceId);
    if (nanoapp == nullptr) {
      CHRE_ASSERT_LOG(false, "Attempted to unsubscribe unknown nanoapp from "
                      "WiFi scan events");
    } else if (!nanoappHasScanMonitorRequest(*mScanRequestingNanoappInstanceId)) {
      nanoapp->unregisterForBroadcastEvent(CHRE_EVENT_WIFI_SCAN_RESULT);
    }

    mScanRequestingNanoappInstanceId.reset();
  }
}

void WifiRequestManager::freeWifiAsyncResultCallback(uint16_t eventType,
                                                     void *eventData) {
  memoryFree(eventData);
}

void WifiRequestManager::freeWifiScanEventCallback(uint16_t eventType,
                                                   void *eventData) {
  chreWifiScanEvent *scanEvent = static_cast<chreWifiScanEvent *>(eventData);
  EventLoopManagerSingleton::get()->getWifiRequestManager()
      .handleFreeWifiScanEvent(scanEvent);
}

}  // namespace chre
