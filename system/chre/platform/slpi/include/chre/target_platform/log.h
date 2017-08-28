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

#ifndef CHRE_PLATFORM_SLPI_LOG_H_
#define CHRE_PLATFORM_SLPI_LOG_H_

// By default, FARF logs with MEDIUM level are compiled out of the binary so we
// enable them with this define.
#define FARF_MEDIUM 1
#include "HAP_farf.h"

// TODO: Implement some more intelligent logging infrastructure. We will most
// likely use some kind of FastRPC to the host and potentially buffer logs here
// for a while to avoid chatter (and allow logging without waking the AP if it
// goes asleep). This just gets the initial logging macros to be supported.

#define LOGE(fmt, ...) FARF(ERROR, fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) FARF(HIGH, fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) FARF(MEDIUM, fmt, ##__VA_ARGS__)
#define LOGD(fmt, ...) FARF(MEDIUM, fmt, ##__VA_ARGS__)

#endif  // CHRE_PLATFORM_SLPI_LOG_H_
