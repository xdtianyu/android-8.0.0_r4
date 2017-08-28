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

#ifndef CHRE_PLATFORM_CONTEXT_H_
#define CHRE_PLATFORM_CONTEXT_H_

#include "chre/core/event_loop.h"

namespace chre {

/**
 * Obtains a pointer to the currently executing EventLoop or nullptr if there is
 * no such event loop. In a multi-threaded system, this function will return the
 * event loop bound to the current thread.
 *
 * @return A pointer to the currently executing event loop or nullptr if there
 *         is no event loop in the current context.
 */
EventLoop *getCurrentEventLoop();

}  // namespace chre

#endif //CHRE_CONTEXT_H
