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

#ifndef WIFICOND_RTT_CONTROLLER_IMPL_H_
#define WIFICOND_RTT_CONTROLLER_IMPL_H_

#include <vector>

#include <android-base/macros.h>

#include "android/net/wifi/IRttController.h"
#include "android/net/wifi/IRttClient.h"

namespace android {
namespace wificond {

class RttControllerBinder;

class RttControllerImpl {
 public:
  RttControllerImpl();
  ~RttControllerImpl();

  bool RegisterRttClient(android::sp<android::net::wifi::IRttClient> client);
  bool UnregisterRttClient(android::sp<android::net::wifi::IRttClient> client);
  size_t GetClientCount() const;

  // Get a pointer to the binder representing this RttControllerImpl.
  android::sp<android::net::wifi::IRttController> GetBinder() const;

 private:
  const android::sp<RttControllerBinder> binder_;
  std::vector<android::sp<android::net::wifi::IRttClient>> clients_;

  DISALLOW_COPY_AND_ASSIGN(RttControllerImpl);
};

}  // namespace wificond
}  // namespace android

#endif  // WIFICOND_RTT_CONTROLLER_IMPL_H_
