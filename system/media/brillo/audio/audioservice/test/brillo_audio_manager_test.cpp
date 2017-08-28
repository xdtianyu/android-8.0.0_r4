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

// Tests for the brillo audio manager interface.

#include <binderwrapper/binder_test_base.h>
#include <binderwrapper/stub_binder_wrapper.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "audio_service_callback.h"
#include "brillo_audio_client.h"
#include "include/brillo_audio_manager.h"
#include "test/brillo_audio_service_mock.h"

using android::sp;
using testing::Mock;
using testing::Return;
using testing::_;

namespace brillo {

static const char kBrilloAudioServiceName[] =
    "android.brillo.brilloaudioservice.BrilloAudioService";

class BrilloAudioManagerTest : public android::BinderTestBase {
 public:
  void ConnectBAS() {
    bas_ = new BrilloAudioServiceMock();
    binder_wrapper()->SetBinderForService(kBrilloAudioServiceName, bas_);
  }

  BAudioManager* GetValidManager() {
    ConnectBAS();
    auto bam = BAudioManager_new();
    EXPECT_NE(bam, nullptr);
    return bam;
  }

  void TearDown() {
    // Stopping the BAS will cause the client to delete itself.
    binder_wrapper()->NotifyAboutBinderDeath(bas_);
    bas_.clear();
  }

  sp<BrilloAudioServiceMock> bas_;
};

TEST_F(BrilloAudioManagerTest, NewNoService) {
  EXPECT_EQ(BAudioManager_new(), nullptr);
}

TEST_F(BrilloAudioManagerTest, NewWithBAS) {
  ConnectBAS();
  auto bam = BAudioManager_new();
  EXPECT_NE(bam, nullptr);
}

TEST_F(BrilloAudioManagerTest, GetDevicesInvalidParams) {
  auto bam = GetValidManager();
  unsigned int num_devices;
  EXPECT_EQ(BAudioManager_getDevices(nullptr, 1, nullptr, 0, &num_devices),
            EINVAL);
  EXPECT_EQ(BAudioManager_getDevices(bam, 1, nullptr, 0, nullptr), EINVAL);
  EXPECT_EQ(BAudioManager_getDevices(bam, -1, nullptr, 0, &num_devices),
            EINVAL);
}

TEST_F(BrilloAudioManagerTest, GetDevicesNullArrNoDevices) {
  auto bam = GetValidManager();
  unsigned int num_devices = -1;
  EXPECT_CALL(*bas_.get(), GetDevices(1, _)).WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_getDevices(bam, 1, nullptr, 0, &num_devices), 0);
  EXPECT_EQ(num_devices, 0);
}

TEST_F(BrilloAudioManagerTest, SetInputDeviceInvalidParams) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_UNKNOWN);
  EXPECT_EQ(BAudioManager_setInputDevice(nullptr, nullptr), EINVAL);
  EXPECT_EQ(BAudioManager_setInputDevice(bam, nullptr), EINVAL);
  EXPECT_EQ(BAudioManager_setInputDevice(nullptr, device), EINVAL);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, SetInputDeviceHeadsetMic) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADSET_MIC);
  EXPECT_CALL(*bas_.get(), SetDevice(AUDIO_POLICY_FORCE_FOR_RECORD,
                                     AUDIO_POLICY_FORCE_HEADPHONES))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_setInputDevice(bam, device), 0);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, SetInputDeviceBuiltinMic) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_BUILTIN_MIC);
  EXPECT_CALL(*bas_.get(),
              SetDevice(AUDIO_POLICY_FORCE_FOR_RECORD, AUDIO_POLICY_FORCE_NONE))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_setInputDevice(bam, device), 0);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, SetOutputDeviceInvalidParams) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_UNKNOWN);
  EXPECT_EQ(BAudioManager_setOutputDevice(nullptr, nullptr, kUsageMedia),
            EINVAL);
  EXPECT_EQ(BAudioManager_setOutputDevice(bam, nullptr, kUsageMedia), EINVAL);
  EXPECT_EQ(BAudioManager_setOutputDevice(nullptr, device, kUsageMedia),
            EINVAL);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, SetOutputDeviceWiredHeadset) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADSET);
  EXPECT_CALL(*bas_.get(), SetDevice(AUDIO_POLICY_FORCE_FOR_MEDIA,
                                     AUDIO_POLICY_FORCE_HEADPHONES))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_setOutputDevice(bam, device, kUsageMedia), 0);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, SetOutputDeviceBuiltinSpeaker) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_BUILTIN_SPEAKER);
  EXPECT_CALL(*bas_.get(), SetDevice(AUDIO_POLICY_FORCE_FOR_SYSTEM,
                                     AUDIO_POLICY_FORCE_SPEAKER))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_setOutputDevice(bam, device, kUsageSystem), 0);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, SetOutputDeviceWiredHeadphoneNotification) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADPHONES);
  EXPECT_CALL(*bas_.get(), SetDevice(AUDIO_POLICY_FORCE_FOR_SYSTEM,
                                     AUDIO_POLICY_FORCE_HEADPHONES))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_setOutputDevice(bam, device, kUsageNotifications), 0);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, SetOutputDeviceWiredHeadphoneAlarm) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADPHONES);
  EXPECT_CALL(*bas_.get(), SetDevice(AUDIO_POLICY_FORCE_FOR_SYSTEM,
                                     AUDIO_POLICY_FORCE_HEADPHONES))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_setOutputDevice(bam, device, kUsageAlarm), 0);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, RegisterCallbackInvalidParams) {
  auto bam = GetValidManager();
  BAudioCallback callback;
  int callback_id;
  EXPECT_EQ(
      BAudioManager_registerAudioCallback(nullptr, nullptr, nullptr, nullptr),
      EINVAL);
  EXPECT_EQ(BAudioManager_registerAudioCallback(bam, nullptr, nullptr, nullptr),
            EINVAL);
  EXPECT_EQ(
      BAudioManager_registerAudioCallback(bam, &callback, nullptr, nullptr),
      EINVAL);
  EXPECT_EQ(
      BAudioManager_registerAudioCallback(bam, nullptr, nullptr, &callback_id),
      EINVAL);
}

