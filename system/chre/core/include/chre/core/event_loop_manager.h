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

#ifndef CHRE_CORE_EVENT_LOOP_MANAGER_H_
#define CHRE_CORE_EVENT_LOOP_MANAGER_H_

#include "chre_api/chre/event.h"
#include "chre/core/event_loop.h"
#include "chre/core/gnss_request_manager.h"
#include "chre/core/host_comms_manager.h"
#include "chre/core/sensor_request_manager.h"
#include "chre/core/wifi_request_manager.h"
#include "chre/core/wwan_request_manager.h"
#include "chre/platform/mutex.h"
#include "chre/util/dynamic_vector.h"
#include "chre/util/non_copyable.h"
#include "chre/util/singleton.h"
#include "chre/util/unique_ptr.h"

namespace chre {

//! An identifier for a system callback, which is mapped into a CHRE event type
//! in the user-defined range.
enum class SystemCallbackType : uint16_t {
  FirstCallbackType = CHRE_EVENT_FIRST_USER_VALUE,

  MessageToHostComplete,
  WifiScanMonitorStateChange,
  WifiRequestScanResponse,
  WifiHandleScanEvent,
  NanoappListResponse,
  SensorLastEventUpdate,
  FinishLoadingNanoapp,
};

//! The function signature of a system callback mirrors the CHRE event free
//! callback to allow it to use the same event infrastructure.
typedef chreEventCompleteFunction SystemCallbackFunction;

/**
 * A class that keeps track of all event loops in the system. This class
 * represents the top-level object in CHRE. It will own all resources that are
 * shared by all event loops.
 */
class EventLoopManager : public NonCopyable {
 public:
   /**
    * Validates that a CHRE API is invoked from a valid nanoapp context and
    * returns a pointer to the currently executing nanoapp. This should be
    * called by most CHRE API methods that require accessing details about the
    * event loop or the nanoapp itself. If the current event loop or nanoapp are
    * null, this is an assertion error.
    *
    * @param functionName The name of the CHRE API. This should be __func__.
    * @return A pointer to the currently executing nanoapp or null if outside
    *         the context of a nanoapp.
    */
  static Nanoapp *validateChreApiCall(const char *functionName);

  /**
   * Constructs an event loop and returns a pointer to it. The event loop is not
   * started by this method.
   *
   * @return A pointer to an event loop. If creation fails, nullptr is returned.
   */
  EventLoop *createEventLoop();

  /**
   * Leverages the event queue mechanism to schedule a CHRE system callback to
   * be invoked at some point in the future from within the context of the
   * "main" EventLoop. Which EventLoop is considered to be the "main" one is
   * currently not specified, but it is required to be exactly one EventLoop
   * that does not change at runtime.
   *
   * This function is safe to call from any thread.
   *
   * @param type An identifier for the callback, which is passed through to the
   *        callback as a uint16_t, and can also be useful for debugging
   * @param data Arbitrary data to provide to the callback
   * @param callback Function to invoke from within the
   */
  bool deferCallback(SystemCallbackType type, void *data,
                     SystemCallbackFunction *callback);

  /**
   * Search all event loops to look up the instance ID associated with a Nanoapp
   * via its app ID, and optionally the EventLoop that is hosting it.
   *
   * Note that this function makes the assumption that there is only one
   * instance of a given appId executing within CHRE at any given time, i.e. the
   * mapping between appId and instanceId is 1:1. This assumption holds due to
   * restrictions imposed in v1.0 of the context hub HAL, but is not guaranteed
   * for all future versions.
   *
   * This function is safe to call from any thread.
   *
   * @param appId The nanoapp ID to search for
   * @param instanceId On success, will be populated with the instanceId
   *        associated with the app. Must not be null.
   * @param eventLoop If not null, will be populated with a pointer to the
   *        EventLoop where this nanoapp executes
   * @return true if an app with the given ID was found
   */
  bool findNanoappInstanceIdByAppId(uint64_t appId, uint32_t *instanceId,
                                    EventLoop **eventLoop = nullptr);

