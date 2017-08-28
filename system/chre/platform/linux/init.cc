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

#include "chre/core/event.h"
#include "chre/core/event_loop.h"
#include "chre/core/event_loop_manager.h"
#include "chre/core/init.h"
#include "chre/core/nanoapp.h"
#include "chre/platform/context.h"
#include "chre/platform/log.h"
#include "chre/platform/static_nanoapps.h"
#include "chre/platform/system_timer.h"
#include "chre/util/time.h"

#include <csignal>

using chre::Milliseconds;

namespace {

extern "C" void signalHandler(int sig) {
  (void) sig;
  LOGI("Stop request received");
  chre::getCurrentEventLoop()->stop();
}

}

int main() {
  chre::init();

  // Construct the event loop and register the signal handler.
  chre::EventLoop& eventLoop = *chre::getCurrentEventLoop();
  std::signal(SIGINT, signalHandler);

  // Load any static nanoapps and start the event loop.
  chre::loadStaticNanoapps(&eventLoop);
  eventLoop.run();

  chre::deinit();
  return 0;
}
