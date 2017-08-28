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

#ifndef CHRE_PLATFORM_SLPI_ASSERT_H_
#define CHRE_PLATFORM_SLPI_ASSERT_H_

// TODO: Replace this with something more intelligent. This implementation
// is a no-op that just logs a message. We will need to spawn a thread for CHRE
// to run in and then request that it be stopped when an assertion fails.

#define CHRE_ASSERT(condition) do {                         \
  if (!(condition)) {                                       \
    LOGE("Assertion failure at %s:%d", __FILE__, __LINE__); \
  }                                                         \
} while (0)

#endif  // CHRE_PLATFORM_SLPI_ASSERT_H_
