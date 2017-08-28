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

#include <cstdarg>
#include <cstring>

#include "chre_api/chre/event.h"
#include "chre_api/chre/re.h"
#include "chre/core/event_loop_manager.h"
#include "chre/core/host_comms_manager.h"
#include "chre/platform/context.h"
#include "chre/platform/log.h"

using chre::EventLoopManager;

bool chreSendEvent(uint16_t eventType, void *eventData,
                   chreEventCompleteFunction *freeCallback,
                   uint32_t targetInstanceId) {
  chre::Nanoapp *nanoapp = EventLoopManager::validateChreApiCall(__func__);
  return chre::EventLoopManagerSingleton::get()->postEvent(
      eventType, eventData, freeCallback, nanoapp->getInstanceId(),
      targetInstanceId);
}

bool chreSendMessageToHost(void *message, uint32_t messageSize,
                           uint32_t messageType,
                           chreMessageFreeFunction *freeCallback) {
  return chreSendMessageToHostEndpoint(
      message, static_cast<size_t>(messageSize), messageType,
      CHRE_HOST_ENDPOINT_BROADCAST, freeCallback);
}

bool chreSendMessageToHostEndpoint(void *message, size_t messageSize,
                                   uint32_t messageType, uint16_t hostEndpoint,
                                   chreMessageFreeFunction *freeCallback) {
  auto& hostCommsManager =
      chre::EventLoopManagerSingleton::get()->getHostCommsManager();
  return hostCommsManager.sendMessageToHostFromCurrentNanoapp(
      message, messageSize, messageType, hostEndpoint, freeCallback);
}

void chreLog(enum chreLogLevel level, const char *formatStr, ...) {
  char logBuf[512];
  va_list args;

  va_start(args, formatStr);
  vsnprintf(logBuf, sizeof(logBuf), formatStr, args);
  va_end(args);

  switch (level) {
    case CHRE_LOG_ERROR:
      LOGE("%s", logBuf);
      break;
    case CHRE_LOG_WARN:
      LOGW("%s", logBuf);
      break;
    case CHRE_LOG_INFO:
      LOGI("%s", logBuf);
      break;
    case CHRE_LOG_DEBUG:
    default:
      LOGD("%s", logBuf);
  }
}
