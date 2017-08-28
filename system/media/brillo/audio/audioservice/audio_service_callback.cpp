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

// Implementation of audio_service_callback.

#include "audio_service_callback.h"

#include <base/bind.h>
#include <base/logging.h>

#include "brillo_audio_client_helpers.h"
#include "brillo_audio_device_info_def.h"

using android::binder::Status;

namespace brillo {

AudioServiceCallback::AudioServiceCallback(const BAudioCallback* callback,
                                           void* user_data) {
  connected_callback_ = base::Bind(callback->OnAudioDeviceAdded);
  disconnected_callback_ = base::Bind(callback->OnAudioDeviceRemoved);
  volume_callback_ = base::Bind(callback->OnVolumeChanged);
  user_data_ = user_data;
}

Status AudioServiceCallback::OnAudioDevicesConnected(
    const std::vector<int>& devices) {
  for (auto device : devices) {
    BAudioDeviceInfo device_info;
    device_info.internal_ = std::unique_ptr<BAudioDeviceInfoInternal>(
        BAudioDeviceInfoInternal::CreateFromAudioDevicesT(device));
    connected_callback_.Run(&device_info, user_data_);
  }
  return Status::ok();
}

Status AudioServiceCallback::OnAudioDevicesDisconnected(
    const std::vector<int>& devices) {
  for (auto device : devices) {
    BAudioDeviceInfo device_info;
    device_info.internal_ = std::unique_ptr<BAudioDeviceInfoInternal>(
        BAudioDeviceInfoInternal::CreateFromAudioDevicesT(device));
    disconnected_callback_.Run(&device_info, user_data_);
  }
  return Status::ok();
}

Status AudioServiceCallback::OnVolumeChanged(int stream,
                                             int previous_index,
                                             int current_index) {
  auto usage = BrilloAudioClientHelpers::GetBAudioUsage(
      static_cast<audio_stream_type_t>(stream));
  volume_callback_.Run(usage, previous_index, current_index, user_data_);
  return Status::ok();
}

bool AudioServiceCallback::Equals(const android::sp<AudioServiceCallback>& callback) {
  if (callback->connected_callback_.Equals(connected_callback_) &&
      callback->disconnected_callback_.Equals(disconnected_callback_) &&
      callback->volume_callback_.Equals(volume_callback_) &&
      callback->user_data_ == user_data_)
    return true;
  return false;
}

}  // namespace brillo
