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

#ifndef CHRE_CORE_WIFI_REQUEST_MANAGER_H_
#define CHRE_CORE_WIFI_REQUEST_MANAGER_H_

#include "chre/core/nanoapp.h"
#include "chre/platform/platform_wifi.h"
#include "chre/util/non_copyable.h"

namespace chre {

/**
 * The WifiRequestManager handles requests from nanoapps for Wifi information.
 * This includes multiplexing multiple requests into one for the platform to
 * handle.
 *
 * This class is effectively a singleton as there can only be one instance of
 * the PlatformWifi instance.
 */
class WifiRequestManager : public NonCopyable {
 public:
   /**
    * Initializes the WifiRequestManager with a default state and memory for any
    * requests.
    */
  WifiRequestManager();

  /**
   * @return the WiFi capabilities exposed by this platform.
   */
  uint32_t getCapabilities();

  /**
   * Handles a request from a nanoapp to configure the scan monitor. This
   * includes merging multiple requests for scan monitoring to the PAL (ie: if
   * multiple apps enable the scan monitor the PAL is only enabled once).
   *
   * @param nanoapp The nanoapp that has requested that the scan monitor be
   *        configured.
   * @param enable true to enable scan monitoring, false to disable scan
   *        monitoring.
   * @param cookie A cookie that is round-tripped back to the nanoapp to
   *        provide a context when making the request.
   * @return true if the request was accepted. The result is delivered
   *         asynchronously through a CHRE event.
   */
  bool configureScanMonitor(Nanoapp *nanoapp, bool enable, const void *cookie);

  /**
   * Performs an active wifi scan.
   *
   * This is currently a 1:1 mapping into the PAL. If more than one nanoapp
   * requests an active wifi scan, this will be an assertion failure for debug
   * builds and a no-op in production (ie: subsequent requests are ignored).
   *
   * @param nanoapp The nanoapp that has requested an active wifi scan.
   * @param params The parameters of the wifi scan.
   * @param cookie A cookie that is round-tripped back to the nanoapp to provide
   *        a context when making the request.
   * @return true if the request was accepted. The result is delivered
   *         asynchronously through a CHRE event.
   */
  bool requestScan(Nanoapp *nanoapp, const chreWifiScanParams *params,
                   const void *cookie);

  /**
   * Handles the result of a request to PlatformWifi to change the state of the
   * scan monitor.
   *
   * @param enabled true if the result of the operation was an enabled scan
   *        monitor.
   * @param errorCode an error code that is provided to indicate success or what
   *        type of error has occurred. See the chreError enum in the CHRE API
   *        for additional details.
   */
  void handleScanMonitorStateChange(bool enabled, uint8_t errorCode);

  /**
   * Handles the result of a request to the PlatformWifi to request an active
   * Wifi scan.
   *
   * @param pending The result of the request was successful and the results
   *        be sent via the handleScanEvent method.
   * @param errorCode an error code that is used to indicate success or what
   *        type of error has occurred. See the chreError enum in the CHRE API
   *        for additional details.
   */
  void handleScanResponse(bool pending, uint8_t errorCode);

  /**
   * Handles a CHRE wifi scan event.
   *
   * @param event The wifi scan event provided to the wifi request manager. This
   *        memory is guaranteed not to be modified until it has been explicitly
   *        released through the PlatformWifi instance.
   */
  void handleScanEvent(chreWifiScanEvent *event);

 private:
  /**
   * Tracks the state of the wifi scan monitor.
   */
  struct ScanMonitorStateTransition {
    //! The nanoapp instance ID that prompted the change.
    uint32_t nanoappInstanceId;

    //! The cookie provided to the CHRE API when the nanoapp requested a change
    //! of state to the scan monitoring.
    const void *cookie;

    //! The target state of the PAL scan monitor.
    bool enable;
  };

  //! The maximum number of scan monitor state transitions that can be queued.
  static constexpr size_t kMaxScanMonitorStateTransitions = 8;

  //! The instance of the platform wifi interface.
  PlatformWifi mPlatformWifi;

