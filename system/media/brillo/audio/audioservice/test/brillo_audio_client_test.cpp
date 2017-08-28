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

// Tests for the brillo audio client.

#include <binderwrapper/binder_test_base.h>
#include <binderwrapper/stub_binder_wrapper.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "audio_service_callback.h"
#include "brillo_audio_client.h"
#include "include/brillo_audio_manager.h"
#include "test/brillo_audio_client_mock.h"
#include "test/brillo_audio_service_mock.h"

using android::sp;
using android::String8;
using testing::Return;
using testing::_;

namespace brillo {

static const char kBrilloAudioServiceName[] =
    "android.brillo.brilloaudioservice.BrilloAudioService";

class BrilloAudioClientTest : public android::BinderTestBase {
 public:
  bool ConnectClientToBAS() {
    bas_ = new BrilloAudioServiceMock();
    binder_wrapper()->SetBinderForService(kBrilloAudioServiceName, bas_);
    return client_.Initialize();
  }

  BrilloAudioClientMock client_;
  sp<BrilloAudioServiceMock> bas_;
};

TEST_F(BrilloAudioClientTest, SetDeviceNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  EXPECT_EQ(
      client_.SetDevice(AUDIO_POLICY_FORCE_USE_MAX, AUDIO_POLICY_FORCE_NONE),
      ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, GetDevicesNoService) {
  std::vector<int> foo;
  EXPECT_CALL(client_, OnBASDisconnect());
  EXPECT_EQ(client_.GetDevices(0, foo), ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, RegisterCallbackNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  EXPECT_EQ(client_.RegisterAudioCallback(nullptr, nullptr), ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, UnregisterAudioCallbackNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  EXPECT_EQ(client_.UnregisterAudioCallback(0), ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, InitializeNoService) {
  EXPECT_FALSE(client_.Initialize());
}

TEST_F(BrilloAudioClientTest, CheckInitializeRegistersForDeathNotifications) {
  EXPECT_TRUE(ConnectClientToBAS());
  EXPECT_CALL(client_, OnBASDisconnect());
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
}

TEST_F(BrilloAudioClientTest, GetDevicesWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  std::vector<int> foo;
  EXPECT_CALL(*bas_.get(), GetDevices(0, &foo)).WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.GetDevices(0, foo), 0);
}

TEST_F(BrilloAudioClientTest, SetDeviceWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  std::vector<int> foo;
  EXPECT_CALL(*bas_.get(),
              SetDevice(AUDIO_POLICY_FORCE_USE_MAX, AUDIO_POLICY_FORCE_NONE))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(
      client_.SetDevice(AUDIO_POLICY_FORCE_USE_MAX, AUDIO_POLICY_FORCE_NONE),
      0);
}

TEST_F(BrilloAudioClientTest, RegisterCallbackWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  BAudioCallback bcallback;
  AudioServiceCallback* callback =
      new AudioServiceCallback(&bcallback, nullptr);
  int id = 0;
  EXPECT_CALL(*bas_.get(),
              RegisterServiceCallback(sp<IAudioServiceCallback>(callback)))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.RegisterAudioCallback(callback, &id), 0);
  EXPECT_NE(id, 0);
}

TEST_F(BrilloAudioClientTest, RegisterSameCallbackTwiceWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  BAudioCallback bcallback;
  AudioServiceCallback* callback =
      new AudioServiceCallback(&bcallback, nullptr);
  int id = -1;
  EXPECT_CALL(*bas_.get(),
              RegisterServiceCallback(sp<IAudioServiceCallback>(callback)))
      .Times(2)
      .WillRepeatedly(Return(Status::ok()));
  EXPECT_EQ(client_.RegisterAudioCallback(callback, &id), 0);
  EXPECT_NE(id, 0);
  id = -1;
  EXPECT_EQ(client_.RegisterAudioCallback(callback, &id), EINVAL);
  EXPECT_EQ(id, 0);
}

TEST_F(BrilloAudioClientTest, UnregisterAudioCallbackValidWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  BAudioCallback bcallback;
  AudioServiceCallback* callback =
      new AudioServiceCallback(&bcallback, nullptr);
  int id = 0;
  EXPECT_CALL(*bas_.get(),
              RegisterServiceCallback(sp<IAudioServiceCallback>(callback)))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.RegisterAudioCallback(callback, &id), 0);
  EXPECT_NE(id, 0);
  EXPECT_CALL(*bas_.get(),
              UnregisterServiceCallback(sp<IAudioServiceCallback>(callback)))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.UnregisterAudioCallback(id), 0);
}

TEST_F(BrilloAudioClientTest, UnregisterInvalidCallbackWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  EXPECT_EQ(client_.UnregisterAudioCallback(1), EINVAL);
}

TEST_F(BrilloAudioClientTest, RegisterAndUnregisterAudioTwoCallbacks) {
  EXPECT_TRUE(ConnectClientToBAS());
  BAudioCallback bcallback1, bcallback2;
  AudioServiceCallback* callback1 =
      new AudioServiceCallback(&bcallback1, nullptr);
  AudioServiceCallback* callback2 =
      new AudioServiceCallback(&bcallback2, nullptr);
  int id1 = 0, id2 = 0;
  EXPECT_CALL(*bas_.get(), RegisterServiceCallback(_))
      .WillRepeatedly(Return(Status::ok()));
  EXPECT_EQ(client_.RegisterAudioCallback(callback1, &id1), 0);
  EXPECT_NE(id1, 0);
  EXPECT_EQ(client_.RegisterAudioCallback(callback2, &id2), 0);
  EXPECT_NE(id2, 0);
  EXPECT_CALL(*bas_.get(), UnregisterServiceCallback(_))
      .WillRepeatedly(Return(Status::ok()));
  EXPECT_EQ(client_.UnregisterAudioCallback(id1), 0);
  EXPECT_EQ(client_.UnregisterAudioCallback(id2), 0);
}

TEST_F(BrilloAudioClientTest, GetMaxVolStepsNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  int foo;
  EXPECT_EQ(client_.GetMaxVolumeSteps(BAudioUsage::kUsageInvalid, &foo),
            ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, GetMaxVolStepsWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  int foo;
  EXPECT_CALL(*bas_.get(), GetMaxVolumeSteps(AUDIO_STREAM_MUSIC, &foo))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.GetMaxVolumeSteps(BAudioUsage::kUsageMedia, &foo), 0);
}

TEST_F(BrilloAudioClientTest, SetMaxVolStepsNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  EXPECT_EQ(client_.SetMaxVolumeSteps(BAudioUsage::kUsageInvalid, 100),
            ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, SetMaxVolStepsWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  EXPECT_CALL(*bas_.get(), SetMaxVolumeSteps(AUDIO_STREAM_MUSIC, 100))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.SetMaxVolumeSteps(BAudioUsage::kUsageMedia, 100), 0);
}

TEST_F(BrilloAudioClientTest, SetVolIndexNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  EXPECT_EQ(client_.SetVolumeIndex(
                BAudioUsage::kUsageInvalid, AUDIO_DEVICE_NONE, 100),
            ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, SetVolIndexWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  EXPECT_CALL(*bas_.get(),
              SetVolumeIndex(AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_SPEAKER, 100))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.SetVolumeIndex(
                BAudioUsage::kUsageMedia, AUDIO_DEVICE_OUT_SPEAKER, 100),
            0);
}

TEST_F(BrilloAudioClientTest, GetVolIndexNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  int foo;
  EXPECT_EQ(client_.GetVolumeIndex(
                BAudioUsage::kUsageInvalid, AUDIO_DEVICE_NONE, &foo),
            ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, GetVolIndexWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  int foo;
  EXPECT_CALL(
      *bas_.get(),
      GetVolumeIndex(AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_SPEAKER, &foo))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.GetVolumeIndex(
                BAudioUsage::kUsageMedia, AUDIO_DEVICE_OUT_SPEAKER, &foo),
            0);
}

TEST_F(BrilloAudioClientTest, GetVolumeControlStreamNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  BAudioUsage foo;
  EXPECT_EQ(client_.GetVolumeControlStream(&foo), ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, GetVolumeControlStreamWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  EXPECT_CALL(*bas_.get(), GetVolumeControlStream(_))
      .WillOnce(Return(Status::ok()));
  BAudioUsage foo;
  EXPECT_EQ(client_.GetVolumeControlStream(&foo), 0);
}

TEST_F(BrilloAudioClientTest, SetVolumeControlStreamNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  EXPECT_EQ(client_.SetVolumeControlStream(kUsageMedia), ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, SetVolumeControlStreamWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  EXPECT_CALL(*bas_.get(), SetVolumeControlStream(AUDIO_STREAM_MUSIC))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.SetVolumeControlStream(kUsageMedia), 0);
}

TEST_F(BrilloAudioClientTest, IncrementVolNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  EXPECT_EQ(client_.IncrementVolume(), ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, IncrementVolWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  EXPECT_CALL(*bas_.get(), IncrementVolume()).WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.IncrementVolume(), 0);
}

TEST_F(BrilloAudioClientTest, DecrementVolNoService) {
  EXPECT_CALL(client_, OnBASDisconnect());
  EXPECT_EQ(client_.DecrementVolume(), ECONNABORTED);
}

TEST_F(BrilloAudioClientTest, DecrementVolWithBAS) {
  EXPECT_TRUE(ConnectClientToBAS());
  EXPECT_CALL(*bas_.get(), DecrementVolume()).WillOnce(Return(Status::ok()));
  EXPECT_EQ(client_.DecrementVolume(), 0);
}

}  // namespace brillo