  /**
   * Search all event loops to find a nanoapp with a given instance ID.
   *
   * This function is safe to call from any thread.
   *
   * @param instanceId The nanoapp instance ID to search for.
   * @return a pointer to the found nanoapp or nullptr.
   */
  Nanoapp *findNanoappByInstanceId(uint32_t instanceId,
                                   EventLoop **eventLoop = nullptr);

  /**
   * Returns a guaranteed unique instance identifier to associate with a newly
   * constructed nanoapp.
   *
   * @return a unique instance ID
   */
  uint32_t getNextInstanceId();

  /**
   * Posts an event to all event loops owned by this event loop manager. This
   * method is thread-safe and is used to post events that all event loops would
   * be interested in, such as sensor event data.
   *
   * @param The type of data being posted.
   * @param The data being posted.
   * @param The callback to invoke when the event is no longer needed.
   * @param The instance ID of the sender of this event.
   * @param The instance ID of the destination of this event.
   * @return true if the event was successfully sent to all nanoapps targeted
   *         by the instance ID (ie: for broadcast, all nanoapps were sent the
   *         event).
   */
  bool postEvent(uint16_t eventType, void *eventData,
                 chreEventCompleteFunction *freeCallback,
                 uint32_t senderInstanceId = kSystemInstanceId,
                 uint32_t targetInstanceId = kBroadcastInstanceId);

  /**
   * @return A reference to the GNSS request manager. This allows interacting
   *         with the platform GNSS subsystem and manages requests from various
   *         nanoapps.
   */
  GnssRequestManager& getGnssRequestManager();

  /**
   * @return A reference to the host communications manager that enables
   *         transferring arbitrary data between the host processor and CHRE.
   */
  HostCommsManager& getHostCommsManager();

  /**
   * @return Returns a reference to the sensor request manager. This allows
   *         interacting with the platform sensors and managing requests from
   *         various nanoapps.
   */
  SensorRequestManager& getSensorRequestManager();

  /**
   * @return Returns a reference to the wifi request manager. This allows
   *         interacting with the platform wifi subsystem and manages the
   *         requests from various nanoapps.
   */
  WifiRequestManager& getWifiRequestManager();

  /**
   * @return A reference to the WWAN request manager. This allows interacting
   *         with the platform WWAN subsystem and manages requests from various
   *         nanoapps.
   */
  WwanRequestManager& getWwanRequestManager();

 private:
  //! The mutex used to ensure that postEvent() completes for all event loops
  //! before another thread can start posting an event. This ensures consistency
  //! of event order between event loops.
  Mutex mMutex;

  //! The instance ID that was previously generated by getNextInstanceId()
  uint32_t mLastInstanceId = kSystemInstanceId;

  //! The list of event loops managed by this event loop manager. The EventLoops
  //! are stored in UniquePtr because they are large objects. They do not
  //! provide an implementation of the move constructor so it is best left to
  //! allocate each event loop and manage the pointers to those event loops.
  DynamicVector<UniquePtr<EventLoop>> mEventLoops;

  //! The GnssRequestManager that handles requests for all nanoapps. This
  //! manages the state of the GNSS subsystem that the runtime subscribes to.
  GnssRequestManager mGnssRequestManager;

  //! Handles communications with the host processor
  HostCommsManager mHostCommsManager;

  //! The SensorRequestManager that handles requests for all nanoapps. This
  //! manages the state of all sensors that runtime subscribes to.
  SensorRequestManager mSensorRequestManager;

  //! The WifiRequestManager that handles requests for nanoapps. This manages
  //! the state of the wifi subsystem that the runtime subscribes to.
  WifiRequestManager mWifiRequestManager;

  //! The WwanRequestManager that handles requests for nanoapps. This manages
  //! the state of the WWAN subsystem that the runtime subscribes to.
  WwanRequestManager mWwanRequestManager;
};

//! Provide an alias to the EventLoopManager singleton.
typedef Singleton<EventLoopManager> EventLoopManagerSingleton;

}  // namespace chre

#endif  // CHRE_CORE_EVENT_LOOP_MANAGER_H_
