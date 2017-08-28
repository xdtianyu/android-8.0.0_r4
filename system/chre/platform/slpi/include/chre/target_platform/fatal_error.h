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

#ifndef CHRE_PLATFORM_LINUX_FATAL_ERROR_H_
#define CHRE_PLATFORM_LINUX_FATAL_ERROR_H_

// TODO: This is not a good way to abort the task as it kills the entire SLPI.
// We need to spawn a thread for CHRE to run in and stop that thread here.

#include <cstdlib>

#define FATAL_ERROR_QUIT abort

#endif  // CHRE_PLATFORM_LINUX_FATAL_ERROR_H_
