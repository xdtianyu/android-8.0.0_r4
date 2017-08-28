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

// Callback object to be passed to brilloaudioservice.

#ifndef BRILLO_AUDIO_AUDIOSERVICE_AUDIO_SERVICE_CALLBACK_H_
#define BRILLO_AUDIO_AUDIOSERVICE_AUDIO_SERVICE_CALLBACK_H_

#include <vector>

#include <base/callback.h>
#include <binder/Status.h>

#include "android/brillo/brilloaudioservice/BnAudioServiceCallback.h"
#include "include/brillo_audio_manager.h"

using android::binder::Status;
using android::brillo::brilloaudioservice::BnAudioServiceCallback;

namespace brillo {

class AudioServiceCallback : public BnAudioServiceCallback {
 public:
  // Constructor for AudioServiceCallback.
  //
  // |callback| is an object of type BAudioCallback.
  // |user_data| is an object to be passed to the callbacks.
  AudioServiceCallback(const BAudioCallback* callback, void* user_data);

  // Callback function triggered when a device is connected.
  //
  // |devices| is a vector of audio_devices_t.
  Status OnAudioDevicesConnected(const std::vector<int>& devices);

  // Callback function triggered when a device is disconnected.
  //
  // |devices| is a vector of audio_devices_t.
  Status OnAudioDevicesDisconnected(const std::vector<int>& devices);

  // Callback function triggered when volume is changed.
  //
  // |stream| is an int representing the stream.
  // |previous_index| is the volume index before the key press.
  // |current_index| is the volume index after the key press.
  Status OnVolumeChanged(int stream, int previous_index, int current_index);

  // Method to compare two AudioServiceCallback objects.
  //
  // |callback| is a ref counted pointer to a AudioServiceCallback object to be
  // compared with this.
  //
  // Returns true if |callback| equals this.
  bool Equals(const android::sp<AudioServiceCallback>& callback);

 private:
  // Callback when devices are connected.
  base::Callback<void(const BAudioDeviceInfo*, void*)> connected_callback_;
  // Callback when devices are disconnected.
  base::Callback<void(const BAudioDeviceInfo*, void*)> disconnected_callback_;
  // Callback when the volume button is pressed.
  base::Callback<void(BAudioUsage, int, int, void*)> volume_callback_;
  // User data passed to the callbacks.
  void* user_data_;
};

}  // namespace brillo

#endif  // BRILLO_AUDIO_AUDIOSERVICE_AUDIO_SERVICE_CALLBACK_H_