  //! The queue of state transition requests for the scan monitor. Only one
  //! asynchronous scan monitor state transition can be in flight at one time.
  //! Any further requests are queued here.
  ArrayQueue<ScanMonitorStateTransition,
             kMaxScanMonitorStateTransitions> mScanMonitorStateTransitions;

  //! The list of nanoapps who have enabled scan monitoring. This list is
  //! maintained to ensure that nanoapps are always subscribed to wifi scan
  //! results as requested. Note that a request for wifi scan monitoring can
  //! exceed the duration of a single active wifi scan request. This makes it
  //! insuitable only subscribe to wifi scan events when an active request is
  //! made and the scan monitor must remain enabled when an active request has
  //! completed.
  DynamicVector<uint32_t> mScanMonitorNanoapps;

  // TODO: Support multiple requests for active wifi scans.
  //! The instance ID of the nanoapp that has a pending active scan request. At
  //! this time, only one nanoapp can have a pending request for an active WiFi
  //! scan.
  Optional<uint32_t> mScanRequestingNanoappInstanceId;

  //! The cookie passed in by a nanoapp making an active request for wifi scans.
  //! Note that this will only be valid if the mScanRequestingNanoappInstanceId
  //! is set.
  const void *mScanRequestingNanoappCookie;

  //! This is set to true if the results of an active scan request are pending.
  bool mScanRequestResultsArePending = false;

  //! Accumulates the number of scan event results to determine when the last
  //! in a scan event stream has been received.
  uint8_t mScanEventResultCountAccumulator = 0;

  /**
   * @return true if the scan monitor is enabled by any nanoapps.
   */
  bool scanMonitorIsEnabled() const;

  /**
   * @param instanceId the instance ID of the nanoapp.
   * @param index an optional pointer to a size_t to populate with the index of
   *        the nanoapp in the list of nanoapps.
   * @return true if the nanoapp has an active request for scan monitoring.
   */
  bool nanoappHasScanMonitorRequest(uint32_t instanceId,
                                    size_t *index = nullptr) const;

  /**
   * @param requestedState The requested state to compare against.
   * @param nanoappHasRequest The requesting nanoapp has an existing request.
   * @return true if the scan monitor is in the requested state.
   */
  bool scanMonitorIsInRequestedState(bool requestedState,
                                     bool nanoappHasRequest) const;

  /**
   * @param requestedState The requested state to compare against.
   * @param nanoappHasRequest The requesting nanoapp has an existing request.
   * @return true if a state transition is required to reach the requested
   * state.
   */
  bool scanMonitorStateTransitionIsRequired(bool requestedState,
                                            bool nanoappHasRequest) const;

  /**
   * Builds a scan monitor state transition and adds it to the queue of incoming
   * requests.
   * @param nanoapp A non-null pointer to a nanoapp that is requesting the
   *        change.
   * @param enable The target requested scan monitoring state.
   * @param cookie The pointer cookie passed in by the calling nanoapp to return
   *        to the nanoapp when the request completes.
   * @return true if the request is enqueued or false if the queue is full.
   */
  bool addScanMonitorRequestToQueue(Nanoapp *nanoapp, bool enable,
                                    const void *cookie);

  /**
   * Adds a nanoapp to the list of nanoapps that are monitoring for wifi scans.
   * @param enable true if enabling scan monitoring.
   * @param instanceId The instance ID of the scan monitoring nanoapp.
   * @return true if the nanoapp was added to the list.
   */
  bool updateNanoappScanMonitoringList(bool enable, uint32_t instanceId);

  /**
   * Posts an event to a nanoapp indicating the result of a wifi scan monitoring
   * configuration change.
   *
   * @param nanoappInstanceId The nanoapp instance ID to direct the event to.
   * @param success If the request for a wifi resource was successful.
   * @param enable The target state of the request. If enable is set to false
   *        and the request was successful, the nanoapp is removed from the
   *        list of nanoapps requesting scan monitoring.
   * @param errorCode The error code when success is set to false.
   * @param cookie The cookie to be provided to the nanoapp. This is
   *        round-tripped from the nanoapp to provide context.
   * @return true if the event was successfully posted to the event loop.
   */
  bool postScanMonitorAsyncResultEvent(
      uint32_t nanoappInstanceId, bool success, bool enable, uint8_t errorCode,
      const void *cookie);

