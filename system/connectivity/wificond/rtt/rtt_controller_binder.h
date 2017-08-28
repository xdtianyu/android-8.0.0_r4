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

#ifndef WIFICOND_RTT_CONTROLLER_BINDER_H_
#define WIFICOND_RTT_CONTROLLER_BINDER_H_

#include <android-base/macros.h>
#include <binder/Status.h>

#include "android/net/wifi/BnRttController.h"

namespace android {
namespace wificond {

class RttControllerImpl;

class RttControllerBinder : public android::net::wifi::BnRttController {
 public:
  explicit RttControllerBinder(RttControllerImpl* impl);
  ~RttControllerBinder() override;

  // Called by |impl_| its destruction.
  // This informs the binder proxy that no future manipulations of |impl_|
  // by remote processes are possible.
  void NotifyImplDead() { impl_ = nullptr; }

 private:
  RttControllerImpl* impl_;

  DISALLOW_COPY_AND_ASSIGN(RttControllerBinder);
};

}  // namespace wificond
}  // namespace android

#endif  // WIFICOND_RTT_CONTROLLER_BINDER_H_
