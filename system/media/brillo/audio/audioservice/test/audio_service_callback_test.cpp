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

// Tests for the audio service callback object.

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <hardware/audio.h>

#include "audio_service_callback.h"

namespace brillo {

class AudioServiceCallbackTest : public testing::Test {
 public:
  void SetUp() override {
    connected_call_count_ = 0;
    disconnected_call_count_ = 0;
    callback_.OnAudioDeviceAdded = OnDeviceConnectedMock;
    callback_.OnAudioDeviceRemoved = OnDeviceDisconnectedMock;
    user_data_ = static_cast<void*>(this);
  }

  static void OnDeviceConnectedMock(const BAudioDeviceInfo*, void* user_data) {
    static_cast<AudioServiceCallbackTest*>(user_data)->connected_call_count_++;
  }

  static void OnDeviceDisconnectedMock(const BAudioDeviceInfo*, void* user_data) {
    static_cast<AudioServiceCallbackTest*>(
        user_data)->disconnected_call_count_++;
  }

  BAudioCallback callback_;
  void* user_data_;
  int connected_call_count_;
  int disconnected_call_count_;
};

TEST_F(AudioServiceCallbackTest, CallbackCallCount) {
  std::vector<int> devices = {AUDIO_DEVICE_OUT_WIRED_HEADSET,
    AUDIO_DEVICE_OUT_WIRED_HEADPHONE};
  AudioServiceCallback service_callback(&callback_, user_data_);
  service_callback.OnAudioDevicesConnected(devices);
  EXPECT_EQ(connected_call_count_, devices.size());
  service_callback.OnAudioDevicesDisconnected(devices);
  EXPECT_EQ(disconnected_call_count_, devices.size());
}

}  // namespace brillo