TEST_F(BrilloAudioManagerTest, RegisterCallbackOnStack) {
  auto bam = GetValidManager();
  BAudioCallback callback;
  callback.OnAudioDeviceAdded = nullptr;
  callback.OnAudioDeviceRemoved = nullptr;
  int callback_id = 0;
  EXPECT_CALL(*bas_.get(), RegisterServiceCallback(_))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_registerAudioCallback(bam, &callback, nullptr,
                                                &callback_id),
            0);
  EXPECT_NE(callback_id, 0);
}

TEST_F(BrilloAudioManagerTest, RegisterCallbackOnHeap) {
  auto bam = GetValidManager();
  BAudioCallback* callback = new BAudioCallback;
  callback->OnAudioDeviceAdded = nullptr;
  callback->OnAudioDeviceRemoved = nullptr;
  int callback_id = 0;
  EXPECT_CALL(*bas_.get(), RegisterServiceCallback(_))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(
      BAudioManager_registerAudioCallback(bam, callback, nullptr, &callback_id),
      0);
  EXPECT_NE(callback_id, 0);
  delete callback;
}

TEST_F(BrilloAudioManagerTest, UnregisterCallbackInvalidParams) {
  auto bam = GetValidManager();
  EXPECT_EQ(BAudioManager_unregisterAudioCallback(nullptr, 1), EINVAL);
  EXPECT_EQ(BAudioManager_unregisterAudioCallback(bam, 1), EINVAL);
}

TEST_F(BrilloAudioManagerTest, UnregisterCallback) {
  auto bam = GetValidManager();
  BAudioCallback callback;
  callback.OnAudioDeviceAdded = nullptr;
  callback.OnAudioDeviceRemoved = nullptr;
  int callback_id = 0;
  EXPECT_CALL(*bas_.get(), RegisterServiceCallback(_))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_registerAudioCallback(bam, &callback, nullptr,
                                                &callback_id),
            0);
  EXPECT_NE(callback_id, 0);
  EXPECT_CALL(*bas_.get(), UnregisterServiceCallback(_))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_unregisterAudioCallback(bam, callback_id), 0);
  // 2nd call shouldn't result in a call to BAS.
  EXPECT_EQ(BAudioManager_unregisterAudioCallback(bam, callback_id), EINVAL);
}

TEST_F(BrilloAudioManagerTest, GetDevicesBASDies) {
  auto bam = GetValidManager();
  unsigned int num_devices = -1;
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_getDevices(bam, 1, nullptr, 0, &num_devices),
            ECONNABORTED);
}

