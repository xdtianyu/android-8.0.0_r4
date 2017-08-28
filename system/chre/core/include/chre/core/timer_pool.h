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

#ifndef CHRE_CORE_TIMER_POOL_H_
#define CHRE_CORE_TIMER_POOL_H_

#include "chre_api/chre/re.h"

#include "chre/platform/mutex.h"
#include "chre/platform/system_timer.h"
#include "chre/util/non_copyable.h"
#include "chre/util/priority_queue.h"

namespace chre {

/**
 * The type to use when referring to a timer instance.
 *
 * Note that this mirrors the CHRE API definition of a timer handle, so should
 * not be changed without appropriate consideration.
 */
typedef uint32_t TimerHandle;

//! Forward declare the EventLoop class to avoid a circular dependency between
//! TimerPool and EventLoop.
class EventLoop;

/**
 * Tracks requests from CHRE apps for timed events.
 */
class TimerPool : public NonCopyable {
 public:
  /**
   * Sets up the timer instance initial conditions.
   *
   * @param The event loop that owns this timer.
   */
  TimerPool(EventLoop& eventLoop);

  /**
   * Requests a timer for a nanoapp given a cookie to pass to the nanoapp when
   * the timer event is published.
   *
   * @param nanoapp The nanoapp for which this timer is being requested.
   * @param duration The duration of the timer.
   * @param cookie A cookie to pass to the app when the timer elapses.
   * @param oneShot false if the timer is expected to auto-reload.
   */
  TimerHandle setTimer(const Nanoapp *nanoapp, Nanoseconds duration,
      const void *cookie, bool oneShot);

  /**
   * Cancels a timer given a handle. If the timer handle is invalid or the timer
   * is not owned by the passed in nanoapp, false is returned.
   *
   * @param nanoapp The nanoapp requesting a timer to be cancelled.
   * @param timerHandle The handle for a timer to be cancelled.
   * @return true if the timer was cancelled successfully.
   */
  bool cancelTimer(const Nanoapp* nanoapp, TimerHandle timerHandle);

  // TODO: should also add methods here to:
  //   - post an event after a delay
  //   - invoke a callback in "unsafe" context (i.e. from other thread), which the
  //     CHRE system could use to trigger things while the task runner is busy

 private:
  /**
   * Tracks metadata associated with a request for a timed event.
   */
  struct TimerRequest {
    //! The nanoapp from which this request was made.
    const Nanoapp *requestingNanoapp;

    //! The TimerHandle assigned to this request.
    TimerHandle timerHandle;

    //! The time when the request was made.
    Nanoseconds expirationTime;

    //! The requested duration of the timer.
    Nanoseconds duration;

    //! Whether or not the request is a one shot or should be rescheduled.
    bool isOneShot;

    //! The cookie pointer to be passed as an event to the requesting nanoapp.
    const void *cookie;

    /**
     * Provides a greater than comparison of TimerRequests.
     *
     * @param request The other request to compare against.
     * @return Returns true if this request is greater than the provided
     *         request.
     */
    bool operator>(const TimerRequest& request) const;
  };

  //! The mutex used to lock the shared data structures below. The
  //! handleSystemTimerCallback may be called from any context so we use a lock
  //! to ensure exclusive access.
  //
  // Consider changing the design here to avoid the use of a mutex. There is
  // another option to post an event to the system task to re-schedule the next
  // timer. It would simplify the design and make it easier to make future
  // extensions to this module.
  Mutex mMutex;

  //! The event loop that owns this timer pool.
  EventLoop& mEventLoop;

  //! The queue of outstanding timer requests.
  PriorityQueue<TimerRequest, std::greater<TimerRequest>> mTimerRequests;

  //! The underlying system timer used to schedule delayed callbacks.
  SystemTimer mSystemTimer;

  //! The next timer handle for generateTimerHandle() to return.
  TimerHandle mLastTimerHandle = CHRE_TIMER_INVALID;

  //! Whether or not the timer handle generation logic needs to perform a
  //! search for a vacant timer handle.
  bool mGenerateTimerHandleMustCheckUniqueness = false;

  /**
   * Looks up a timer request given a timer handle. The lock must be acquired
   * prior to entering this function.
   *
   * @param timerHandle The timer handle referring to a given request.
   * @param index A pointer to the index of the handle. If the handle is found
   *        this will be populated with the index of the request from the list
   *        of requests. This is optional and will only be populated if not
   *        nullptr.
   * @return A pointer to a TimerRequest or nullptr if no match is found.
   */
  TimerRequest *getTimerRequestByTimerHandle(TimerHandle timerHandle,
      size_t *index = nullptr);

  /**
   * Obtains a unique timer handle to return to an app requesting a timer.
   *
   * @return The guaranteed unique timer handle.
   */
  TimerHandle generateTimerHandle();

  /**
   * Obtains a unique timer handle by searching through the list of timer
   * requests. This is a fallback for once the timer handles have been
   * exhausted. The lock must be acquired prior to entering this function.
   *
   * @return A guaranteed unique timer handle.
   */
  TimerHandle generateUniqueTimerHandle();

  /**
   * Inserts a TimerRequest into the list of active timer requests. The order of
   * mTimerRequests is always maintained such that the timer request with the
   * closest expiration time is at the front of the list.
   *
   * @param timerRequest The timer request being inserted into the list.
   */
   void insertTimerRequest(const TimerRequest& timerRequest);

   /**
    * Handles a completion callback for a timer by scheduling the next timer if
    * available. If any timers have expired already an event is posted for them
    * as well.
    */
   void onSystemTimerCallback();

   /**
    * Sets the underlying system timer to the next timer in the timer list if
    * available. The lock must be acquired prior to entering this function.
    *
    * @return true if any timer events were posted
    */
   bool handleExpiredTimersAndScheduleNext();

   /**
    * This static method handles the callback from the system timer. The data
    * pointer here is the TimerPool instance.
    *
    * @param data A pointer to the timer pool.
    */
   static void handleSystemTimerCallback(void *timerPoolPtr);
};

}  // namespace chre

#endif  // CHRE_CORE_TIMER_POOL_H_
