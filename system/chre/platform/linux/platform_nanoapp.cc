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

#include "chre_api/chre/version.h"
#include "chre/platform/platform_nanoapp.h"

namespace chre {

PlatformNanoapp::~PlatformNanoapp() {}

bool PlatformNanoapp::start() {
  return mStart();
}

void PlatformNanoapp::handleEvent(uint32_t senderInstanceId,
                                  uint16_t eventType,
                                  const void *eventData) {
  mHandleEvent(senderInstanceId, eventType, eventData);
}

void PlatformNanoapp::end() {
  mEnd();
}

uint64_t PlatformNanoapp::getAppId() const {
  return mAppId;
}

uint32_t PlatformNanoapp::getAppVersion() const {
  return mAppVersion;
}

uint32_t PlatformNanoapp::getTargetApiVersion() const {
  return CHRE_API_VERSION;
}

bool PlatformNanoapp::isSystemNanoapp() const {
  return true;
}

}  // namespace chre
