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

// Tests for audio volume handler.

#include "audio_volume_handler_mock.h"

#include <memory>
#include <string>

#include <base/files/file_path.h>
#include <base/files/file_util.h>
#include <base/files/scoped_temp_dir.h>
#include <brillo/key_value_store.h>
#include <brillo/strings/string_utils.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "audio_device_handler.h"

using base::FilePath;
using base::PathExists;
using base::ScopedTempDir;
using brillo::string_utils::ToString;
using std::stoi;
using testing::_;

namespace brillo {

class AudioVolumeHandlerTest : public testing::Test {
 public:
  void SetUp() override {
    EXPECT_TRUE(temp_dir_.CreateUniqueTempDir());
    volume_file_path_ = temp_dir_.path().Append("vol_file");
    handler_.SetVolumeFilePathForTesting(volume_file_path_);
  }

  void SetupHandlerVolumeFile() {
    handler_.kv_store_ = std::unique_ptr<KeyValueStore>(new KeyValueStore);
    handler_.GenerateVolumeFile();
  }

  AudioVolumeHandlerMock handler_;
  FilePath volume_file_path_;

 private:
  ScopedTempDir temp_dir_;
};

// Test that the volume file is formatted correctly.
TEST_F(AudioVolumeHandlerTest, FileGeneration) {
  SetupHandlerVolumeFile();
  KeyValueStore kv_store;
  kv_store.Load(volume_file_path_);
  for (auto stream : handler_.kSupportedStreams_) {
    std::string value;
    ASSERT_EQ(handler_.kMinIndex_, 0);
    ASSERT_EQ(handler_.kMaxIndex_, 100);
    for (auto device : AudioDeviceHandler::kSupportedOutputDevices_) {
      ASSERT_TRUE(kv_store.GetString(handler_.kCurrentIndexKey_ + "." +
                                         ToString(stream) + "." +
                                         ToString(device),
                                     &value));
      ASSERT_EQ(handler_.kDefaultCurrentIndex_, stoi(value));
    }
  }
}

// Test GetVolumeCurrentIndex.
TEST_F(AudioVolumeHandlerTest, GetVolumeForStreamDeviceTuple) {
  handler_.kv_store_ = std::unique_ptr<KeyValueStore>(new KeyValueStore);
  handler_.kv_store_->SetString(handler_.kCurrentIndexKey_ + ".1.2", "100");
  ASSERT_EQ(
      handler_.GetVolumeCurrentIndex(static_cast<audio_stream_type_t>(1), 2),
      100);
}

// Test SetVolumeCurrentIndex.
TEST_F(AudioVolumeHandlerTest, SetVolumeForStreamDeviceTuple) {
  handler_.kv_store_ = std::unique_ptr<KeyValueStore>(new KeyValueStore);
  handler_.PersistVolumeConfiguration(
      static_cast<audio_stream_type_t>(1), 2, 100);
  std::string value;
  auto key = handler_.kCurrentIndexKey_ + ".1.2";
  handler_.kv_store_->GetString(key, &value);
  ASSERT_EQ(stoi(value), 100);
}

// Test that a new volume file is generated if it doesn't exist.
TEST_F(AudioVolumeHandlerTest, InitNoFile) {
  EXPECT_CALL(handler_, InitAPSAllStreams());
  handler_.Init(nullptr);
  EXPECT_TRUE(PathExists(volume_file_path_));
}

// Test that a new volume file isn't generated it already exists.
TEST_F(AudioVolumeHandlerTest, InitFilePresent) {
  KeyValueStore kv_store;
  kv_store.SetString("foo", "100");
  kv_store.Save(volume_file_path_);
  EXPECT_CALL(handler_, InitAPSAllStreams());
  handler_.Init(nullptr);
  EXPECT_TRUE(PathExists(volume_file_path_));
  std::string value;
  handler_.kv_store_->GetString("foo", &value);
  EXPECT_EQ(stoi(value), 100);
}

TEST_F(AudioVolumeHandlerTest, ProcessEventEmpty) {
  struct input_event event;
  event.type = 0;
  event.code = 0;
  event.value = 0;
  EXPECT_CALL(handler_, AdjustVolumeActiveStreams(_)).Times(0);
  handler_.ProcessEvent(event);
}

TEST_F(AudioVolumeHandlerTest, ProcessEventKeyUp) {
  struct input_event event;
  event.type = EV_KEY;
  event.code = KEY_VOLUMEUP;
  event.value = 1;
  EXPECT_CALL(handler_, AdjustVolumeActiveStreams(1));
  handler_.ProcessEvent(event);
}

TEST_F(AudioVolumeHandlerTest, ProcessEventKeyDown) {
  struct input_event event;
  event.type = EV_KEY;
  event.code = KEY_VOLUMEDOWN;
  event.value = 1;
  EXPECT_CALL(handler_, AdjustVolumeActiveStreams(-1));
  handler_.ProcessEvent(event);
}

TEST_F(AudioVolumeHandlerTest, SelectStream) {
  EXPECT_EQ(handler_.GetVolumeControlStream(), AUDIO_STREAM_DEFAULT);
  handler_.SetVolumeControlStream(AUDIO_STREAM_MUSIC);
  EXPECT_EQ(handler_.GetVolumeControlStream(), AUDIO_STREAM_MUSIC);
}

TEST_F(AudioVolumeHandlerTest, ComputeNewVolume) {
  EXPECT_EQ(handler_.GetNewVolumeIndex(50, 1, AUDIO_STREAM_MUSIC), 51);
  EXPECT_EQ(handler_.GetNewVolumeIndex(50, -1, AUDIO_STREAM_MUSIC), 49);
  handler_.step_sizes_[AUDIO_STREAM_MUSIC] = 10;
  EXPECT_EQ(handler_.GetNewVolumeIndex(50, 1, AUDIO_STREAM_MUSIC), 60);
  EXPECT_EQ(handler_.GetNewVolumeIndex(50, -1, AUDIO_STREAM_MUSIC), 40);
  SetupHandlerVolumeFile();
  EXPECT_EQ(handler_.GetNewVolumeIndex(100, 1, AUDIO_STREAM_MUSIC), 100);
  EXPECT_EQ(handler_.GetNewVolumeIndex(0, -1, AUDIO_STREAM_MUSIC), 0);
}

TEST_F(AudioVolumeHandlerTest, GetSetMaxSteps) {
  EXPECT_EQ(handler_.GetVolumeMaxSteps(AUDIO_STREAM_MUSIC), 100);
  EXPECT_EQ(handler_.SetVolumeMaxSteps(AUDIO_STREAM_MUSIC, 0), EINVAL);
  EXPECT_EQ(handler_.GetVolumeMaxSteps(AUDIO_STREAM_MUSIC), 100);
  EXPECT_EQ(handler_.SetVolumeMaxSteps(AUDIO_STREAM_MUSIC, 100), 0);
  EXPECT_EQ(handler_.GetVolumeMaxSteps(AUDIO_STREAM_MUSIC), 100);
  EXPECT_EQ(handler_.SetVolumeMaxSteps(AUDIO_STREAM_MUSIC, -1), EINVAL);
  EXPECT_EQ(handler_.SetVolumeMaxSteps(AUDIO_STREAM_MUSIC, 101), EINVAL);
}

TEST_F(AudioVolumeHandlerTest, GetSetVolumeIndex) {
  SetupHandlerVolumeFile();
  EXPECT_CALL(handler_, TriggerCallback(AUDIO_STREAM_MUSIC, _, 0));
  EXPECT_EQ(handler_.SetVolumeIndex(
                AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_WIRED_HEADSET, 0),
            0);
  EXPECT_CALL(handler_, TriggerCallback(AUDIO_STREAM_MUSIC, 0, 50));
  EXPECT_EQ(handler_.SetVolumeIndex(
                AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_WIRED_HEADSET, 50),
            0);
  EXPECT_CALL(handler_, TriggerCallback(AUDIO_STREAM_MUSIC, 50, 100));
  EXPECT_EQ(handler_.SetVolumeIndex(
                AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_WIRED_HEADSET, 100),
            0);
  EXPECT_EQ(handler_.SetVolumeIndex(
                AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_WIRED_HEADSET, -1),
            EINVAL);
  EXPECT_EQ(handler_.SetVolumeIndex(
                AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_WIRED_HEADSET, 101),
            EINVAL);
  EXPECT_EQ(handler_.SetVolumeMaxSteps(AUDIO_STREAM_MUSIC, 10), 0);
  EXPECT_EQ(handler_.GetVolumeIndex(AUDIO_STREAM_MUSIC,
                                    AUDIO_DEVICE_OUT_WIRED_HEADSET),
            10);
  EXPECT_EQ(handler_.SetVolumeIndex(
                AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_WIRED_HEADSET, 11),
            EINVAL);
  EXPECT_CALL(handler_, TriggerCallback(AUDIO_STREAM_MUSIC, 100, 50));
  EXPECT_EQ(handler_.SetVolumeIndex(
                AUDIO_STREAM_MUSIC, AUDIO_DEVICE_OUT_WIRED_HEADSET, 5),
            0);
  EXPECT_EQ(handler_.SetVolumeMaxSteps(AUDIO_STREAM_MUSIC, 20), 0);
  EXPECT_EQ(handler_.GetVolumeIndex(AUDIO_STREAM_MUSIC,
                                    AUDIO_DEVICE_OUT_WIRED_HEADSET),
            10);
}

}  // namespace brillo
