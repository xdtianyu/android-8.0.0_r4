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

#include "chre/core/nanoapp.h"

#include "chre/core/event_loop_manager.h"
#include "chre/platform/assert.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"

namespace chre {

uint32_t Nanoapp::getInstanceId() const {
  return mInstanceId;
}

void Nanoapp::setInstanceId(uint32_t instanceId) {
  mInstanceId = instanceId;
}

bool Nanoapp::isRegisteredForBroadcastEvent(uint16_t eventType) const {
  return (mRegisteredEvents.find(eventType) != mRegisteredEvents.size());
}

bool Nanoapp::registerForBroadcastEvent(uint16_t eventId) {
  if (isRegisteredForBroadcastEvent(eventId)) {
    return false;
  }

  if (!mRegisteredEvents.push_back(eventId)) {
    FATAL_ERROR("App failed to register for event: out of memory");
  }

  return true;
}

bool Nanoapp::unregisterForBroadcastEvent(uint16_t eventId) {
  size_t registeredEventIndex = mRegisteredEvents.find(eventId);
  if (registeredEventIndex == mRegisteredEvents.size()) {
    return false;
  }

  mRegisteredEvents.erase(registeredEventIndex);
  return true;
}

void Nanoapp::postEvent(Event *event) {
  mEventQueue.push(event);
}

bool Nanoapp::hasPendingEvent() {
  return !mEventQueue.empty();
}

Event *Nanoapp::processNextEvent() {
  Event *event = mEventQueue.pop();

  CHRE_ASSERT_LOG(event != nullptr, "Tried delivering event, but queue empty");
  if (event != nullptr) {
    handleEvent(event->senderInstanceId, event->eventType, event->eventData);
  }

  return event;
}

}  // namespace chre