  /**
   * Calls through to postScanMonitorAsyncResultEvent but invokes the
   * FATAL_ERROR macro if the event is not posted successfully. This is used in
   * asynchronous contexts where a nanoapp could be stuck waiting for a response
   * but CHRE failed to enqueue one. For parameter details,
   * @see postScanMonitorAsyncResultEvent
   */
  void postScanMonitorAsyncResultEventFatal(
      uint32_t nanoappInstanceId, bool success, bool enable, uint8_t errorCode,
      const void *cookie);

  /**
   * Posts an event to a nanoapp indicating the result of a request for an
   * active wifi scan.
   *
   * @param nanoappInstanceId The nanoapp instance ID to direct the event to.
   * @param success If the request for a wifi resource was successful.
   * @param errorCode The error code when success is set to false.
   * @param cookie The cookie to be provided to the nanoapp. This is
   *        round-tripped from the nanoapp to provide context.
   * @return true if the event was successfully posted to the event loop.
   */
  bool postScanRequestAsyncResultEvent(
      uint32_t nanoappInstanceId, bool success, uint8_t errorCode,
      const void *cookie);

  /**
   * Calls through to postScanRequestAsyncResultEvent but invokes the
   * FATAL_ERROR macro if the event is not posted successfully. This is used in
   * asynchronous contexts where a nanoapp could be stuck waiting for a response
   * but CHRE failed to enqueue one. For parameter details,
   * @see postScanRequestAsyncResultEvent
   */
  void postScanRequestAsyncResultEventFatal(
      uint32_t nanoappInstanceId, bool success, uint8_t errorCode,
      const void *cookie);

  /**
   * Posts a broadcast event containing the results of a wifi scan. Failure to
   * post this event is a FATAL_ERROR. This is unrecoverable as the nanoapp will
   * be stuck waiting for wifi scan results but there may be a gap.
   *
   * @param event the wifi scan event.
   */
  void postScanEventFatal(chreWifiScanEvent *event);

  /**
   * Handles the result of a request to PlatformWifi to change the state of the
   * scan monitor. See the handleScanMonitorStateChange method which may be
   * called from any thread. This method is intended to be invoked on the CHRE
   * event loop thread.
   *
   * @param enabled true if the result of the operation was an enabled scan
   *        monitor.
   * @param errorCode an error code that is provided to indicate success or what
   *        type of error has occurred. See the chreError enum in the CHRE API
   *        for additional details.
   */
  void handleScanMonitorStateChangeSync(bool enabled, uint8_t errorCode);

  /**
   * Handles the result of a request to PlatformWifi to perform an active WiFi
   * scan. See the handleScanResponse method which may be called from any
   * thread. This method is intended to be invoked on the CHRE event loop
   * thread.
   *
   * @param enabled true if the result of the operation was an enabled scan
   *        monitor.
   * @param errorCode an error code that is provided to indicate success or what
   *        type of error has occurred. See the chreError enum in the CHRE API
   *        for additional details.
   */
  void handleScanResponseSync(bool pending, uint8_t errorCode);

  /**
   * Handles a WiFi scan event. See the handleScanEvent method with may be
   * called from any thread. This method is intended to be invoked on the CHRE
   * event loop thread.
   *
   * @param event The wifi event to distribute to nanoapps.
   */
  void handleScanEventSync(chreWifiScanEvent *event);

  /**
   * TODO: this.
   */
  void handleFreeWifiScanEvent(chreWifiScanEvent *scanEvent);

  /**
   * Releases the memory associated with an asynchronous wifi event.
   *
   * @param eventType The type of event being freed.
   * @param eventData A pointer to the data to release.
   */
  static void freeWifiAsyncResultCallback(uint16_t eventType, void *eventData);

  /**
   * Releases a wifi scan event after nanoapps have consumed it.
   *
   * @param eventType the type of event being freed.
   * @param eventData a pointer to the scan event to release.
   */
  static void freeWifiScanEventCallback(uint16_t eventType, void *eventData);
};

}  // namespace chre

#endif  // CHRE_CORE_WIFI_REQUEST_MANAGER_H_
