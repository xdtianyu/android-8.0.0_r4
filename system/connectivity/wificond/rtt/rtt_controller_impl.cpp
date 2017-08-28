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

#include "wificond/rtt/rtt_controller_impl.h"

#include <android-base/logging.h>

#include "wificond/rtt/rtt_controller_binder.h"

using android::net::wifi::IRttClient;
using android::net::wifi::IRttController;
using android::sp;

namespace android {
namespace wificond {

RttControllerImpl::RttControllerImpl()
    : binder_(new RttControllerBinder(this)) {
  // TODO(nywang): create a HAL RttController object.
}

RttControllerImpl::~RttControllerImpl() {
  binder_->NotifyImplDead();
}

sp<IRttController> RttControllerImpl::GetBinder() const {
  return binder_;
}

bool RttControllerImpl::RegisterRttClient(android::sp<IRttClient> client) {
  for (auto& it : clients_) {
    if (IRttClient::asBinder(client) == IRttClient::asBinder(it)) {
      LOG(WARNING) << "Ignore duplicate RttClient registration";
      return false;
    }
  }
  clients_.push_back(client);
  return true;

}

bool RttControllerImpl::UnregisterRttClient(android::sp<IRttClient> client) {
  for (auto it = clients_.begin(); it != clients_.end(); it++) {
    if (IRttClient::asBinder(client) == IRttClient::asBinder(*it)) {
      clients_.erase(it);
      return true;
    }
  }
  LOG(WARNING) << "Failed to find registered RttClient to unregister";
  return false;
}

size_t RttControllerImpl::GetClientCount() const {
  return clients_.size();
}

}  // namespace wificond
}  // namespace android
