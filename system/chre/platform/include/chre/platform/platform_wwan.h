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

#ifndef CHRE_PLATFORM_PLATFORM_WWAN_H_
#define CHRE_PLATFORM_PLATFORM_WWAN_H_

#include "chre/target_platform/platform_wwan_base.h"

namespace chre {

class PlatformWwan : public PlatformWwanBase {
 public:
  /**
   * Performs platform-specific initialization of the PlatformWwan instance.
   */
  PlatformWwan();

  /**
   * Performs platform-specific deinitialization of the PlatformWwan instance.
   */
  ~PlatformWwan();

  /**
   * Returns the set of WWAN capabilities that the platform has exposed. This
   * may return CHRE_WWAN_CAPABILITIES_NONE if WWAN is not supported.
   *
   * @return the WWAN capabilities exposed by this platform.
   */
  uint32_t getCapabilities();
};

}  // namespace chre

#endif  // CHRE_PLATFORM_PLATFORM_WWAN_H_
