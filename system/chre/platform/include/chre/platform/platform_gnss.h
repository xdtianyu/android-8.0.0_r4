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

#ifndef CHRE_PLATFORM_PLATFORM_GNSS_H_
#define CHRE_PLATFORM_PLATFORM_GNSS_H_

#include "chre/target_platform/platform_gnss_base.h"

namespace chre {

class PlatformGnss : public PlatformGnssBase {
 public:
  /**
   * Performs platform-specific initialization of the PlatformGnss instance.
   */
  PlatformGnss();

  /**
   * Performs platform-specific deinitialization of the PlatformGnss instance.
   */
  ~PlatformGnss();

  /**
   * Returns the set of GNSS capabilities that the platform has exposed. This
   * may return CHRE_GNSS_CAPABILITIES_NONE if GNSS is not supported.
   *
   * @return the GNSS capabilities exposed by this platform.
   */
  uint32_t getCapabilities();
};

}  // namespace chre

#endif  // CHRE_PLATFORM_PLATFORM_GNSS_H_
