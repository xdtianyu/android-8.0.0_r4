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

#ifndef CHRE_UTIL_NANOAPP_LOG_H_
#define CHRE_UTIL_NANOAPP_LOG_H_

/**
 * @file
 * Logging macros for nanoapps. These macros allow injecting a LOG_TAG and
 * compiling nanoapps with a minimum logging level (that is different than CHRE
 * itself).
 *
 * The typical format for the LOG_TAG macro is: "[AppName]"
 */

#include "chre/util/log_common.h"

#ifndef NANOAPP_MINIMUM_LOG_LEVEL
#error "NANOAPP_MINIMUM_LOG_LEVEL must be defined"
#endif  // NANOAPP_MINIMUM_LOG_LEVEL

/*
 * Supply a stub implementation of the LOGx macros when the build is
 * configured with a minimum logging level that is above the requested level.
 * Otherwise just map into the chreLog function with the appropriate level.
 */

#if NANOAPP_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_ERROR
#define LOGE(fmt, ...) chreLog(CHRE_LOG_ERROR, LOG_TAG " " fmt, ##__VA_ARGS__)
#else
#define LOGE(fmt, ...) chreLogNull(fmt, ##__VA_ARGS__)
#endif

#if CHRE_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_WARN
#define LOGW(fmt, ...) chreLog(CHRE_LOG_WARN, LOG_TAG " " fmt, ##__VA_ARGS__)
#else
#define LOGW(fmt, ...) chreLogNull(fmt, ##__VA_ARGS__)
#endif

#if CHRE_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_INFO
#define LOGI(fmt, ...) chreLog(CHRE_LOG_INFO, LOG_TAG " " fmt, ##__VA_ARGS__)
#else
#define LOGI(fmt, ...) chreLogNull(fmt, ##__VA_ARGS__)
#endif

#if CHRE_MINIMUM_LOG_LEVEL >= CHRE_LOG_LEVEL_DEBUG
#define LOGD(fmt, ...) chreLog(CHRE_LOG_DEBUG, LOG_TAG " " fmt, ##__VA_ARGS__)
#else
#define LOGD(fmt, ...) chreLogNull(fmt, ##__VA_ARGS__)
#endif

#endif  // CHRE_UTIL_NANOAPP_LOG_H_
