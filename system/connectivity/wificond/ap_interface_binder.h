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

#ifndef WIFICOND_AP_INTERFACE_BINDER_H_
#define WIFICOND_AP_INTERFACE_BINDER_H_

#include <android-base/macros.h>

#include "android/net/wifi/BnApInterface.h"

namespace android {
namespace wificond {

class ApInterfaceImpl;

class ApInterfaceBinder : public android::net::wifi::BnApInterface {
 public:
  explicit ApInterfaceBinder(ApInterfaceImpl* impl);
  ~ApInterfaceBinder() override;

  // Called by |impl_| its destruction.
  // This informs the binder proxy that no future manipulations of |impl_|
  // by remote processes are possible.
  void NotifyImplDead() { impl_ = nullptr; }

  binder::Status startHostapd(bool* out_success) override;
  binder::Status stopHostapd(bool* out_success) override;
  binder::Status writeHostapdConfig(const std::vector<uint8_t>& ssid,
                                    bool is_hidden,
                                    int32_t channel,
                                    int32_t encryption_type,
                                    const std::vector<uint8_t>& passphrase,
                                    bool* out_success) override;
  binder::Status getInterfaceName(std::string* out_name) override;
  binder::Status getNumberOfAssociatedStations(
      int* out_num_of_stations) override;

 private:
  ApInterfaceImpl* impl_;

  DISALLOW_COPY_AND_ASSIGN(ApInterfaceBinder);
};

}  // namespace wificond
}  // namespace android

#endif  // WIFICOND_AP_INTERFACE_BINDER_H_
