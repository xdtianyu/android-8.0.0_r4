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

#ifndef CHRE_PLATFORM_SLPI_SYSTEM_TIMER_BASE_H_
#define CHRE_PLATFORM_SLPI_SYSTEM_TIMER_BASE_H_

extern "C" {

// TODO: Investigate switching to utimer.h. The symbols are not currently
// exported by the static image. I have tested a static image with utimer
// symbols exported but an SLPI crash occurs when the callback is invoked.
#include "timer.h"

}  // extern "C"

namespace chre {

class SystemTimerBase {
 public:
  //! The underlying QURT timer.
  timer_type mTimerHandle;

  //! Tracks whether the timer has been initialized correctly.
  bool mInitialized = false;

  //! A static method that is invoked by the underlying QURT timer.
  static void systemTimerNotifyCallback(timer_cb_data_type data);
};

}  // namespace chre

#endif  // CHRE_PLATFORM_SLPI_SYSTEM_TIMER_BASE_H_
