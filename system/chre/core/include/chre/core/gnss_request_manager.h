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

#ifndef CHRE_CORE_GNSS_REQUEST_MANAGER_H_
#define CHRE_CORE_GNSS_REQUEST_MANAGER_H_

#include <cstdint>

#include "chre/platform/platform_gnss.h"
#include "chre/util/non_copyable.h"

namespace chre {

/**
 * The GnssRequestManager handles requests from nanoapps for GNSS data. This
 * includes multiplexing multiple requests into one for the platform to handle.
 *
 * This class is effectively a singleton as there can only be one instance of
 * the PlatformGnss instance.
 */
class GnssRequestManager : public NonCopyable {
 public:
  /**
   * @return the GNSS capabilities exposed by this platform.
   */
  uint32_t getCapabilities();

 private:
  //! The instance of the platform GNSS interface.
  PlatformGnss mPlatformGnss;
};

}  // namespace chre

#endif  // CHRE_CORE_GNSS_REQUEST_MANAGER_H_