TEST_F(BrilloAudioManagerTest, SetInputDeviceBASDies) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADSET_MIC);
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_setInputDevice(bam, device), ECONNABORTED);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, SetOutputDeviceBASDies) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADPHONES);
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_setOutputDevice(bam, device, kUsageNotifications),
            ECONNABORTED);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, RegisterServiceCallbackBASDies) {
  auto bam = GetValidManager();
  BAudioCallback callback;
  callback.OnAudioDeviceAdded = nullptr;
  callback.OnAudioDeviceRemoved = nullptr;
  int callback_id = 1;
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_registerAudioCallback(bam, &callback, nullptr,
                                                &callback_id),
            ECONNABORTED);
  EXPECT_EQ(callback_id, 0);
}

TEST_F(BrilloAudioManagerTest, UnregisterCallbackBASDies) {
  auto bam = GetValidManager();
  BAudioCallback callback;
  callback.OnAudioDeviceAdded = nullptr;
  callback.OnAudioDeviceRemoved = nullptr;
  int callback_id = 0;
  EXPECT_CALL(*bas_.get(), RegisterServiceCallback(_))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_registerAudioCallback(bam, &callback, nullptr,
                                                &callback_id),
            0);
  EXPECT_NE(callback_id, 0);
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_unregisterAudioCallback(bam, callback_id),
            ECONNABORTED);
}

TEST_F(BrilloAudioManagerTest, GetMaxVolumeStepsInvalidParams) {
  auto bam = GetValidManager();
  int foo;
  EXPECT_EQ(BAudioManager_getMaxVolumeSteps(
                nullptr, BAudioUsage::kUsageMedia, nullptr),
            EINVAL);
  EXPECT_EQ(
      BAudioManager_getMaxVolumeSteps(nullptr, BAudioUsage::kUsageMedia, &foo),
      EINVAL);
  EXPECT_EQ(
      BAudioManager_getMaxVolumeSteps(bam, BAudioUsage::kUsageMedia, nullptr),
      EINVAL);
}

TEST_F(BrilloAudioManagerTest, GetMaxVolStepsWithBAS) {
  auto bam = GetValidManager();
  int foo;
  EXPECT_CALL(*bas_.get(), GetMaxVolumeSteps(AUDIO_STREAM_MUSIC, &foo))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(
      BAudioManager_getMaxVolumeSteps(bam, BAudioUsage::kUsageMedia, &foo), 0);
}

TEST_F(BrilloAudioManagerTest, GetMaxVolStepsBASDies) {
  auto bam = GetValidManager();
  int foo;
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(
      BAudioManager_getMaxVolumeSteps(bam, BAudioUsage::kUsageMedia, &foo),
      ECONNABORTED);
}

TEST_F(BrilloAudioManagerTest, SetMaxVolumeStepsInvalidParams) {
  EXPECT_EQ(
      BAudioManager_setMaxVolumeSteps(nullptr, BAudioUsage::kUsageMedia, 100),
      EINVAL);
}

TEST_F(BrilloAudioManagerTest, SetMaxVolStepsWithBAS) {
  auto bam = GetValidManager();
  EXPECT_CALL(*bas_.get(), SetMaxVolumeSteps(AUDIO_STREAM_MUSIC, 100))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_setMaxVolumeSteps(bam, BAudioUsage::kUsageMedia, 100),
            0);
}

TEST_F(BrilloAudioManagerTest, SetMaxVolStepsBASDies) {
  auto bam = GetValidManager();
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_setMaxVolumeSteps(bam, BAudioUsage::kUsageMedia, 100),
            ECONNABORTED);
}

TEST_F(BrilloAudioManagerTest, SetVolIndexInvalidParams) {
  auto bam = GetValidManager();
  EXPECT_EQ(BAudioManager_setVolumeIndex(
                nullptr, BAudioUsage::kUsageMedia, nullptr, 100),
            EINVAL);
  EXPECT_EQ(
      BAudioManager_setVolumeIndex(bam, BAudioUsage::kUsageMedia, nullptr, 100),
      EINVAL);
}

TEST_F(BrilloAudioManagerTest, SetVolIndexWithBAS) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADPHONES);
  EXPECT_CALL(
      *bas_.get(),
      SetVolumeIndex(AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_WIRED_HEADPHONE, 100))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(
      BAudioManager_setVolumeIndex(bam, BAudioUsage::kUsageMedia, device, 100),
      0);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, SetVolIndexBASDies) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADPHONES);
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(
      BAudioManager_setVolumeIndex(bam, BAudioUsage::kUsageMedia, device, 100),
      ECONNABORTED);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, GetVolIndexInvalidParams) {
  auto bam = GetValidManager();
  int foo;
  EXPECT_EQ(BAudioManager_getVolumeIndex(
                nullptr, BAudioUsage::kUsageMedia, nullptr, nullptr),
            EINVAL);
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADPHONES);
  EXPECT_EQ(BAudioManager_getVolumeIndex(
                bam, BAudioUsage::kUsageMedia, device, nullptr),
            EINVAL);
  EXPECT_EQ(BAudioManager_getVolumeIndex(
                nullptr, BAudioUsage::kUsageMedia, device, &foo),
            EINVAL);
  EXPECT_EQ(BAudioManager_getVolumeIndex(
                bam, BAudioUsage::kUsageMedia, nullptr, &foo),
            EINVAL);
}

