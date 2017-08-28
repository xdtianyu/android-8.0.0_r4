// Copyright 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

// Internal class to represent BAudioDeviceInfo.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_DEVICE_INFO_INTERNAL_H_
#define BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_DEVICE_INFO_INTERNAL_H_

#include <vector>

#include <gtest/gtest_prod.h>
#include <hardware/audio_policy.h>

#include "include/brillo_audio_device_info.h"

namespace brillo {

class BAudioDeviceInfoInternal {
 public:
  // Constructor for BAudioDeviceInfoInternal.
  //
  // |device_id| is an integer representing an audio device type as defined in
  // brillo_audio_device_info.h.
  explicit BAudioDeviceInfoInternal(int device_id);

  // Get audio policy config.
  //
  // Returns an audio_policy_forced_cfg_t.
  audio_policy_forced_cfg_t GetConfig();

  // Create a BAudioDeviceInfoInternal object from a audio_devices_t device
  // type.
  //
  // |devices_t| is an audio device of type audio_devices_t which is represented
  // using an int.
  //
  // Returns a pointer to a BAudioDeviceInfoInternal that has been created.
  static BAudioDeviceInfoInternal* CreateFromAudioDevicesT(unsigned int device);

  // Get the device id.
  //
  // Returns an int which is the device_id.
  int GetDeviceId();

  // Get audio_devices_t that corresponds to device_id;
  //
  // Returns an audio_devices_t.
  audio_devices_t GetAudioDevicesT();

 private:
  FRIEND_TEST(BrilloAudioDeviceInfoInternalTest, InWiredHeadset);
  FRIEND_TEST(BrilloAudioDeviceInfoInternalTest, OutWiredHeadset);
  FRIEND_TEST(BrilloAudioDeviceInfoInternalTest, OutWiredHeadphone);

  // An int representing the underlying audio device. The int is one of the
  // constants defined in brillo_audio_device_info.h.
  int device_id_;
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_DEVICE_INFO_INTERNAL_H_
