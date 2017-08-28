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

#include "chre/platform/slpi/platform_sensor_util.h"

#ifdef GTEST
// This value is taken from the SMGR API definition.
#define SNS_SMGR_SAMPLING_RATE_INVERSION_POINT_V01 1000
#else
#include "sns_smgr_common_v01.h"
#endif  // GTEST

namespace chre {

uint16_t intervalToSmgrSamplingRate(Nanoseconds interval) {
  Milliseconds millis = Milliseconds(interval);
  if (millis.getMilliseconds() > SNS_SMGR_SAMPLING_RATE_INVERSION_POINT_V01) {
    return millis.getMilliseconds();
  } else if (interval != Nanoseconds(0)) {
    return static_cast<uint16_t>(
        Seconds(1).toRawNanoseconds() / interval.toRawNanoseconds());
  } else {
    return 0;
  }
}

uint32_t intervalToSmgrQ16ReportRate(Nanoseconds interval) {
  uint64_t freq = UINT32_MAX;
  if (interval != Nanoseconds(0)) {
    freq = (Seconds(1).toRawNanoseconds() << 16) / interval.toRawNanoseconds();
  }
  return (freq > UINT32_MAX) ? UINT32_MAX : static_cast<uint32_t>(freq);
}

}  // namespace chre
