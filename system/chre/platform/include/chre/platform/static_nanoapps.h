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

#ifndef CHRE_PLATFORM_STATIC_NANOAPPS_H_
#define CHRE_PLATFORM_STATIC_NANOAPPS_H_

#include "chre/core/event_loop.h"

namespace chre {

//! The list of static nanoapps to load.
extern UniquePtr<Nanoapp> *const kStaticNanoappList[];

//! The number of static nanoapps to load.
extern const size_t kStaticNanoappCount;

/**
 * Loads the static nanoapps as required for this variant. All nanoapps are
 * loaded into one event loop. Failure to load static nanoapps is considered a
 * FATAL_ERROR.
 *
 * @param eventLoop the event loops to load nanoapps into.
 */
void loadStaticNanoapps(EventLoop *eventLoop);

}  // namespace chre

#endif  // CHRE_PLATFORM_STATIC_NANOAPPS_H_
