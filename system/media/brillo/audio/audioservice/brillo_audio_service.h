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

#ifndef BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_SERVICE_H_
#define BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_SERVICE_H_

#include "android/brillo/brilloaudioservice/BnBrilloAudioService.h"

#include <memory>
#include <set>
#include <vector>

#include <binder/Status.h>

#include "android/brillo/brilloaudioservice/IAudioServiceCallback.h"
#include "audio_device_handler.h"
#include "audio_volume_handler.h"

using android::binder::Status;
using android::brillo::brilloaudioservice::BnBrilloAudioService;
using android::brillo::brilloaudioservice::IAudioServiceCallback;

namespace brillo {

class BrilloAudioService : public BnBrilloAudioService {
 public:
  virtual ~BrilloAudioService() {}

  // From AIDL.
  virtual Status GetDevices(int flag, std::vector<int>* _aidl_return) = 0;
  virtual Status SetDevice(int usage, int config) = 0;
  virtual Status GetMaxVolumeSteps(int stream, int* _aidl_return) = 0;
  virtual Status SetMaxVolumeSteps(int stream, int max_steps) = 0;
  virtual Status SetVolumeIndex(int stream, int device, int index) = 0;
  virtual Status GetVolumeIndex(int stream, int device, int* _aidl_return) = 0;
  virtual Status GetVolumeControlStream(int* _aidl_return) = 0;
  virtual Status SetVolumeControlStream(int stream) = 0;
  virtual Status IncrementVolume() = 0;
  virtual Status DecrementVolume() = 0;
  virtual Status RegisterServiceCallback(
      const android::sp<IAudioServiceCallback>& callback) = 0;
  virtual Status UnregisterServiceCallback(
      const android::sp<IAudioServiceCallback>& callback) = 0;

  // Register daemon handlers.
  //
  // |audio_device_handler| is a weak pointer to an audio device handler object.
  // |audio_volume_handler| is a weak pointer to an audio volume handler object.
  virtual void RegisterHandlers(
      std::weak_ptr<AudioDeviceHandler> audio_device_handler,
      std::weak_ptr<AudioVolumeHandler> audio_volume_handler) = 0;

  // Callback to be called when a device is connected.
  //
  // |devices| is a vector of ints representing the audio_devices_t.
  virtual void OnDevicesConnected(const std::vector<int>& device) = 0;

  // Callback to be called when a device is disconnected.
  //
  // |devices| is a vector of ints representing the audio_devices_t.
  virtual void OnDevicesDisconnected(const std::vector<int>& device) = 0;

  // Callback to be called when the volume is changed.
  //
  // |stream| is an audio_stream_type_t representing the stream.
  // |previous_index| is the volume index before the key press.
  // |current_index| is the volume index after the key press.
  virtual void OnVolumeChanged(audio_stream_type_t stream,
                               int previous_index,
                               int current_index) = 0;
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_BRILLO_AUDIO_SERVICE_H_