TEST_F(BrilloAudioManagerTest, GetVolIndexWithBAS) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADPHONES);
  int foo;
  EXPECT_CALL(*bas_.get(),
              GetVolumeIndex(
                  AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_WIRED_HEADPHONE, &foo))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(
      BAudioManager_getVolumeIndex(bam, BAudioUsage::kUsageMedia, device, &foo),
      0);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, GetVolIndexBASDies) {
  auto bam = GetValidManager();
  auto device = BAudioDeviceInfo_new(TYPE_WIRED_HEADPHONES);
  int foo;
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(
      BAudioManager_getVolumeIndex(bam, BAudioUsage::kUsageMedia, device, &foo),
      ECONNABORTED);
  BAudioDeviceInfo_delete(device);
}

TEST_F(BrilloAudioManagerTest, GetVolumeControlUsageInvalidParams) {
  auto bam = GetValidManager();
  BAudioUsage foo;
  EXPECT_EQ(BAudioManager_getVolumeControlUsage(nullptr, nullptr), EINVAL);
  EXPECT_EQ(BAudioManager_getVolumeControlUsage(nullptr, &foo), EINVAL);
  EXPECT_EQ(BAudioManager_getVolumeControlUsage(bam, nullptr), EINVAL);
}

TEST_F(BrilloAudioManagerTest, GetVolumeControlStreamWithBAS) {
  auto bam = GetValidManager();
  BAudioUsage foo;
  EXPECT_CALL(*bas_.get(), GetVolumeControlStream(_))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_getVolumeControlUsage(bam, &foo), 0);
}

TEST_F(BrilloAudioManagerTest, GetVolumeControlStreamBASDies) {
  auto bam = GetValidManager();
  BAudioUsage foo;
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_getVolumeControlUsage(bam, &foo), ECONNABORTED);
}

TEST_F(BrilloAudioManagerTest, SetVolumeControlUsageInvalidParams) {
  EXPECT_EQ(
      BAudioManager_setVolumeControlUsage(nullptr, BAudioUsage::kUsageMedia),
      EINVAL);
}

TEST_F(BrilloAudioManagerTest, SetVolumeControlStreamWithBAS) {
  auto bam = GetValidManager();
  EXPECT_CALL(*bas_.get(), SetVolumeControlStream(AUDIO_STREAM_MUSIC))
      .WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_setVolumeControlUsage(bam, BAudioUsage::kUsageMedia),
            0);
}

TEST_F(BrilloAudioManagerTest, SetVolumeControlStreamBASDies) {
  auto bam = GetValidManager();
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_setVolumeControlUsage(bam, BAudioUsage::kUsageMedia),
            ECONNABORTED);
}

TEST_F(BrilloAudioManagerTest, DecIncInvalidParams) {
  EXPECT_EQ(BAudioManager_decrementVolume(nullptr), EINVAL);
  EXPECT_EQ(BAudioManager_incrementVolume(nullptr), EINVAL);
}

TEST_F(BrilloAudioManagerTest, IncVolWithBAS) {
  auto bam = GetValidManager();
  EXPECT_CALL(*bas_.get(), IncrementVolume()).WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_incrementVolume(bam), 0);
}

TEST_F(BrilloAudioManagerTest, IncVolBASDies) {
  auto bam = GetValidManager();
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_incrementVolume(bam), ECONNABORTED);
}

TEST_F(BrilloAudioManagerTest, DecVolWithBAS) {
  auto bam = GetValidManager();
  EXPECT_CALL(*bas_.get(), DecrementVolume()).WillOnce(Return(Status::ok()));
  EXPECT_EQ(BAudioManager_decrementVolume(bam), 0);
}

TEST_F(BrilloAudioManagerTest, DecVolBASDies) {
  auto bam = GetValidManager();
  binder_wrapper()->NotifyAboutBinderDeath(bas_);
  EXPECT_EQ(BAudioManager_decrementVolume(bam), ECONNABORTED);
}

}  // namespace brillo
