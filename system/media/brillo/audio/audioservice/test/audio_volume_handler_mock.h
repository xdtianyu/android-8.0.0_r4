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

// Mock of AudioVolumeHandler.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_TEST_AUDIO_VOLUME_HANDLER_MOCK_H_
#define BRILLO_AUDIO_AUDIOSERVICE_TEST_AUDIO_VOLUME_HANDLER_MOCK_H_

#include <gmock/gmock.h>
#include <gtest/gtest_prod.h>

#include "audio_volume_handler.h"

namespace brillo {

class AudioVolumeHandlerMock : public AudioVolumeHandler {
 public:
  AudioVolumeHandlerMock() = default;
  ~AudioVolumeHandlerMock() {}

 private:
  friend class AudioVolumeHandlerTest;
  FRIEND_TEST(AudioVolumeHandlerTest, FileGeneration);
  FRIEND_TEST(AudioVolumeHandlerTest, GetVolumeForKey);
  FRIEND_TEST(AudioVolumeHandlerTest, GetVolumeForStreamDeviceTuple);
  FRIEND_TEST(AudioVolumeHandlerTest, SetVolumeForStreamDeviceTuple);
  FRIEND_TEST(AudioVolumeHandlerTest, InitNoFile);
  FRIEND_TEST(AudioVolumeHandlerTest, InitFilePresent);
  FRIEND_TEST(AudioVolumeHandlerTest, ProcessEventEmpty);
  FRIEND_TEST(AudioVolumeHandlerTest, ProcessEventKeyUp);
  FRIEND_TEST(AudioVolumeHandlerTest, ProcessEventKeyDown);
  FRIEND_TEST(AudioVolumeHandlerTest, SelectStream);
  FRIEND_TEST(AudioVolumeHandlerTest, ComputeNewVolume);
  FRIEND_TEST(AudioVolumeHandlerTest, GetSetVolumeIndex);

  MOCK_METHOD3(TriggerCallback, void(audio_stream_type_t, int, int));
  MOCK_METHOD0(InitAPSAllStreams, void());
  MOCK_METHOD1(AdjustVolumeActiveStreams, void(int));
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_TEST_AUDIO_VOLUME_HANDLER_MOCK_H_
