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

// Tests for audio daemon.

#include "audio_daemon_mock.h"

#include <memory>
#include <vector>

#include <binder/Binder.h>
#include <binderwrapper/binder_test_base.h>
#include <binderwrapper/stub_binder_wrapper.h>
#include <gmock/gmock.h>

#include "audio_device_handler_mock.h"

using android::BinderTestBase;
using android::IInterface;
using std::make_shared;
using testing::_;
using testing::AnyNumber;

namespace brillo {

class AudioDaemonTest : public BinderTestBase {
 public:
  AudioDaemonMock daemon_;
  AudioDeviceHandlerMock device_handler_;
};

TEST_F(AudioDaemonTest, RegisterService) {
  daemon_.InitializeBrilloAudioService();
  EXPECT_EQ(daemon_.brillo_audio_service_,
            binder_wrapper()->GetRegisteredService(
                "android.brillo.brilloaudioservice.BrilloAudioService"));
}

TEST_F(AudioDaemonTest, TestAPSConnectInitializesHandlersOnlyOnce) {
  binder_wrapper()->SetBinderForService("media.audio_policy",
                                        binder_wrapper()->CreateLocalBinder());
  daemon_.handlers_initialized_ = false;
  EXPECT_CALL(daemon_, InitializeHandlers()).Times(1);
  daemon_.ConnectToAPS();
}

TEST_F(AudioDaemonTest, TestDeviceCallbackInitializesBASIfNULL) {
  daemon_.DeviceCallback(
      AudioDeviceHandlerMock::DeviceConnectionState::kDevicesConnected,
      std::vector<int>());
  EXPECT_EQ(daemon_.brillo_audio_service_,
            binder_wrapper()->GetRegisteredService(
                "android.brillo.brilloaudioservice.BrilloAudioService"));
}

}  // namespace brillo
