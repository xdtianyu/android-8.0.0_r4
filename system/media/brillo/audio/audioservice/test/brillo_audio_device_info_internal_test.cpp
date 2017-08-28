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

// Tests for the BrilloAudioDeviceInfoInternal test.

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <hardware/audio.h>

#include "brillo_audio_device_info_internal.h"

namespace brillo {

TEST(BrilloAudioDeviceInfoInternalTest, OutWiredHeadset) {
  BAudioDeviceInfoInternal* badi =
      BAudioDeviceInfoInternal::CreateFromAudioDevicesT(
          AUDIO_DEVICE_OUT_WIRED_HEADSET);
  EXPECT_EQ(badi->device_id_, TYPE_WIRED_HEADSET);
  EXPECT_EQ(badi->GetConfig(), AUDIO_POLICY_FORCE_HEADPHONES);
}

TEST(BrilloAudioDeviceInfoInternalTest, OutWiredHeadphone) {
  BAudioDeviceInfoInternal* badi =
      BAudioDeviceInfoInternal::CreateFromAudioDevicesT(
          AUDIO_DEVICE_OUT_WIRED_HEADPHONE);
  EXPECT_EQ(badi->device_id_, TYPE_WIRED_HEADPHONES);
  EXPECT_EQ(badi->GetConfig(), AUDIO_POLICY_FORCE_HEADPHONES);
}

TEST(BrilloAudioDeviceInfoInternalTest, InWiredHeadset) {
  BAudioDeviceInfoInternal* badi =
      BAudioDeviceInfoInternal::CreateFromAudioDevicesT(
          AUDIO_DEVICE_IN_WIRED_HEADSET);
  EXPECT_EQ(badi->device_id_, TYPE_WIRED_HEADSET_MIC);
  EXPECT_EQ(badi->GetConfig(), AUDIO_POLICY_FORCE_HEADPHONES);
}

}  // namespace brillo
