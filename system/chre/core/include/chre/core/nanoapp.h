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

#ifndef CHRE_CORE_NANOAPP_H_
#define CHRE_CORE_NANOAPP_H_

#include <cinttypes>

#include "chre/core/event.h"
#include "chre/core/event_ref_queue.h"
#include "chre/platform/platform_nanoapp.h"
#include "chre/util/dynamic_vector.h"

namespace chre {

/**
 * A class that tracks the state of a Nanoapp including incoming events and
 * event registrations.
 *
 * Inheritance is used to separate the common interface with common
 * implementation part (chre::Nanoapp) from the common interface with
 * platform-specific implementation part (chre::PlatformNanoapp) from the purely
 * platform-specific part (chre::PlatformNanoappBase). However, this inheritance
 * relationship does *not* imply polymorphism, and this object must only be
 * referred to via the most-derived type, i.e. chre::Nanoapp.
 */
class Nanoapp : public PlatformNanoapp {
 public:
  /**
   * @return uint32_t The globally unique identifier for this Nanoapp instance
   */
  uint32_t getInstanceId() const;

  /**
   * Assigns an instance ID to this Nanoapp. This must be called prior to
   * starting this nanoapp.
   */
  void setInstanceId(uint32_t instanceId);

  /**
   * @return true if the nanoapp should receive broadcast events with the given
   *         type
   */
  bool isRegisteredForBroadcastEvent(uint16_t eventType) const;

  /**
   * Updates the Nanoapp's registration so that it will receive broadcast events
   * with the given event ID.
   *
   * @return true if the event is newly registered
   */
  bool registerForBroadcastEvent(uint16_t eventId);

  /**
   * Updates the Nanoapp's registration so that it will not receive broadcast
   * events with the given event ID.
   *
   * @return true if the event was previously registered
   */
  bool unregisterForBroadcastEvent(uint16_t eventId);

  /**
   * Adds an event to this nanoapp's queue of pending events.
   *
   * @param event
   */
  void postEvent(Event *event);

  /**
   * Indicates whether there are any pending events in this apps queue.
   *
   * @return True indicating that there are events available to be processed.
   */
  bool hasPendingEvent();

  /**
   * Sends the next event in the queue to the nanoapp and returns the processed
   * event. The hasPendingEvent() method should be tested before invoking this.
   *
   * @return a pointer to the processed event.
   */
  Event *processNextEvent();

 private:
  uint32_t mInstanceId = kInvalidInstanceId;

  //! The set of broadcast events that this app is registered for.
  // TODO: Implement a set container and replace DynamicVector here. There may
  // also be a better way of handling this (perhaps we map event type to apps
  // who care about them).
  DynamicVector<uint16_t> mRegisteredEvents;

  EventRefQueue mEventQueue;
};

}

#endif  // CHRE_CORE_NANOAPP_H_
