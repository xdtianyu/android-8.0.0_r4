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

#ifndef BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_SERVICE_MOCK_H_
#define BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_SERVICE_MOCK_H_

#include <vector>

#include <gmock/gmock.h>
#include <gtest/gtest_prod.h>

#include "brillo_audio_service.h"

namespace brillo {

class BrilloAudioServiceMock : public BrilloAudioService {
 public:
  BrilloAudioServiceMock() = default;
  ~BrilloAudioServiceMock() {}

  MOCK_METHOD2(GetDevices, Status(int flag, std::vector<int>* _aidl_return));
  MOCK_METHOD2(SetDevice, Status(int usage, int config));
  MOCK_METHOD2(GetMaxVolumeSteps, Status(int stream, int* _aidl_return));
  MOCK_METHOD2(SetMaxVolumeSteps, Status(int stream, int max_steps));
  MOCK_METHOD3(SetVolumeIndex, Status(int stream, int device, int index));
  MOCK_METHOD3(GetVolumeIndex,
               Status(int stream, int device, int* _aidl_return));
  MOCK_METHOD1(GetVolumeControlStream, Status(int* _aidl_return));
  MOCK_METHOD1(SetVolumeControlStream, Status(int stream));
  MOCK_METHOD0(IncrementVolume, Status());
  MOCK_METHOD0(DecrementVolume, Status());
  MOCK_METHOD1(RegisterServiceCallback,
               Status(const android::sp<IAudioServiceCallback>& callback));
  MOCK_METHOD1(UnregisterServiceCallback,
               Status(const android::sp<IAudioServiceCallback>& callback));

  void RegisterHandlers(std::weak_ptr<AudioDeviceHandler>,
                        std::weak_ptr<AudioVolumeHandler>){};
  void OnDevicesConnected(const std::vector<int>&) {}
  void OnDevicesDisconnected(const std::vector<int>&) {}
  void OnVolumeChanged(audio_stream_type_t, int, int){};
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_SERVICE_MOCK_H_
