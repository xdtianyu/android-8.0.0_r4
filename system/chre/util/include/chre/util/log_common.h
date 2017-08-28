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

#ifndef CHRE_UTIL_LOG_COMMON_H_
#define CHRE_UTIL_LOG_COMMON_H_

/**
 * @file
 * Includes common logging macros and functions that are shared between nanoapps
 * and the CHRE implementation.
 */

/**
 * An inline stub function to direct log messages to when logging is disabled.
 * This avoids unused variable warnings and will result in no overhead.
 */
inline void chreLogNull(const char *fmt, ...) {}

//! The logging level to specify that no logs are output.
#define CHRE_LOG_LEVEL_MUTE 0

//! The logging level to specify that only LOGE is output.
#define CHRE_LOG_LEVEL_ERROR 1

//! The logging level to specify that LOGW and LOGE are output.
#define CHRE_LOG_LEVEL_WARN 2

//! The logging level to specify that LOGI, LOGW and LOGE are output.
#define CHRE_LOG_LEVEL_INFO 3

//! The logging level to specify that LOGD, LOGI, LOGW and LOGE are output.
#define CHRE_LOG_LEVEL_DEBUG 4

#endif  // CHRE_UTIL_LOG_COMMON_H_
