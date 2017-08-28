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

#ifndef CHRE_PLATFORM_HOST_LINK_H_
#define CHRE_PLATFORM_HOST_LINK_H_

#include "chre/target_platform/host_link_base.h"
#include "chre/util/non_copyable.h"

namespace chre {

struct HostMessage;
typedef HostMessage MessageToHost;

/**
 * Abstracts the platform-specific communications link between CHRE and the host
 * processor
 */
class HostLink : public HostLinkBase,
                 public NonCopyable {
 public:
  /**
   * Enqueues a message for sending to the host. Once sending the message is
   * complete (success or failure), the platform implementation must invoke
   * HostCommsManager::onMessageToHostComplete (can be called from any thread).
   *
   * @param message A non-null pointer to the message
   *
   * @return true if the message was successfully queued
   */
  bool sendMessage(const MessageToHost *message);
};

}  // namespace chre

#endif  // CHRE_PLATFORM_HOST_LINK_H_
