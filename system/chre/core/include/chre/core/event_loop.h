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

#ifndef CHRE_CORE_EVENT_LOOP_H_
#define CHRE_CORE_EVENT_LOOP_H_

#include "chre/core/event.h"
#include "chre/core/nanoapp.h"
#include "chre/core/timer_pool.h"
#include "chre/platform/mutex.h"
#include "chre/platform/platform_nanoapp.h"
#include "chre/util/dynamic_vector.h"
#include "chre/util/fixed_size_blocking_queue.h"
#include "chre/util/non_copyable.h"
#include "chre/util/synchronized_memory_pool.h"
#include "chre/util/unique_ptr.h"

namespace chre {

/**
 * The EventLoop represents a single thread of execution that is shared among
 * zero or more nanoapps. As the name implies, the EventLoop is built around a
 * loop that delivers events to the nanoapps managed within for processing.
 */
class EventLoop : public NonCopyable {
 public:
  /**
   * Setup the event loop.
   */
  EventLoop();

  /**
   * Synchronous callback used with forEachNanoapp
   */
  typedef void (NanoappCallbackFunction)(const Nanoapp *nanoapp, void *data);

  /**
   * Searches the set of nanoapps managed by this EventLoop for one with the
   * given app ID. If found, provides its instance ID, which can be used to send
   * events to the app.
   *
   * This function is safe to call from any thread.
   *
   * @param appId The nanoapp identifier to search for.
   * @param instanceId If this function returns true, will be populated with the
   *        instanceId associated with the given appId; otherwise unmodified.
   *        Must not be null.
   * @return true if the given app ID was found and instanceId was populated
   */
  bool findNanoappInstanceIdByAppId(uint64_t appId, uint32_t *instanceId);

  /**
   * Iterates over the list of Nanoapps managed by this EventLoop, and invokes
   * the supplied callback for each one. This holds a lock if necessary, so it
   * is safe to call from any thread.
   *
   * @param callback Function to invoke on each Nanoapp (synchronously)
   * @param data Arbitrary data to pass to the callback
   */
  void forEachNanoapp(NanoappCallbackFunction *callback, void *data);

  /**
   * Invokes the Nanoapp's start callback, and if successful, adds it to the
   * set of Nanoapps managed by this EventLoop. This function must only be
   * called from the context of the thread that runs this event loop (i.e. from
   * the same thread that will call run() or from a callback invoked within
   * run()).
   *
   * @param nanoapp The nanoapp that will be started. Upon success, this
   *        UniquePtr will become invalid, as the underlying Nanoapp instance
   *        will have been transferred to be managed by this EventLoop.
   * @return true if the app was started successfully
   */
  bool startNanoapp(UniquePtr<Nanoapp>& nanoapp);

  /**
   * Stops a nanoapp by invoking the stop entry point. The nanoapp passed in
   * must have been previously started by the startNanoapp method. After this
   * function returns, all references to the Nanoapp are invalid.
   *
   * @param nanoapp A pointer to the nanoapp to stop.
   */
  void stopNanoapp(Nanoapp *nanoapp);

  /**
   * Executes the loop that blocks on the event queue and delivers received
   * events to nanoapps. Only returns after stop() is called (from another
   * context).
   */
  void run();

  /**
   * Signals the event loop currently executing in run() to exit gracefully at
   * the next available opportunity. This function is thread-safe.
   */
  void stop();

  /**
   * Posts an event to a nanoapp that is currently running (or all nanoapps if
   * the target instance ID is kBroadcastInstanceId).
   *
   * This function is safe to call from any thread.
   *
   * @param eventType The type of data being posted.
   * @param eventData The data being posted.
   * @param freeCallback The callback to invoke when the event is no longer
   *        needed.
   * @param senderInstanceId The instance ID of the sender of this event.
   * @param targetInstanceId The instance ID of the destination of this event.
   *
   * @return true if the event was successfully added to the queue
   *
   * @see chreSendEvent
   */
  bool postEvent(uint16_t eventType, void *eventData,
                 chreEventCompleteFunction *freeCallback,
                 uint32_t senderInstanceId = kSystemInstanceId,
                 uint32_t targetInstanceId = kBroadcastInstanceId);

  /**
   * Returns a pointer to the currently executing Nanoapp, or nullptr if none is
   * currently executing. Must only be called from within the thread context
   * associated with this EventLoop.
   *
   * @return the currently executing nanoapp, or nullptr
   */
  Nanoapp *getCurrentNanoapp() const;

  /**
   * Gets the number of nanoapps currently associated with this event loop. Must
   * only be called within the context of this EventLoop.
   *
   * @return The number of nanoapps managed by this event loop
   */
  size_t getNanoappCount() const;

  /**
   * Obtains the TimerPool associated with this event loop.
   *
   * @return The timer pool owned by this event loop.
   */
  TimerPool& getTimerPool();

  /**
   * Searches the set of nanoapps managed by this EventLoop for one with the
   * given instance ID.
   *
   * This function is safe to call from any thread.
   *
   * @param instanceId The nanoapp instance ID to search for.
   * @return a pointer to the found nanoapp or nullptr if no match was found.
   */
  Nanoapp *findNanoappByInstanceId(uint32_t instanceId);

 private:
  //! The maximum number of events that can be active in the system.
  static constexpr size_t kMaxEventCount = 1024;

  //! The maximum number of events that are awaiting to be scheduled. These
  //! events are in a queue to be distributed to apps.
  static constexpr size_t kMaxUnscheduledEventCount = 1024;

  //! The memory pool to allocate incoming events from.
  SynchronizedMemoryPool<Event, kMaxEventCount> mEventPool;

  //! The timer used schedule timed events for tasks running in this event loop.
  TimerPool mTimerPool;

  //! The list of nanoapps managed by this event loop.
  DynamicVector<UniquePtr<Nanoapp>> mNanoapps;

  //! This lock *must* be held whenever we:
  //!   (1) make changes to the mNanoapps vector, or
  //!   (2) read the mNanoapps vector from a thread other than the one
  //!       associated with this EventLoop
  //! It is not necessary to acquire the lock when reading mNanoapps from within
  //! the thread context of this EventLoop.
  Mutex mNanoappsLock;

  //! The blocking queue of incoming events from the system that have not been
  //!  distributed out to apps yet.
  FixedSizeBlockingQueue<Event *, kMaxUnscheduledEventCount> mEvents;

  // TODO: should probably be atomic to be fully correct
  volatile bool mRunning = false;

  Nanoapp *mCurrentApp = nullptr;

  /**
   * Delivers the next event pending in the Nanoapp's queue, and takes care of
   * freeing events once they have been delivered to all nanoapps. Must only be
   * called after confirming that the app has at least 1 pending event.
   *
   * @return true if the nanoapp has another event pending in its queue
   */
  bool deliverNextEvent(const UniquePtr<Nanoapp>& app);

  /**
   * Call after when an Event has been delivered to all intended recipients.
   * Invokes the event's free callback (if given) and releases resources.
   *
   * @param event The event to be freed
   */
  void freeEvent(Event *event);

  /**
   * Finds a Nanoapp with the given instanceId.
   *
   * Only safe to call within this EventLoop's thread.
   *
   * @param instanceId Nanoapp instance identifier
   * @return Nanoapp with the given instanceId, or nullptr if not found
   */
  Nanoapp *lookupAppByInstanceId(uint32_t instanceId);

  /**
   * Stops the Nanoapp at the given index in mNanoapps
   */
  void stopNanoapp(size_t index);
};

}  // namespace chre

#endif  // CHRE_CORE_EVENT_LOOP_H_
