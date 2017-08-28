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

#ifndef BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_SERVICE_IMPL_H_
#define BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_SERVICE_IMPL_H_

// Server side implementation of brillo audio service.

#include "brillo_audio_service.h"

namespace brillo {

class BrilloAudioServiceImpl : public BrilloAudioService {
 public:
  ~BrilloAudioServiceImpl() = default;

  // From AIDL.
  Status GetDevices(int flag, std::vector<int>* _aidl_return) override;
  Status SetDevice(int usage, int config) override;
  Status GetMaxVolumeSteps(int stream, int* _aidl_return) override;
  Status SetMaxVolumeSteps(int stream, int max_steps) override;
  Status SetVolumeIndex(int stream, int device, int index) override;
  Status GetVolumeIndex(int stream, int device, int* _aidl_return) override;
  Status GetVolumeControlStream(int* _aidl_return) override;
  Status SetVolumeControlStream(int stream) override;
  Status IncrementVolume() override;
  Status DecrementVolume() override;
  Status RegisterServiceCallback(
      const android::sp<IAudioServiceCallback>& callback) override;
  Status UnregisterServiceCallback(
      const android::sp<IAudioServiceCallback>& callback) override;

  // Register daemon handlers.
  //
  // |audio_device_handler| is a weak pointer to an audio device handler object.
  // |audio_volume_handler| is a weak pointer to an audio volume handler object.
  void RegisterHandlers(
      std::weak_ptr<AudioDeviceHandler> audio_device_handler,
      std::weak_ptr<AudioVolumeHandler> audio_volume_handler) override;

  // Callback to be called when a device is connected.
  //
  // |devices| is a vector of ints representing the audio_devices_t.
  void OnDevicesConnected(const std::vector<int>& device) override;

  // Callback to be called when a device is disconnected.
  //
  // |devices| is a vector of ints representing the audio_devices_t.
  void OnDevicesDisconnected(const std::vector<int>& device) override;

  // Callback to be called when volume is changed.
  //
  // |stream| is an int representing the stream.
  // |previous_index| is the volume index before the key press.
  // |current_index| is the volume index after the key press.
  void OnVolumeChanged(audio_stream_type_t stream,
                       int previous_index,
                       int current_index) override;

 private:
  // A weak pointer to the audio device handler.
  std::weak_ptr<AudioDeviceHandler> audio_device_handler_;
  // A weak pointer to the audio volume handler.
  std::weak_ptr<AudioVolumeHandler> audio_volume_handler_;
  // List of all callbacks objects registered with the service.
  std::set<android::sp<IAudioServiceCallback> > callbacks_set_;
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_SERVICE_IMPL_H_
